package com.painel.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "PainelPrefs";

    private static final int DEFAULT_INTERVAL_SECONDS = 30;
    private static final int MIN_INTERVAL_SECONDS = 10;
    private static final int MAX_INTERVAL_SECONDS = 3600;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 10000L;

    private static final int MAX_CSS_ATTEMPTS = 6;
    private static final long CSS_DELAY_MS = 2000L;

    private static final int LARGURA_VIRTUAL = 1920;

    private WebView webView;
    private View overlayCapturador; // Camada invisível que garante o foco do controle remoto
    private final List<String> playlistLinks = new ArrayList<>();

    private int currentIndex = 0;
    private int retryCount = 0;
    private int cssAttempts = 0;

    private long intervaloMs = DEFAULT_INTERVAL_SECONDS * 1000L;
    private long navigationId = 0L;

    private boolean activityDestroyed = false;
    private boolean pageLoaded = false;
    private boolean fitClicado = false;

    // Contador para abrir configurações apertando VOLTAR 3 vezes rápido
    private int backPressCount = 0;
    private long lastBackPressTime = 0L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Runnable playlistRunnable;
    private Runnable retryRunnable;
    private Runnable cssRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        carregarConfiguracoes();

        if (playlistLinks.isEmpty()) {
            abrirConfiguracoes();
            return;
        }

        configurarTela();
        criarWebView();
        carregarLinkAtual();
    }

    private void configurarTela() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ocultarBarrasSistema();
    }

    private void carregarConfiguracoes() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String rawLinks = prefs.getString("links", "");
        int tempoSegundos = prefs.getInt("tempo", DEFAULT_INTERVAL_SECONDS);

        tempoSegundos = Math.max(MIN_INTERVAL_SECONDS, Math.min(MAX_INTERVAL_SECONDS, tempoSegundos));
        intervaloMs = tempoSegundos * 1000L;

        playlistLinks.clear();
        if (rawLinks != null && !rawLinks.trim().isEmpty()) {
            String[] links = rawLinks.split("[,\\r\\n]+");
            for (String link : links) {
                String limpo = link.trim();
                if (limpo.isEmpty()) continue;

                String urlLower = limpo.toLowerCase(Locale.ROOT);
                if (urlLower.startsWith("https://") || urlLower.startsWith("http://")) {
                    playlistLinks.add(limpo);
                }
            }
        }
    }

    private void criarWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#141923"));
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // IMPORTANTE: Desativa o foco no WebView para ele NÃO engolir os comandos do controle da TV!
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);

        int larguraTela = getResources().getDisplayMetrics().widthPixels;
        int escala = (int) Math.round(larguraTela * 100.0 / LARGURA_VIRTUAL);
        webView.setInitialScale(escala);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageLoaded = false;
                fitClicado = false;
                cancelarTarefasDePagina();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (activityDestroyed || view != webView) return;

                pageLoaded = true;
                retryCount = 0;
                cssAttempts = 0;

                injetarViewportVirtual();
                agendarAjusteVisual(navigationId);

                // Garante que o foco continue na nossa camada invisível e não no WebView
                if (overlayCapturador != null) {
                    overlayCapturador.requestFocus();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.isForMainFrame()) {
                    tratarFalhaDeCarregamento();
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    tratarFalhaDeCarregamento();
                }
            }
        });

        FrameLayout container = new FrameLayout(this);
        container.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Camada transparente por cima do WebView que recebe o foco do controle remoto e cliques
        overlayCapturador = new View(this);
        overlayCapturador.setBackgroundColor(Color.TRANSPARENT);
        overlayCapturador.setFocusable(true);
        overlayCapturador.setFocusableInTouchMode(true);
        overlayCapturador.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirConfiguracoes();
            }
        });
        overlayCapturador.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                return processarTeclaControle(keyCode, event);
            }
        });
        container.addView(overlayCapturador, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        final TextView dica = new TextView(this);
        dica.setText("Aperte OK, Setas ou 3x Voltar para Configurações");
        dica.setTextColor(Color.WHITE);
        dica.setTextSize(16);
        dica.setBackgroundColor(Color.parseColor("#CC000000"));
        dica.setPadding(24, 12, 24, 12);
        FrameLayout.LayoutParams lpDica = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        lpDica.setMargins(0, 0, 32, 32);
        container.addView(dica, lpDica);

        setContentView(container);
        overlayCapturador.requestFocus();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dica.setVisibility(View.GONE);
            }
        }, 10000L);
    }

    private void carregarLinkAtual() {
        if (activityDestroyed || webView == null || playlistLinks.isEmpty()) return;

        cancelarTodasAsTarefas();

        navigationId++;
        final long idAtual = navigationId;

        pageLoaded = false;
        retryCount = 0;
        cssAttempts = 0;
        fitClicado = false;

        webView.loadUrl(playlistLinks.get(currentIndex));

        playlistRunnable = new Runnable() {
            @Override
            public void run() {
                if (activityDestroyed || idAtual != navigationId) return;

                if (!playlistLinks.isEmpty()) {
                    currentIndex = (currentIndex + 1) % playlistLinks.size();
                    carregarLinkAtual();
                }
            }
        };

        handler.postDelayed(playlistRunnable, intervaloMs);
    }

    private void tratarFalhaDeCarregamento() {
        if (activityDestroyed || pageLoaded || webView == null
                || retryRunnable != null || retryCount >= MAX_RETRIES) return;

        final long idDaFalha = navigationId;
        retryCount++;

        retryRunnable = new Runnable() {
            @Override
            public void run() {
                retryRunnable = null;
                if (activityDestroyed || webView == null
                        || idDaFalha != navigationId || pageLoaded) return;

                webView.loadUrl(playlistLinks.get(currentIndex));
            }
        };

        handler.postDelayed(retryRunnable, RETRY_DELAY_MS);
    }

    private void injetarViewportVirtual() {
        if (webView == null || activityDestroyed) return;

        String js =
                "(function(){" +
                "  var m = document.querySelector('meta[name=viewport]');" +
                "  if (!m) {" +
                "    m = document.createElement('meta');" +
                "    m.name = 'viewport';" +
                "    if (document.head) document.head.appendChild(m);" +
                "  }" +
                "  m.setAttribute('content', 'width=" + LARGURA_VIRTUAL + ", initial-scale=1');" +
                "  window.dispatchEvent(new Event('resize'));" +
                "})();";

        webView.evaluateJavascript(js, null);
    }

    private void agendarAjusteVisual(final long idDaPagina) {
        if (activityDestroyed || webView == null) return;

        if (cssRunnable != null) {
            handler.removeCallbacks(cssRunnable);
        }

        cssRunnable = new Runnable() {
            @Override
            public void run() {
                cssRunnable = null;
                if (activityDestroyed || webView == null
                        || idDaPagina != navigationId || !pageLoaded) return;

                aplicarEstiloEClicarEmAjustar();

                cssAttempts++;
                if (cssAttempts < MAX_CSS_ATTEMPTS) {
                    agendarAjusteVisual(idDaPagina);
                }
            }
        };

        handler.postDelayed(cssRunnable, CSS_DELAY_MS);
    }

    private void aplicarEstiloEClicarEmAjustar() {
        if (webView == null || activityDestroyed) return;

        final String deveClicar = fitClicado ? "false" : "true";

        String js =
                "(function() {" +
                "  var css = '" +
                "    html, body { margin:0 !important; padding:0 !important;" +
                "      background:#141923 !important; overflow:hidden !important; }" +
                "    ::-webkit-scrollbar { display:none !important; width:0 !important; height:0 !important; }" +
                "    .logoBar, .statusbar, .pbi-status-bar, .bottomBar," +
                "    [class*=\"logoBar\"], [class*=\"statusBar\"], [class*=\"statusbar\"]" +
                "      { display:none !important; }" +
                "  ';" +
                "  var style = document.getElementById('painel-tv-custom-style');" +
                "  if (!style && document.head) {" +
                "    style = document.createElement('style');" +
                "    style.id = 'painel-tv-custom-style';" +
                "    document.head.appendChild(style);" +
                "  }" +
                "  if (style) { style.textContent = css; }" +
                "  var achou = false;" +
                "  if (" + deveClicar + ") {" +
                "    var els = document.querySelectorAll('button, [role=\"button\"]');" +
                "    for (var i = 0; i < els.length; i++) {" +
                "      var t = ((els[i].getAttribute('title') || '') + ' ' +" +
                "               (els[i].getAttribute('aria-label') || '')).toLowerCase();" +
                "      if (t.indexOf('ajustar à página') >= 0 || t.indexOf('fit to page') >= 0) {" +
                "        els[i].click();" +
                "        achou = true;" +
                "        break;" +
                "      }" +
                "    }" +
                "  }" +
                "  window.dispatchEvent(new Event('resize'));" +
                "  return achou;" +
                "})();";

        webView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String resultado) {
                if ("true".equals(resultado)) {
                    fitClicado = true;
                }
            }
        });
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

    private void abrirConfiguracoes() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Centraliza a detecção de teclas do controle remoto.
     */
    private boolean processarTeclaControle(int tecla, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        // 1) Botão Voltar: se apertar 3 vezes seguidas rápido (ou segurar), abre as configurações
        if (tecla == KeyEvent.KEYCODE_BACK) {
            if (event.getRepeatCount() > 0) {
                abrirConfiguracoes();
                return true;
            }
            long agora = SystemClock.uptimeMillis();
            if (agora - lastBackPressTime < 1500) {
                backPressCount++;
            } else {
                backPressCount = 1;
            }
            lastBackPressTime = agora;

            if (backPressCount >= 3) {
                backPressCount = 0;
                abrirConfiguracoes();
            } else {
                Toast.makeText(this, "Aperte VOLTAR mais " + (3 - backPressCount) + "x para Configurações", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        // 2) Qualquer tecla de confirmação, menu ou setas direcionais abre as configurações
        if (tecla == KeyEvent.KEYCODE_DPAD_CENTER
                || tecla == KeyEvent.KEYCODE_ENTER
                || tecla == KeyEvent.KEYCODE_NUMPAD_ENTER
                || tecla == KeyEvent.KEYCODE_SPACE
                || tecla == KeyEvent.KEYCODE_BUTTON_A
                || tecla == KeyEvent.KEYCODE_MENU
                || tecla == KeyEvent.KEYCODE_SETTINGS
                || tecla == KeyEvent.KEYCODE_INFO
                || tecla == KeyEvent.KEYCODE_GUIDE
                || tecla == KeyEvent.KEYCODE_DPAD_UP
                || tecla == KeyEvent.KEYCODE_DPAD_DOWN
                || tecla == KeyEvent.KEYCODE_DPAD_LEFT
                || tecla == KeyEvent.KEYCODE_DPAD_RIGHT) {
            abrirConfiguracoes();
            return true;
        }

        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (processarTeclaControle(event.getKeyCode(), event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
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
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !activityDestroyed && webView != null) {
            ocultarBarrasSistema();
            if (overlayCapturador != null) overlayCapturador.requestFocus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!activityDestroyed && webView != null) {
            ocultarBarrasSistema();
            if (overlayCapturador != null) overlayCapturador.requestFocus();
        }
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        cancelarTodasAsTarefas();
        handler.removeCallbacksAndMessages(null);
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
        // Tratado no processarTeclaControle (3 cliques para abrir configurações)
    }
}