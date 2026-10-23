package app.navgo.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class PacketCodec {
    public static final int MAX_BODY = 461;

    private PacketCodec() {}

    public static byte[] body(Navigation nav) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(nav.state.ordinal());
        out.write(nav.turn.ordinal());
        field(out, nav.distance, 32);
        field(out, nav.instruction, 280);
        field(out, nav.summary, 144);
        return out.toByteArray();
    }

    private static void field(ByteArrayOutputStream out, String value, int budget) {
        String clean = MapsParser.clean(value);
        int used = 0;
        for (int i = 0; i < clean.length();) {
            int cp = clean.codePointAt(i);
            byte[] bytes = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
            if (used + bytes.length > budget) break;
            out.write(bytes, 0, bytes.length);
            used += bytes.length;
            i += Character.charCount(cp);
        }
        out.write(0);
    }

}
