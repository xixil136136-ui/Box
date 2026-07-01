package com.github.tvbox.osc.util;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

/**
 * 全屏通用工具类
 *
 * 功能：
 * 1. 横屏锁定
 * 2. 沉浸式全屏（隐藏状态栏/导航栏/刘海屏适配）
 * 3. 防休眠锁
 *
 * 用法：
 *   ScreenUtils.goFullScreen(this);          // 一键全屏
 *   ScreenUtils.with(this).setLandscape().setImmersive().setKeepScreenOn().apply();
 *   // onWindowFocusChanged 中调用：
 *   ScreenUtils.with(this).reapplyImmersive(hasFocus);
 */
public class ScreenUtils {

    private final Activity activity;
    private boolean landscape = false;
    private boolean immersive = false;
    private boolean keepScreenOn = false;

    // ── 构造 ──
    private ScreenUtils(Activity activity) {
        this.activity = activity;
    }

    public static ScreenUtils with(Activity activity) {
        return new ScreenUtils(activity);
    }

    public ScreenUtils setLandscape() {
        this.landscape = true;
        return this;
    }

    public ScreenUtils setImmersive() {
        this.immersive = true;
        return this;
    }

    public ScreenUtils setKeepScreenOn() {
        this.keepScreenOn = true;
        return this;
    }

    /** 一键应用所有配置 */
    public void apply() {
        if (landscape)   applyLandscape();
        if (immersive)   applyImmersive();
        if (keepScreenOn) applyKeepScreenOn();
    }

    /** 一键全屏（横屏+沉浸+防休眠） */
    public static void goFullScreen(Activity activity) {
        with(activity)
                .setLandscape()
                .setImmersive()
                .setKeepScreenOn()
                .apply();
    }

    // ═══ 1. 横屏锁定 ═══
    private void applyLandscape() {
        activity.setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    // ═══ 2. 沉浸式全屏 ═══
    private void applyImmersive() {
        Window window = activity.getWindow();
        if (window == null) return;

        // 全屏 Flag
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // 隐藏系统栏（Android 4.4+）
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        // Android 11+ 新方式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars()
                        | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }

        // Android 9+ 刘海屏适配（不留黑边）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attrs);
        }
    }

    /** 在 onWindowFocusChanged 中重新应用沉浸模式 */
    public void reapplyImmersive(boolean hasFocus) {
        if (hasFocus && immersive) {
            activity.getWindow().getDecorView().postDelayed(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsetsController controller =
                            activity.getWindow().getInsetsController();
                    if (controller != null) {
                        controller.hide(WindowInsets.Type.statusBars()
                                | WindowInsets.Type.navigationBars());
                    }
                } else {
                    activity.getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    );
                }
            }, 100);
        }
    }

    // ═══ 3. 防休眠锁 ═══
    private void applyKeepScreenOn() {
        activity.getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }


    /** 判断当前设备是否是电视/盒子 */
    public static boolean isTv(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    /** 获取屏幕对角线尺寸（英寸） */
    public static double getSqrt(Activity activity) {
        WindowManager wm = activity.getWindowManager();
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        double x = Math.pow(dm.widthPixels / dm.xdpi, 2);
        double y = Math.pow(dm.heightPixels / dm.ydpi, 2);
        return Math.sqrt(x + y);
    }

    public void releaseKeepScreenOn() {
        if (keepScreenOn) {
            activity.getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
}
