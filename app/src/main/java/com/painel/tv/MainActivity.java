package com.painel.tv;

import android.app.Activity;
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

public class MainActivity extends Activity {

    // COLE SEU LINK DO POWER BI ENTRE AS ASPAS ABAIXO:
    private static final String POWER_BI_URL = "https://app.powerbi.com/view?r=eyJrIjoiZjQ5N2M4NWQtNjEzNC00ZGQwLThhMjctNjhiYWY4ZGQ4MjhkIiwidCI6IjkxZGMyNjczLWMyMTYtNDQ0Yi04ZGRkLWJjNmE0YjkwNTRhYiJ9";

    // Tempo de atualização: 30 segundos (30000 milissegundos)
    private static final long TEMPO_ATUALIZACAO_MS = 30000;

    // Escala ideal para a resolução lógica da Google TV (57%)
    private static final int ESCALA_TV = 57;

    private WebView webView;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (webView != null) {
                webView.reload();
            }
            refreshHandler.postDelayed(this, TEMPO_ATUALIZACAO_MS);
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

        webView = new WebView(this);
        // Fundo da mesma cor do cabeçalho da Colibri para não piscar branco
        webView.setBackgroundColor(Color.parseColor("#141923"));

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // Define a escala exata automaticamente ao abrir
        webView.setInitialScale(ESCALA_TV);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(false);
        settings.setLoadWithOverviewMode(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.setInitialScale(ESCALA_TV);
                ajustarPainelTelaCheia(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> view.loadUrl(POWER_BI_URL), 15000);
                }
            }
        });

        setContentView(webView);
        webView.loadUrl(POWER_BI_URL);

        refreshHandler.postDelayed(refreshRunnable, TEMPO_ATUALIZACAO_MS);
    }

    // Remove a barra inferior de zoom, esconde as barras de rolagem e estica o relatório na tela toda
    private void ajustarPainelTelaCheia(WebView view) {
        String js = "javascript:(function() {" +
                "function aplicarAjuste() {" +
                "  /* 1. Clica sozinho no botão Ajustar à página se estiver em 100% */" +
                "  var botoesZoom = document.querySelectorAll('button[title*=\"Ajustar\"], button[title*=\"Fit\"], button[aria-label*=\"Ajustar\"], button[aria-label*=\"Fit\"], .fitToPageButton');" +
                "  botoesZoom.forEach(function(b) { b.click(); });" +
                "  /* 2. Esconde barra inferior de zoom, barras de rolagem e margens brancas */" +
                "  var css = '" +
                "    ::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; }" +
                "    html, body { margin: 0 !important; padding: 0 !important; overflow: hidden !important; background-color: #141923 !important; width: 100vw !important; height: 100vh !important; }" +
                "    .logoBarWrapper, .logoBar, .zoomBar, div[class*=\"logoBar\"], div[class*=\"zoom\"], footer { display: none !important; height: 0 !important; opacity: 0 !important; pointer-events: none !important; }" +
                "    .pvExplorationHost, .ExplorationContainer, .report-canvas, .visualContainerHost { background-color: #141923 !important; }" +
                "  ';" +
                "  var style = document.getElementById('estilo-colibri-tv');" +
                "  if (!style) {" +
                "    style = document.createElement('style');" +
                "    style.id = 'estilo-colibri-tv';" +
                "    style.innerHTML = css;" +
                "    document.head.appendChild(style);" +
                "  }" +
                "}" +
                "aplicarAjuste();" +
                "setInterval(aplicarAjuste, 1000);" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refreshHandler.removeCallbacks(refreshRunnable);
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