package com.painel.tv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
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
    private static final String POWER_BI_URL = "https://app.powerbi.com/view?r=eyJrIjoiZjQ5N2M4NWQtNjEzNC00ZGQwLThhMjctNjhiYWY4ZGQ4MjhkIiwidCI6IjkxZGMyNjczLWMyMTYtNDQ0Yi04ZGRkLWJjNmE0YjkwNTRhYiJ9";
    
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mantém a tela da TV sempre acesa (bloqueia protetor de tela / suspensão)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Esconde todas as barras do sistema (Tela Cheia Imersiva)
        ocultarBarrasSistema();

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // Obrigatório para o Power BI renderizar os gráficos
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // Se a internet cair no galpão, tenta recarregar após 15 segundos
                if (request.isForMainFrame()) {
                    new Handler().postDelayed(() -> view.loadUrl(POWER_BI_URL), 15000);
                }
            }
        });

        setContentView(webView);
        webView.loadUrl(POWER_BI_URL);
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

    // Bloqueia o botão "Voltar" do controle remoto da TV
    @Override
    public void onBackPressed() {
        // Ignorado propositalmente para modo quiosque
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true; // Consome o clique do botão Voltar
        }
        return super.onKeyDown(keyCode, event);
    }
}