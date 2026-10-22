package app.navgo.core;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PacketCodecTest {
    @Test public void fragmentsAndReassemblesAtDefaultBleMtu() {
        Navigation nav = new Navigation(Navigation.State.NAVIGATING, Navigation.Turn.RIGHT,
            "200 m", "Turn right onto Main Street", "12 min remaining");
        List<byte[]> frames = PacketCodec.frames(nav, 0xabcd);
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (int i = 0; i < frames.size(); i++) {
            byte[] frame = frames.get(i);
            assertTrue(frame.length <= 20);
            assertEquals(0x4e, frame[0]);
            assertEquals(1, frame[1]);
            assertEquals(0xab, frame[2] & 255);
            assertEquals(0xcd, frame[3] & 255);
            assertEquals(i, frame[4] & 255);
            assertEquals(frames.size(), frame[5] & 255);
            joined.write(frame, 6, frame.length - 6);
        }
        assertArrayEquals(PacketCodec.body(nav), joined.toByteArray());
    }

    @Test public void boundsMultilingualTextWithoutSplittingUtf8() throws CharacterCodingException {
        Navigation nav = new Navigation(Navigation.State.NAVIGATING, Navigation.Turn.UNKNOWN,
            "\u8def".repeat(100), "\ud83d\udeb6".repeat(300), "\u0928".repeat(200));
        byte[] body = PacketCodec.body(nav);
        assertTrue(body.length <= PacketCodec.MAX_BODY);
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(body));
        assertTrue(PacketCodec.frames(nav, 1).size() <= 33);
    }

    @Test public void preventsEmbeddedFieldDelimiters() {
        Navigation nav = new Navigation(Navigation.State.NAVIGATING, Navigation.Turn.LEFT,
            "20 m", "Turn\u0000left\nnow", "1 min");
        byte[] body = PacketCodec.body(nav);
        int delimiters = 0;
        for (int i = 2; i < body.length; i++) if (body[i] == 0) delimiters++;
        assertEquals(3, delimiters);
        assertTrue(new String(body, StandardCharsets.UTF_8).contains("Turn left now"));
    }
}
