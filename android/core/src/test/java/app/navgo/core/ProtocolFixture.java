package app.navgo.core;

public final class ProtocolFixture {
    public static void main(String[] args) {
        Navigation nav = new Navigation(Navigation.State.NAVIGATING, Navigation.Turn.LEFT,
            "200 m", "Turn left onto \u092e\u0941\u0916\u094d\u092f \u0938\u0921\u093c\u0915 \ud83d\udeb6", "12 min \u00b7 4.2 km");
        System.out.print("[");
        boolean first = true;
        for (byte[] frame : PacketCodec.frames(nav, 65535)) {
            if (!first) System.out.print(",");
            first = false;
            System.out.print("\"");
            for (byte value : frame) System.out.printf("%02x", value & 255);
            System.out.print("\"");
        }
        System.out.println("]");
    }
}
