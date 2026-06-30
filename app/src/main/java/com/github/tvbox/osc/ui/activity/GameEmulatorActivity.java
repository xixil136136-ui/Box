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
 * 遥控器按键 → 游戏键盘事件映射:
 *   方向键 ↑ → ArrowUp
 *   方向键 ↓ → ArrowDown
 *   方向键 ← → ArrowLeft
 *   方向键 → → ArrowRight
 *   确定/OK → Space
 *   返回/Back → Escape
 *   菜单/Menu → Enter
 */
public class GameEmulatorActivity extends BaseActivity {

    private WebView wvGame;
    private View llTopBar;
    private TextView tvGameName;
    private TextView tvLoading;
    private String gameUrl;
    private String gameName;
    private boolean isPageLoaded = false;
    private boolean topBarVisible = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_game_emulator;
    }

    @Override
    protected void init() {
        gameName = getIntent().getStringExtra("game_name");
        gameUrl = getIntent().getStringExtra("game_url");

        initView();
        loadGame();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initView() {
        wvGame = findViewById(R.id.wvGame);
        llTopBar = findViewById(R.id.llTopBar);
        tvGameName = findViewById(R.id.tvGameName);
        tvLoading = findViewById(R.id.tvLoading);

        tvGameName.setText(gameName != null ? gameName : "游戏");

        // 退出按钮
        findViewById(R.id.btnExit).setOnClickListener(v -> exitGame());

        // WebView 配置
        WebSettings settings = wvGame.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
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
                // 注入按键映射JS
                injectKeyMappingScript();
            }
        });

        wvGame.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (gameName == null || gameName.isEmpty()) {
                    tvGameName.setText(title);
                }
            }
        });
    }

    /**
     * 注入按键映射 JavaScript
     * 将遥控器按键事件转为键盘事件发送到游戏页面
     */
    private void injectKeyMappingScript() {
        String script =
            "if(!window.__keyMapperInjected){" +
            "  window.__keyMapperInjected=true;" +
            "  document.addEventListener('keydown', function(e){" +
            "    var keyMap={" +
            "      37:'ArrowLeft',38:'ArrowUp',39:'ArrowRight',40:'ArrowDown'," +
            "      13:'Enter',32:' ',8:'Escape',27:'Escape'" +
            "    };" +
            "    if(keyMap[e.keyCode]){" +
            "      var evt=new KeyboardEvent('keydown',{key:keyMap[e.keyCode],code:keyMap[e.keyCode],bubbles:true});" +
            "      document.dispatchEvent(evt);" +
            "      e.preventDefault();" +
            "    }" +
            "  });" +
            "  document.addEventListener('keyup',function(e){" +
            "    var keyMap={" +
            "      37:'ArrowLeft',38:'ArrowUp',39:'ArrowRight',40:'ArrowDown'," +
            "      13:'Enter',32:' ',8:'Escape',27:'Escape'" +
            "    };" +
            "    if(keyMap[e.keyCode]){" +
            "      var evt=new KeyboardEvent('keyup',{key:keyMap[e.keyCode],code:keyMap[e.keyCode],bubbles:true});" +
            "      document.dispatchEvent(evt);" +
            "      e.preventDefault();" +
            "    }" +
            "  });" +
            "}";
        wvGame.evaluateJavascript(script, null);
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
        wvGame.stopLoading();
        wvGame.destroy();
        finish();
    }

    /**
     * 遥控器/手柄按键 → 游戏操控
     * KEYCODE_DPAD_* → 方向键Arrow
     * KEYCODE_ENTER/DPAD_CENTER → Space(很多游戏用空格跳跃/确认)
     * KEYCODE_BACK → Escape
     * KEYCODE_MENU → 显示/隐藏顶栏
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            // 两次MENU键显示顶栏
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
                toggleTopBar();
                return true;
            }

            // 发送按键到WebView
            if (isPageLoaded && wvGame != null) {
                // 为TV遥控器生成不同keyCode
                wvGame.dispatchKeyEvent(event);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void toggleTopBar() {
        topBarVisible = !topBarVisible;
        llTopBar.setVisibility(topBarVisible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (wvGame != null && wvGame.canGoBack()) {
            wvGame.goBack();
        } else {
            exitGame();
        }
    }

    @Override
    protected void onDestroy() {
        if (wvGame != null) {
            wvGame.destroy();
            wvGame = null;
        }
        super.onDestroy();
    }
}
