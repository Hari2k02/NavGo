package app.navgo.core;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PacketCodecTest {
    @Test public void encodesStateTurnAndTerminatedFields() {
        Navigation nav = new Navigation(Navigation.State.NAVIGATING, Navigation.Turn.RIGHT,
            "200 m", "Turn right onto Main Street", "12 min remaining");
        assertArrayEquals(("\u0001\u0003200 m\u0000Turn right onto Main Street\u000012 min remaining\u0000")
            .getBytes(StandardCharsets.UTF_8), PacketCodec.body(nav));
    }

    @Test public void boundsMultilingualTextWithoutSplittingUtf8() throws CharacterCodingException {
        Navigation nav = new Navigation(Navigation.State.NAVIGATING, Navigation.Turn.UNKNOWN,
            "\u8def".repeat(100), "\ud83d\udeb6".repeat(300), "\u0928".repeat(200));
        byte[] body = PacketCodec.body(nav);
        assertTrue(body.length <= PacketCodec.MAX_BODY);
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(body));
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
