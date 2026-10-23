package app.navgo;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;

final class BridgeKey {
    private final SharedPreferences preferences;

    BridgeKey(Context context) {
        preferences = context.getSharedPreferences("bridge", Context.MODE_PRIVATE);
        context.getSharedPreferences("device_trust", Context.MODE_PRIVATE).edit().clear().apply();
    }

    String get() {
        String key = preferences.getString("key", null);
        return key == null ? reset() : key;
    }

    String reset() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder key = new StringBuilder(64);
        for (byte value : bytes) {
            key.append(Character.forDigit((value & 255) >>> 4, 16));
            key.append(Character.forDigit(value & 15, 16));
        }
        String result = key.toString();
        preferences.edit().putString("key", result).apply();
        return result;
    }
}
