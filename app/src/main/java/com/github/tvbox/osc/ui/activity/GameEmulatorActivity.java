package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;

/**
 * 游戏模拟器 - WebView + 手柄/遥控器按键映射
 *
 * 按键映射表 (Android TV 遥控器 → 游戏键盘事件):
 *   DPAD_UP(19)      → ArrowUp
 *   DPAD_DOWN(20)    → ArrowDown
 *   DPAD_LEFT(21)    → ArrowLeft
 *   DPAD_RIGHT(22)   → ArrowRight
 *   DPAD_CENTER(23)  → Space  (跳跃/确认)
 *   ENTER(66)        → Enter
 *   BACK(4)          → Escape (退出游戏用MENU)
 *   MENU(82)         → 显示/隐藏顶栏
 *   蓝牙手柄按钮      → 自动透传
 */
public class GameEmulatorActivity extends BaseActivity {

    private WebView wvGame;
    private View llTopBar;
    private TextView tvLoading;
    private String gameUrl;

    private boolean isPageLoaded = false;
    private boolean isDestroyed = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_game_emulator;
    }

    @Override
    protected void init() {
        gameUrl = getIntent().getStringExtra("game_url");

        initView();
        loadGame();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initView() {
        wvGame = findViewById(R.id.wvGame);
        llTopBar = findViewById(R.id.llTopBar);
        tvLoading = findViewById(R.id.tvLoading);

        // 退出按钮
        findViewById(R.id.btnExit).setOnClickListener(v -> exitGame());

        // WebView 配置
        WebSettings settings = wvGame.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        wvGame.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                tvLoading.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                tvLoading.setVisibility(View.GONE);
                isPageLoaded = true;
            }
        });

        wvGame.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                TextView tvName = findViewById(R.id.tvGameName);
                if (tvName != null) tvName.setText(title);
            }
        });
    }

    /**
     * 注入按键映射 JS
     * Android TV WebView 收到的键盘事件 keyCode 是 Android 原生码
     * 这里转成游戏能识别的标准 Web KeyBoardEvent
     * 同时支持蓝牙手柄 (PS/Xbox/Switch Pro) 按钮
     */
    private void injectKeyMappingScript() {
        String script =
            "if(!window.__km_inj){" +
            "  window.__km_inj=true;" +
            "  var K={" +
            "    19:'ArrowUp',20:'ArrowDown',21:'ArrowLeft',22:'ArrowRight'," +
            "    23:' ',66:'Enter',4:'Escape',82:'Menu'," +
            "    96:' ',97:'Escape',99:'Enter',100:'Enter'," +
            "    102:'ShiftLeft',103:'ShiftRight',108:'Enter',109:'Escape'" +
            "  };" +
            "  document.addEventListener('keydown',function(e){" +
            "    var k=K[e.keyCode];" +
            "    if(k){" +
            "      var ev=new KeyboardEvent('keydown',{" +
            "        key:k,code:k,bubbles:true,cancelable:true" +
            "      });" +
            "      document.dispatchEvent(ev);" +
            "      e.preventDefault();" +
            "    }" +
            "  });" +
            "  document.addEventListener('keyup',function(e){" +
            "    var k=K[e.keyCode];" +
            "    if(k){" +
            "      var ev=new KeyboardEvent('keyup',{" +
            "        key:k,code:k,bubbles:true,cancelable:true" +
            "      });" +
            "      document.dispatchEvent(ev);" +
            "      e.preventDefault();" +
            "    }" +
            "  });" +
            "}";
        wvGame.evaluateJavascript(script, null);

        // 额外给 body 也挂一份 (有些游戏监听 body 而非 document)
        String bodyScript =
            "if(!window.__km_bodyinj){" +
            "  window.__km_bodyinj=true;" +
            "  (function(){" +
            "    var b=document.body||document.documentElement;" +
            "    if(!b)return;" +
    "    var K={" +
    "      19:'ArrowUp',20:'ArrowDown',21:'ArrowLeft',22:'ArrowRight'," +
    "      23:' ',66:'Enter',4:'Escape',82:'Menu'," +
    "      96:' ',97:'Escape',99:'Enter',100:'Enter'," +
    "      102:'ShiftLeft',103:'ShiftRight',108:'Enter',109:'Escape'" +
    "    };" +
            "    b.addEventListener('keydown',function(e){" +
            "      var k=K[e.keyCode];" +
            "      if(k){" +
            "        e.preventDefault();e.stopPropagation();" +
            "        var ev=new KeyboardEvent('keydown',{key:k,code:k,bubbles:true});" +
            "        document.dispatchEvent(ev);" +
            "      }" +
            "    },true);" +
            "    b.addEventListener('keyup',function(e){" +
            "      var k=K[e.keyCode];" +
            "      if(k){e.preventDefault();e.stopPropagation();" +
            "        var ev=new KeyboardEvent('keyup',{key:k,code:k,bubbles:true});" +
            "        document.dispatchEvent(ev);" +
            "      }" +
            "    },true);" +
            "  })();" +
            "}";
        wvGame.evaluateJavascript(bodyScript, null);
    }

    private void loadGame() {
        if (gameUrl != null && !gameUrl.isEmpty()) {
            wvGame.loadUrl(gameUrl);
        } else {
            Toast.makeText(this, "游戏地址无效", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void exitGame() {
        isDestroyed = true;
        wvGame.stopLoading();
        wvGame.loadUrl("about:blank");
        finish();
    }

    /**
     * Android TV 遥控器按键拦截 - 用 dispatchKeyEvent 在 View 层级之前截获
     * 确保遥控器方向键能正确传到 WebView 游戏页面
     * 蓝牙手柄按钮也走这里映射
     *
     * 映射表:
     *   DPAD_UP(19)      → ArrowUp
     *   DPAD_DOWN(20)    → ArrowDown
     *   DPAD_LEFT(21)    → ArrowLeft
     *   DPAD_RIGHT(22)   → ArrowRight
     *   DPAD_CENTER(23)  → Space (跳跃)
     *   ENTER(66)        → Enter
     *   BACK(4)          → 返回
     *   MENU(82)         → 显示顶栏
     *   手柄A(96)        → Space
     *   手柄B(97)        → Escape
     *   手柄X/Y(99/100)  → Enter
     *   手柄LB/RB(102/103) → Shift
     *   手柄START(108)   → Enter
     *   手柄SELECT(109)  → Escape
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isDestroyed) return super.dispatchKeyEvent(event);

        int action = event.getAction();
        int keyCode = event.getKeyCode();

        // MENU → 显示/隐藏顶栏 (只处理按下)
        if (action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
            toggleTopBar();
            return true;
        }

        // 方向键 + 功能键 → 注入到游戏页面的 JavaScript
        int[] gameKeys = {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT
        };
        boolean isGameKey = false;
        for (int k : gameKeys) {
            if (keyCode == k) { isGameKey = true; break; }
        }

        if (isGameKey && isPageLoaded && wvGame != null) {
            String jsAction = (action == KeyEvent.ACTION_DOWN) ? "keydown" : "keyup";
            String js = String.format(
                "javascript:(function(){" +
                "  var e=new KeyboardEvent('%s',{key:'%s',code:'%s',bubbles:true,cancelable:true});" +
                "  document.dispatchEvent(e);" +
                "})()",
                jsAction, getMappedKey(keyCode), getMappedKey(keyCode)
            );
            wvGame.loadUrl(js);
            return true; // 消费事件，不让WebView自己处理
        }

        return super.dispatchKeyEvent(event);
    }

    private String getMappedKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:    return "ArrowUp";
            case KeyEvent.KEYCODE_DPAD_DOWN:  return "ArrowDown";
            case KeyEvent.KEYCODE_DPAD_LEFT:  return "ArrowLeft";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "ArrowRight";
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:   return " ";
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_START: return "Enter";
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "Escape";
            case KeyEvent.KEYCODE_BUTTON_L1: return "ShiftLeft";
            case KeyEvent.KEYCODE_BUTTON_R1: return "ShiftRight";
            default: return "";
        }
    }

    private void toggleTopBar() {
        if (llTopBar == null) return;
        boolean visible = llTopBar.getVisibility() != View.VISIBLE;
        llTopBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            findViewById(R.id.btnExit).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        if (isDestroyed) {
            super.onBackPressed();
            return;
        }
        // 无顶栏时 → 显示顶栏
        if (llTopBar != null && llTopBar.getVisibility() != View.VISIBLE) {
            toggleTopBar();
        } else {
            exitGame();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
