package com.github.tvbox.osc.util;

import android.content.Context;
import android.os.Looper;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

/**
 * 卡密鉴权拦截器
 */
public class CardAuthInterceptor {

    public interface AuthCallback {
        void onSuccess(String msg, int vipLevel);
        void onFailure(String error);
    }

    public static boolean needsActivation() {
        String savedCard = Hawk.get("card_auth_key", "");
        long expireAt = Hawk.get("card_auth_expire", 0L);
        if (savedCard.isEmpty()) return true;
        return expireAt > 0 && expireAt < System.currentTimeMillis();
    }

    public static void doAuth(Context context, String cardKey, AuthCallback callback) {
        String deviceId = DeviceUtil.getDeviceId(context);
        String workerUrl = Hawk.get("custom_worker_url", "https://tvbox-auth.lys1998826.workers.dev");
        String url = workerUrl + "/auth?device_id=" + deviceId + "&card_key=" + cardKey;

        // ── 本地离线备选：支持大小写/空格容错 ──
        if (cardKey != null && cardKey.trim().equalsIgnoreCase("lys1998826")) {
            long expireAt = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000; // 365天
            Hawk.put("card_auth_key", cardKey);
            Hawk.put("card_auth_expire", expireAt);
            Hawk.put("vip_level", 1);
            android.os.Handler mainHandler = new android.os.Handler(Looper.getMainLooper());
            mainHandler.post(() -> callback.onSuccess("激活成功! VIP=1 (本地离线)", 1));
            return;
        }

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build();
                Request req = new Request.Builder().url(url).build();
                Response resp = client.newCall(req).execute();
                String body = resp.body().string();
                JsonObject json = new Gson().fromJson(body, JsonObject.class);

                int code = json.has("code") ? json.get("code").getAsInt() : 0;
                if (code == 1) {
                    long expireAt = json.has("expire_at") ? json.get("expire_at").getAsLong() : 0;
                    int vipLevel = json.has("vip_level") ? json.get("vip_level").getAsInt() : 0;
                    Hawk.put("card_auth_key", cardKey);
                    Hawk.put("card_auth_expire", expireAt);
                    Hawk.put("vip_level", vipLevel);
                    android.os.Handler mainHandler = new android.os.Handler(Looper.getMainLooper());
                    mainHandler.post(() -> callback.onSuccess("激活成功! VIP=" + vipLevel, vipLevel));
                } else {
                    String msg = json.has("msg") ? json.get("msg").getAsString() : "验证失败";
                    android.os.Handler mainHandler = new android.os.Handler(Looper.getMainLooper());
                    mainHandler.post(() -> callback.onFailure(msg));
                }
            } catch (Exception e) {
                // ── 网络失败降级：兜底走离线激活 ──
                if (cardKey != null && cardKey.trim().equalsIgnoreCase("lys1998826")) {
                    long expireAt = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000;
                    Hawk.put("card_auth_key", cardKey.trim());
                    Hawk.put("card_auth_expire", expireAt);
                    Hawk.put("vip_level", 1);
                    android.os.Handler mainHandler = new android.os.Handler(Looper.getMainLooper());
                    mainHandler.post(() -> callback.onSuccess("激活成功! VIP=1 (离线降级)", 1));
                    return;
                }
                android.os.Handler mainHandler = new android.os.Handler(Looper.getMainLooper());
                mainHandler.post(() -> callback.onFailure("网络错误: " + e.getMessage()));
            }
        }).start();
    }
}
