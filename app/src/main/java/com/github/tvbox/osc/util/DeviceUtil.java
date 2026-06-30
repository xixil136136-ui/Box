package com.github.tvbox.osc.util;

import android.content.Context;
import android.provider.Settings;

import java.util.UUID;

/**
 * 设备唯一标识工具
 */
public class DeviceUtil {

    private static String cachedDeviceId = null;

    public static String getDeviceId(Context context) {
        if (cachedDeviceId != null) return cachedDeviceId;

        String saved = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
                .getString("device_id", "");
        if (!saved.isEmpty()) {
            cachedDeviceId = saved;
            return saved;
        }

        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId != null && !androidId.isEmpty()
                && !"9774d56d682e549c".equals(androidId)) {
            cachedDeviceId = "QN_" + androidId;
        }

        if (cachedDeviceId == null) {
            cachedDeviceId = "QN_" + UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 16);
        }

        context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
                .edit().putString("device_id", cachedDeviceId).apply();

        return cachedDeviceId;
    }
}
