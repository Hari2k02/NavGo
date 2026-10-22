package app.navgo;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;
import java.util.Locale;

final class TrustStore {
    private final SharedPreferences preferences;

    TrustStore(Context context) {
        preferences = context.getSharedPreferences("device_trust", Context.MODE_PRIVATE);
    }

    String approved() {
        return preferences.getString("watch", "");
    }

    void approve(String address) {
        preferences.edit().putString("watch", address).apply();
    }

    void forget() {
        preferences.edit().remove("watch").apply();
    }

    byte[] phoneCode() {
        int code = preferences.getInt("phone_code", -1);
        if (code < 0) {
            code = new SecureRandom().nextInt(0x1000000);
            preferences.edit().putInt("phone_code", code).apply();
        }
        return new byte[] { (byte) (code >>> 16), (byte) (code >>> 8), (byte) code };
    }

    String phoneLabel() {
        byte[] code = phoneCode();
        return String.format(Locale.ROOT, "%02X%02X%02X", code[0] & 255, code[1] & 255, code[2] & 255);
    }
}
