package com.painel.tv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    // COLE SEU LINK DO POWER BI ENTRE AS ASPAS ABAIXO:
    private static final String POWER_BI_URL = "COLE_SEU_LINK_DO_POWER_BI_AQUI";

    // Tempo de atualização: 30 segundos (30000 milissegundos)
    private static final long TEMPO_ATUALIZACAO_MS = 30000;

    private WebView webView;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (webView != null) {
                webView.reload(); // Atualiza o painel
            }
            // Agenda a próxima atualização para daqui a 30 segundos
            refreshHandler.postDelayed(this, TEMPO_ATUALIZACAO_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mantém a tela da TV sempre acesa
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ocultarBarrasSistema();

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // --- AJUSTE AUTOMÁTICO DE ESCALA PARA TELA DA TV ---
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(100);
        // Simula navegador de PC em tela grande para o Power BI não carregar versão mobile
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Desativa barras de rolagem na tela da TV
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        // Ajusta a escala inicial (0 = automático conforme a resolução da TV)
        webView.setInitialScale(0);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Esconde a barra de rodapé do Power BI ("Publish to Web") e trava o scroll para ocupar 100% da tela
                String jsAjusteTela = "javascript:(function() {" +
                        "var style = document.createElement('style');" +
                        "style.innerHTML = '.logoBarWrapper, .logoBar { display: none !important; } body, html { overflow: hidden !important; margin: 0 !important; padding: 0 !important; }';" +
                        "document.head.appendChild(style);" +
                        "})()";
                view.evaluateJavascript(jsAjusteTela, null);
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

        // Inicia o ciclo de atualização a cada 30 segundos
        refreshHandler.postDelayed(refreshRunnable, TEMPO_ATUALIZACAO_MS);
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