
package com.painel.tv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
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
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "PainelPrefs";
    private static final String DEFAULT_URL = "https://app.powerbi.com/";
    private static final int DEFAULT_INTERVAL_SECONDS = 30;
    private static final int MIN_INTERVAL_SECONDS = 10;
    private static final int MAX_INTERVAL_SECONDS = 3600;

    private static final int MAX_RETRIES = 3;
    private static final int MAX_CSS_ATTEMPTS = 6;

    private static final long RETRY_DELAY_MS = 10000L;
    private static final long CSS_DELAY_MS = 1500L;

    private WebView webView;

    private final List<String> playlistLinks = new ArrayList<>();

    private int currentIndex = 0;
    private int retryCount = 0;
    private int cssAttempts = 0;

    private long intervaloMs = DEFAULT_INTERVAL_SECONDS * 1000L;
    private long navigationId = 0L;

    private boolean activityDestroyed = false;
    private boolean pageLoaded = false;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private Runnable playlistRunnable;
    private Runnable retryRunnable;
    private Runnable cssRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        configurarTela();
        carregarConfiguracoes();
        criarWebView();

        if (playlistLinks.isEmpty()) {
            playlistLinks.add(DEFAULT_URL);
        }

        carregarLinkAtual();
    }

    private void configurarTela() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        ocultarBarrasSistema();
    }

    private void carregarConfiguracoes() {
        SharedPreferences prefs =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String rawLinks = prefs.getString("links", "");

        int tempoSegundos = prefs.getInt(
                "tempo",
                DEFAULT_INTERVAL_SECONDS
        );

        tempoSegundos = Math.max(
                MIN_INTERVAL_SECONDS,
                Math.min(MAX_INTERVAL_SECONDS, tempoSegundos)
        );

        intervaloMs = tempoSegundos * 1000L;

        playlistLinks.clear();

        if (rawLinks != null && !rawLinks.trim().isEmpty()) {
            String[] links = rawLinks.split("[,\\r\\n]+");

            for (String link : links) {
                String limpo = link.trim();

                if (limpo.isEmpty()) {
                    continue;
                }

                // Aceita apenas URLs HTTP e HTTPS.
                String urlLower = limpo.toLowerCase(Locale.ROOT);

                if (urlLower.startsWith("https://")
                        || urlLower.startsWith("http://")) {
                    playlistLinks.add(limpo);
                }
            }
        }
    }

    private void criarWebView() {
        webView = new WebView(this);

        webView.setBackgroundColor(
                Color.parseColor("#141923")
        );

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Usa o User-Agent padrão do WebView.
        // Só altere se houver um problema comprovado de compatibilidade.

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon
            ) {
                super.onPageStarted(view, url, favicon);

                pageLoaded = false;
                cancelarTarefasDePagina();
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {
                super.onPageFinished(view, url);

                if (activityDestroyed || view != webView) {
                    return;
                }

                pageLoaded = true;
                retryCount = 0;
                cssAttempts = 0;

                agendarAjusteVisual(navigationId);
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                super.onReceivedError(view, request, error);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && request.isForMainFrame()) {
                    tratarFalhaDeCarregamento();
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(
                    WebView view,
                    int errorCode,
                    String description,
                    String failingUrl
            ) {
                super.onReceivedError(
                        view,
                        errorCode,
                        description,
                        failingUrl
                );

                // Compatibilidade com versões antigas do Android.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    tratarFalhaDeCarregamento();
                }
            }
        });

        setContentView(webView);
    }

    private void carregarLinkAtual() {
        if (activityDestroyed
                || webView == null
                || playlistLinks.isEmpty()) {
            return;
        }

        cancelarTodasAsTarefas();

        navigationId++;
        final long idAtual = navigationId;

        pageLoaded = false;
        retryCount = 0;
        cssAttempts = 0;

        String url = playlistLinks.get(currentIndex);

        // Adiciona o parâmetro sem duplicá-lo.
        if (!url.matches("(?i).*([?&])chromeless=.*")) {
            url += (url.contains("?") ? "&" : "?")
                    + "chromeless=true";
        }

        webView.loadUrl(url);

        // O intervalo é contado a partir do início da navegação.
        // A playlist não fica parada indefinidamente se uma página travar.
        playlistRunnable = new Runnable() {
            @Override
            public void run() {
                if (activityDestroyed || idAtual != navigationId) {
                    return;
                }

                if (!playlistLinks.isEmpty()) {
                    currentIndex =
                            (currentIndex + 1) % playlistLinks.size();

                    carregarLinkAtual();
                }
            }
        };

        handler.postDelayed(playlistRunnable, intervaloMs);
    }

    private void tratarFalhaDeCarregamento() {
        if (activityDestroyed || pageLoaded || webView == null) {
            return;
        }

        if (retryRunnable != null) {
            return;
        }

        if (retryCount >= MAX_RETRIES) {
            // A playlist continuará avançando normalmente.
            return;
        }

        final long idDaFalha = navigationId;

        retryCount++;

        retryRunnable = new Runnable() {
            @Override
            public void run() {
                retryRunnable = null;

                if (activityDestroyed
                        || webView == null
                        || idDaFalha != navigationId
                        || pageLoaded) {
                    return;
                }

                String url = playlistLinks.get(currentIndex);

                if (!url.matches("(?i).*([?&])chromeless=.*")) {
                    url += (url.contains("?") ? "&" : "?")
                            + "chromeless=true";
                }

                webView.loadUrl(url);
            }
        };

        handler.postDelayed(retryRunnable, RETRY_DELAY_MS);
    }

    private void agendarAjusteVisual(final long idDaPagina) {
        if (activityDestroyed || webView == null) {
            return;
        }

        if (cssRunnable != null) {
            handler.removeCallbacks(cssRunnable);
        }

        cssRunnable = new Runnable() {
            @Override
            public void run() {
                cssRunnable = null;

                if (activityDestroyed
                        || webView == null
                        || idDaPagina != navigationId
                        || !pageLoaded) {
                    return;
                }

                aplicarAjusteVisual();

                cssAttempts++;

                if (cssAttempts < MAX_CSS_ATTEMPTS) {
                    agendarAjusteVisual(idDaPagina);
                }
            }
        };

        handler.postDelayed(cssRunnable, CSS_DELAY_MS);
    }

    private void aplicarAjusteVisual() {
        if (webView == null || activityDestroyed) {
            return;
        }

        String js =
                "(function() {" +
                "  var css = `" +

                "    html, body {" +
                "      margin: 0 !important;" +
                "      padding: 0 !important;" +
                "      background: #141923 !important;" +
                "      overflow-x: hidden !important;" +
                "    }" +

                "    ::-webkit-scrollbar {" +
                "      display: none !important;" +
                "      width: 0 !important;" +
                "      height: 0 !important;" +
                "    }" +

                "    .report-navigation-outer," +
                "    .report-navigation," +
                "    .report-navigation-wrapper," +
                "    [class*='report-navigation']," +
                "    [class*='pageNavigation']," +
                "    [class*='zoomBar']," +
                "    [class*='logoBar'] {" +
                "      display: none !important;" +
                "      visibility: hidden !important;" +
                "      pointer-events: none !important;" +
                "    }" +

                "  `;" +

                "  var style = document.getElementById(" +
                "    'painel-tv-custom-style');" +

                "  if (!style && document.head) {" +
                "    style = document.createElement('style');" +
                "    style.id = 'painel-tv-custom-style';" +
                "    document.head.appendChild(style);" +
                "  }" +

                "  if (style) {" +
                "    style.textContent = css;" +
                "  }" +

                "  var botoes = document.querySelectorAll('button');" +

                "  for (var i = 0; i < botoes.length; i++) {" +
                "    var titulo = botoes[i].getAttribute('title') || '';" +
                "    var aria = botoes[i].getAttribute('aria-label') || '';" +
                "    var texto = titulo + ' ' + aria;" +

                "    if (/fit to page|fit to width|ajustar à página|" +
                "ajustar a página|ajustar à largura|" +
                "ajustar a largura/i.test(texto)) {" +
                "      botoes[i].click();" +
                "      break;" +
                "    }" +
                "  }" +

                "})();";

        webView.evaluateJavascript(js, null);
    }

    private void cancelarTarefasDePagina() {
        if (retryRunnable != null) {
            handler.removeCallbacks(retryRunnable);
            retryRunnable = null;
        }

        if (cssRunnable != null) {
            handler.removeCallbacks(cssRunnable);
            cssRunnable = null;
        }
    }

    private void cancelarTodasAsTarefas() {
        if (playlistRunnable != null) {
            handler.removeCallbacks(playlistRunnable);
            playlistRunnable = null;
        }

        cancelarTarefasDePagina();
    }

    private void ocultarBarrasSistema() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && !activityDestroyed) {
            ocultarBarrasSistema();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!activityDestroyed) {
            ocultarBarrasSistema();
        }
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;

        cancelarTodasAsTarefas();

        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(new WebViewClient());
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Mantém o botão Voltar bloqueado.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
}
