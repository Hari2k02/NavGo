package app.navgo.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public final class LocalNavServerTest {
    private static final String KEY = "ab".repeat(32);
    private final AtomicInteger snapshots = new AtomicInteger();
    private final CopyOnWriteArrayList<String> failures = new CopyOnWriteArrayList<>();
    private LocalNavServer server;

    @Before public void start() throws IOException {
        server = new LocalNavServer(KEY, () -> {
            snapshots.incrementAndGet();
            return Navigation.idle();
        }, () -> {}, failures::add, warning -> {});
        server.start(0);
    }

    @After public void stop() {
        server.close();
        assertTrue(failures.toString(), failures.isEmpty());
    }

    private String request(String path, String headers) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(4000);
            String request = "GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + server.port()
                + "\r\n" + headers + "\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String auth() {
        return "Authorization: Bearer " + KEY + "\r\n";
    }

    @Test public void requiresKeyBeforeReadingRoute() throws IOException {
        for (String header : new String[] { "", "Authorization: Bearer " + "cd".repeat(32) + "\r\n" }) {
            String response = request("/v1/navigation?requestId=abc-1", header);
            assertTrue(response.startsWith("HTTP/1.1 401"));
            assertFalse(response.contains("\"body\""));
        }
        assertEquals(0, snapshots.get());
        String response = request("/v1/navigation?requestId=abc-2", auth());
        assertTrue(response.startsWith("HTTP/1.1 200"));
        assertTrue(response.contains("Cache-Control: no-store"));
        assertTrue(response.contains("\"requestId\":\"abc-2\""));
        assertTrue(response.matches("(?s).*\"body\":\"[0-9a-f]+\"}"));
        assertEquals(1, snapshots.get());
    }

    @Test public void rejectsBrowserRequestsMalformedHeadersAndInjectedIds() throws IOException {
        assertTrue(request("/v1/navigation?requestId=ok", auth() + "Origin: https://example.invalid\r\n")
            .startsWith("HTTP/1.1 403"));
        assertTrue(request("/v1/navigation?requestId=ok", auth() + auth()).startsWith("HTTP/1.1 400"));
        assertTrue(request("/v1/navigation?requestId=ok", auth() + "Content-Length: 1\r\n")
            .startsWith("HTTP/1.1 403"));
        assertTrue(request("/v1/navigation?requestId=ok", auth() + "Host: attacker.invalid\r\n")
            .startsWith("HTTP/1.1 400"));
        for (String id : new String[] { "", "x".repeat(65), "x%22", "x&remote=elsewhere" }) {
            assertTrue(request("/v1/navigation?requestId=" + id, auth()).startsWith("HTTP/1.1 404"));
        }
        assertEquals(0, snapshots.get());
    }

    @Test public void capsHeadersAndReleasesStalledClients() throws IOException {
        assertTrue(request("/v1/navigation?requestId=ok", auth() + "Padding: " + "x".repeat(4096) + "\r\n")
            .startsWith("HTTP/1.1 400"));
        try (Socket stalled = new Socket("127.0.0.1", server.port())) {
            stalled.setSoTimeout(4000);
            stalled.getOutputStream().write('G');
            long start = System.nanoTime();
            assertEquals(-1, stalled.getInputStream().read());
            assertTrue((System.nanoTime() - start) / 1_000_000 < 3500);
        }
        assertTrue(request("/v1/navigation?requestId=after", auth()).startsWith("HTTP/1.1 200"));
    }

    @Test public void listensOnlyOnLoopbackAndClosesOnStop() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            assertTrue(socket.getInetAddress().isLoopbackAddress());
        }
        InetAddress host = InetAddress.getLocalHost();
        if (!host.isLoopbackAddress()) {
            try (Socket remote = new Socket()) {
                assertThrows(IOException.class, () -> remote.connect(
                    new java.net.InetSocketAddress(host, server.port()), 500));
            }
        }
        int port = server.port();
        server.close();
        try (Socket socket = new Socket()) {
            assertThrows(IOException.class, () -> socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500));
        }
    }
}
