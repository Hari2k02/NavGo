package app.navgo.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PacketCodec {
    public static final String SERVICE_UUID = "a8e70001-8d9b-4c2f-a51b-2e52d72f6300";
    public static final String DATA_UUID = "a8e70002-8d9b-4c2f-a51b-2e52d72f6300";
    public static final int MAX_BODY = 461;
    private static final int CHUNK_BYTES = 14;

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

    public static List<byte[]> frames(Navigation nav, int sequence) {
        byte[] body = body(nav);
        int count = (body.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        List<byte[]> frames = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int start = index * CHUNK_BYTES;
            int size = Math.min(CHUNK_BYTES, body.length - start);
            byte[] frame = new byte[size + 6];
            frame[0] = 0x4e;
            frame[1] = 1;
            frame[2] = (byte) (sequence >>> 8);
            frame[3] = (byte) sequence;
            frame[4] = (byte) index;
            frame[5] = (byte) count;
            System.arraycopy(body, start, frame, 6, size);
            frames.add(frame);
        }
        return frames;
    }
}
