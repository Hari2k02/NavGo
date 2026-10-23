package app.navgo.core;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A bounded, loopback-only GET endpoint. It never makes outbound connections. */
public final class LocalNavServer implements AutoCloseable {
    public static final int PORT = 27341;
    private final ServerSocket listener = new ServerSocket();
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0,
        TimeUnit.SECONDS, new ArrayBlockingQueue<>(4), task -> {
            Thread thread = new Thread(task, "navgo-local-request");
            thread.setDaemon(true);
            return thread;
        });
    private final byte[] authorization;
    private final Supplier<Navigation> snapshot;
    private final Runnable connected;
    private final Consumer<String> fatal;
    private final Consumer<String> warning;
    private volatile boolean closed;

    public LocalNavServer(String key, Supplier<Navigation> snapshot, Runnable connected,
                          Consumer<String> fatal, Consumer<String> warning) throws IOException {
        if (key == null || !key.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid local bridge key");
        }
        authorization = ("Bearer " + key).getBytes(StandardCharsets.US_ASCII);
        this.snapshot = snapshot;
        this.connected = connected;
        this.fatal = fatal;
        this.warning = warning;
    }

    public void start(int port) throws IOException {
        listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 4);
        Thread thread = new Thread(this::accept, "navgo-local-listener");
        thread.setDaemon(true);
        thread.start();
    }

    public int port() {
        return listener.getLocalPort();
    }

    private void accept() {
        while (!closed) {
            try {
                Socket socket = listener.accept();
                clients.add(socket);
                if (closed) {
                    closeClient(socket);
                    break;
                }
                try {
                    workers.execute(() -> serve(socket));
                } catch (RejectedExecutionException exception) {
                    closeClient(socket);
                    if (!closed) warning.accept("Local bridge request capacity reached");
                }
            } catch (IOException exception) {
                if (!closed) {
                    fatal.accept("Local bridge listener failed. Stop and restart sharing.");
                    close();
                }
            }
        }
    }

    private void serve(Socket socket) {
        try {
            String request = readHeaders(socket);
            if (request == null) {
                respond(socket, 400, "{}");
                return;
            }
            String[] lines = request.split("\r\n");
            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon <= 0 || headers.put(lines[i].substring(0, colon).toLowerCase(Locale.ROOT),
                    lines[i].substring(colon + 1).trim()) != null) {
                    respond(socket, 400, "{}");
                    return;
                }
            }
            if (!("127.0.0.1:" + port()).equals(headers.get("host"))
                || headers.containsKey("origin") || headers.containsKey("transfer-encoding")
                || (headers.containsKey("content-length") && !"0".equals(headers.get("content-length")))) {
                respond(socket, 403, "{}");
                return;
            }
            byte[] supplied = headers.getOrDefault("authorization", "").getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(authorization, supplied)) {
                respond(socket, 401, "{}");
                return;
            }
            if (!lines[0].matches("GET /v1/navigation\\?requestId=[a-z0-9-]{1,64} HTTP/1\\.[01]")) {
                respond(socket, 404, "{}");
                return;
            }
            String id = lines[0].substring("GET /v1/navigation?requestId=".length(), lines[0].lastIndexOf(' '));
            byte[] body = PacketCodec.body(snapshot.get());
            StringBuilder hex = new StringBuilder(body.length * 2);
            for (byte value : body) {
                hex.append(Character.forDigit((value & 255) >>> 4, 16));
                hex.append(Character.forDigit(value & 15, 16));
            }
            if (closed) return;
            respond(socket, 200, "{\"version\":1,\"requestId\":\"" + id + "\",\"body\":\"" + hex + "\"}");
            connected.run();
        } catch (IOException exception) {
            if (!closed) warning.accept("Local bridge request timed out or disconnected");
        } finally {
            closeClient(socket);
        }
    }

    private String readHeaders(Socket socket) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        BufferedInputStream input = new BufferedInputStream(socket.getInputStream(), 512);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int tail = 0;
        while (bytes.size() < 4096) {
            long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remaining <= 0) return null;
            socket.setSoTimeout((int) Math.max(1, remaining));
            int value = input.read();
            if (value < 0 || value > 127 || (value < 32 && value != 13 && value != 10 && value != 9)) return null;
            bytes.write(value);
            tail = (tail << 8) | value;
            if (tail == 0x0d0a0d0a) return bytes.toString(StandardCharsets.US_ASCII.name());
        }
        return null;
    }

    private void respond(Socket socket, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Rejected") + "\r\n"
            + "Content-Type: application/json\r\nContent-Length: " + bytes.length + "\r\n"
            + "Cache-Control: no-store\r\nConnection: close\r\n\r\n";
        socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
    }

    private void closeClient(Socket socket) {
        clients.remove(socket);
        try {
            socket.close();
        } catch (IOException exception) {
            warning.accept("Could not close a local bridge socket");
        }
    }

    @Override public void close() {
        closed = true;
        try {
            listener.close();
        } catch (IOException exception) {
            warning.accept("Could not close the local bridge listener");
        }
        for (Socket socket : clients) closeClient(socket);
        workers.shutdownNow();
    }
}
