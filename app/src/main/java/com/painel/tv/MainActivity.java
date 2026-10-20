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
        webView.setInitialScale(100);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                removerBarraInferiorTotal(view);
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

    // Script cirúrgico para ocultar a barra de navegação/rodapé do Power BI e expandir a tela
    private void removerBarraInferiorTotal(WebView view) {
        String js = "javascript:(function() {" +
                "  function executarAjuste() {" +
                "    var botoes = document.querySelectorAll('button');" +
                "    for (var i = 0; i < botoes.length; i++) {" +
                "      var titulo = botoes[i].getAttribute('title') || '';" +
                "      var aria = botoes[i].getAttribute('aria-label') || '';" +
                "      if (titulo.indexOf('Ajustar') !== -1 || titulo.indexOf('Fit') !== -1 ||" +
                "          aria.indexOf('Ajustar') !== -1 || aria.indexOf('Fit') !== -1) {" +
                "        botoes[i].click();" +
                "      }" +
                "    }" +
                "  }" +
                "  setTimeout(executarAjuste, 1500);" +
                "  setTimeout(executarAjuste, 3500);" +
                "  " +
                "  /* Destrói a barra inferior branca e expande o relatório para 100vh */" +
                "  var css = '" +
                "    ::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; }" +
                "    html, body { margin: 0 !important; padding: 0 !important; overflow: hidden !important; background-color: #141923 !important; width: 100vw !important; height: 100vh !important; }" +
                "    .logoBarWrapper, .logoBar, .zoomBar, .pbi-footer, footer, div[class*=\"logoBar\"], div[class*=\"footer\"], .report-navigation-outer, .pbi-side-pane, nav, .footer, .report-actions-container { display: none !important; height: 0 !important; opacity: 0 !important; pointer-events: none !important; visibility: hidden !important; }" +
                "    .reportContainer, .embedContainer, .pbi-absolute-container, .visualContainerHost { height: 100vh !important; max-height: 100vh !important; bottom: 0 !important; top: 0 !important; }" +
                "  ';" +
                "  var style = document.getElementById('estilo-mata-barra');" +
                "  if (!style) {" +
                "    style = document.createElement('style');" +
                "    style.id = 'estilo-mata-barra';" +
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
    public void onBackPressed() {
        // Bloqueia botão Voltar
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}