package com.github.tvbox.osc.ui.activity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;

/**
 * HTML5游戏仿真器 — 全屏WebView加载在线游戏
 */
public class GameEmulatorActivity extends BaseActivity {

    private WebView wvGame;
    private View llTopBar;
    private TextView tvGameName;
    private TextView tvLoading;
    private String gameUrl;
    private String gameName;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_game_emulator;
    }

    @Override
    protected void init() {
        gameName = getIntent().getStringExtra("game_name");
        gameUrl  = getIntent().getStringExtra("game_url");

        wvGame = findViewById(R.id.wvGame);
        llTopBar = findViewById(R.id.llTopBar);
        tvGameName = findViewById(R.id.tvGameName);
        tvLoading = findViewById(R.id.tvLoading);
        ImageView btnExit = findViewById(R.id.btnExit);

        if (gameName != null) tvGameName.setText(gameName);

        btnExit.setOnClickListener(v -> finish());

        setupWebView();
        if (gameUrl != null && !gameUrl.isEmpty()) {
            wvGame.loadUrl(gameUrl);
        }
    }

    private void setupWebView() {
        WebSettings settings = wvGame.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        wvGame.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                tvLoading.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                tvLoading.setVisibility(View.GONE);
            }
        });

        wvGame.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 80) {
                    tvLoading.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (wvGame.canGoBack()) {
                wvGame.goBack();
                return true;
            }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (wvGame != null) {
            wvGame.destroy();
        }
        super.onDestroy();
    }
}
