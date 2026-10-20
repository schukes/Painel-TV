package com.painel.tv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private WebView webView;
    private List<String> playlistLinks = new ArrayList<>();
    private int currentIndex = 0;
    private long intervaloMs = 30000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int ESCALA_TV = 57;

    private final Runnable playlistRunnable = new Runnable() {
        @Override
        public void run() {
            if (!playlistLinks.isEmpty()) {
                currentIndex = (currentIndex + 1) % playlistLinks.size();
                carregarLinkAtual();
            }
            handler.postDelayed(this, intervaloMs);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ocultarBarrasSistema();

        SharedPreferences prefs = getSharedPreferences("PainelPrefs", MODE_PRIVATE);
        String rawLinks = prefs.getString("links", "");
        int tempoSegundos = prefs.getInt("tempo", 30);
        intervaloMs = tempoSegundos * 1000L;

        playlistLinks.clear();
        if (!rawLinks.isEmpty()) {
            String[] split = rawLinks.split("[,\\n]+");
            for (String l : split) {
                String limpo = l.trim();
                if (!limpo.isEmpty()) {
                    playlistLinks.add(limpo);
                }
            }
        }

        if (playlistLinks.isEmpty()) {
            playlistLinks.add("https://app.powerbi.com/");
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#141923"));
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setInitialScale(ESCALA_TV);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.setInitialScale(ESCALA_TV);
                aplicarEstiloTelaCheia(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    handler.postDelayed(() -> carregarLinkAtual(), 10000);
                }
            }
        });

        setContentView(webView);
        carregarLinkAtual();

        handler.postDelayed(playlistRunnable, intervaloMs);
    }

    private void carregarLinkAtual() {
        if (webView != null && !playlistLinks.isEmpty()) {
            String url = playlistLinks.get(currentIndex);
            if (!url.contains("chromeless=")) {
                url += (url.contains("?") ? "&" : "?") + "chromeless=true";
            }
            webView.loadUrl(url);
        }
    }

    private void aplicarEstiloTelaCheia(WebView view) {
        String js = "javascript:(function() {" +
                "  var css = '" +
                "    ::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; }" +
                "    html, body { margin: 0 !important; padding: 0 !important; overflow: hidden !important; background-color: #141923 !important; width: 100vw !important; height: 100vh !important; }" +
                "    .logoBarWrapper, .logoBar, .zoomBar, div[class*=\"logoBar\"], div[class*=\"zoom\"], footer { display: none !important; height: 0 !important; opacity: 0 !important; pointer-events: none !important; }" +
                "  ';" +
                "  var style = document.getElementById('estilo-playlist-tv');" +
                "  if (!style) {" +
                "    style = document.createElement('style');" +
                "    style.id = 'estilo-playlist-tv';" +
                "    style.innerHTML = css;" +
                "    document.head.appendChild(style);" +
                "  }" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(playlistRunnable);
    }

    private void ocultarBarrasSistema() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            ocultarBarrasSistema();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}