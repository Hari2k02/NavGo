package app.navgo.core;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ProtocolFixture {
    public static void main(String[] args) throws Exception {
        Navigation nav = new Navigation(Navigation.State.NAVIGATING, Navigation.Turn.LEFT,
            "200 m", "Turn left onto \u092e\u0941\u0916\u094d\u092f \u0938\u0921\u093c\u0915 \ud83d\udeb6", "12 min \u00b7 4.2 km");
        String key = "ab".repeat(32);
        String requestId = args.length == 0 ? "interop" : args[0];
        try (LocalNavServer server = new LocalNavServer(key, () -> nav, () -> {},
            System.err::println, System.err::println)) {
            server.start(0);
            HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:"
                + server.port() + "/v1/navigation?requestId=" + requestId).openConnection();
            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            try (var input = connection.getInputStream()) {
                System.out.println(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            } finally {
                connection.disconnect();
            }
        }
    }
}
