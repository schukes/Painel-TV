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
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "PainelPrefs";

    private static final int DEFAULT_INTERVAL_SECONDS = 30;
    private static final int MIN_INTERVAL_SECONDS = 10;
    private static final int MAX_INTERVAL_SECONDS = 3600;

    // Delay de carregamento: tempo que o painel de trás espera, DEPOIS de carregar,
    // para o Power BI desenhar os visuais antes de trocar com o painel da frente.
    // Configurável na tela de configurações (chave "delay", em segundos).
    private static final int DEFAULT_DELAY_SECONDS = 10;
    private static final int MIN_DELAY_SECONDS = 0;
    private static final int MAX_DELAY_SECONDS = 60;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 10000L;

    private static final int MAX_CSS_ATTEMPTS = 8;
    private static final long CSS_DELAY_MS = 2500L;

    private static final int LARGURA_VIRTUAL = 1920;

    // Se o próximo painel não terminar de carregar nesse tempo, desiste e mantém o atual na tela.
    private static final long PRELOAD_TIMEOUT_MS = 60000L;

    private WebView webView;        // FRENTE: o que está visível
    private WebView webViewBack;    // TRÁS: carrega o próximo link escondido
    private FrameLayout container;
    private View overlayCapturador;
    private TextView bannerStatus;
    private final List<String> playlistLinks = new ArrayList<>();

    private String usuarioBi = "";
    private String senhaBi = "";

    private int currentIndex = 0;   // link que está na frente
    private int targetIndex = 0;    // link que está sendo carregado atrás
    private int retryCount = 0;
    private int cssAttempts = 0;

    private long intervaloMs = DEFAULT_INTERVAL_SECONDS * 1000L;
    private long renderDelayMs = DEFAULT_DELAY_SECONDS * 1000L;
    private long navigationId = 0L;

    private boolean activityDestroyed = false;
    private boolean pageLoaded = false;
    private boolean fitClicado = false;
    private boolean emTelaDeLogin = false;

    private int backPressCount = 0;
    private long lastBackPressTime = 0L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Runnable playlistRunnable;
    private Runnable retryRunnable;
    private Runnable cssRunnable;
    private Runnable loginRunnable;
    private Runnable swapRunnable;
    private Runnable timeoutRunnable;

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
        carregarPrimeiroLink();
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
        int delaySegundos = prefs.getInt("delay", DEFAULT_DELAY_SECONDS);
        usuarioBi = prefs.getString("usuario", "");
        senhaBi = prefs.getString("senha", "");

        tempoSegundos = Math.max(MIN_INTERVAL_SECONDS, Math.min(MAX_INTERVAL_SECONDS, tempoSegundos));
        intervaloMs = tempoSegundos * 1000L;

        delaySegundos = Math.max(MIN_DELAY_SECONDS, Math.min(MAX_DELAY_SECONDS, delaySegundos));
        renderDelayMs = delaySegundos * 1000L;

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

    // ------------------------------------------------------------------------------------------
    // CRIAÇÃO DOS WEBVIEWS (frente + trás)
    // ------------------------------------------------------------------------------------------

    private WebView novoWebView() {
        WebView wv = new WebView(this);
        wv.setBackgroundColor(Color.parseColor("#141923"));
        wv.setVerticalScrollBarEnabled(false);
        wv.setHorizontalScrollBarEnabled(false);
        wv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        wv.setFocusable(false);
        wv.setFocusableInTouchMode(false);

        int larguraTela = getResources().getDisplayMetrics().widthPixels;
        int escala = (int) Math.round(larguraTela * 100.0 / LARGURA_VIRTUAL);
        wv.setInitialScale(escala);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
        }

        WebSettings settings = wv.getSettings();
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

        wv.setWebViewClient(new PainelClient());
        return wv;
    }

    private void criarWebView() {
        CookieManager.getInstance().setAcceptCookie(true);

        webView = novoWebView();
        webViewBack = novoWebView();

        container = new FrameLayout(this);
        // Ordem importa: o de trás é adicionado primeiro (fica embaixo), o da frente por cima.
        container.addView(webViewBack, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        container.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

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

        bannerStatus = new TextView(this);
        bannerStatus.setText("Aperte OK, Setas ou 3x Voltar para Configurações");
        bannerStatus.setTextColor(Color.WHITE);
        bannerStatus.setTextSize(16);
        bannerStatus.setBackgroundColor(Color.parseColor("#CC000000"));
        bannerStatus.setPadding(24, 12, 24, 12);
        FrameLayout.LayoutParams lpDica = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        lpDica.setMargins(0, 0, 32, 32);
        container.addView(bannerStatus, lpDica);

        setContentView(container);
        overlayCapturador.requestFocus();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!emTelaDeLogin && bannerStatus != null) {
                    bannerStatus.setVisibility(View.GONE);
                }
            }
        }, 10000L);
    }

    // ------------------------------------------------------------------------------------------
    // WEBVIEW CLIENT (compartilhado pelos dois WebViews; diferencia por "view == webView/Back")
    // ------------------------------------------------------------------------------------------

    private class PainelClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (activityDestroyed) return;

            if (view == webViewBack) {
                // O link novo caiu na tela de login da Microsoft (cookie vencido):
                // traz esse WebView para a frente para o login/MFA funcionar.
                if (ehUrlLogin(url)) {
                    trocarFrenteETras();
                } else {
                    return;
                }
            }
            if (view != webView) return;

            pageLoaded = false;
            fitClicado = false;
            cancelarTarefasDePagina();
            verificarModoLogin(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (activityDestroyed) return;
            if (url != null && url.startsWith("about:blank")) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().flush();
            }

            // Página de trás terminou de carregar: prepara e espera o delay de renderização
            if (view == webViewBack) {
                aoTerminarCarregamentoDeTras(url);
                return;
            }
            if (view != webView) return;

            // ---- Página da FRENTE terminou (primeira abertura ou fluxo de login) ----
            pageLoaded = true;
            retryCount = 0;
            cssAttempts = 0;

            verificarModoLogin(url);

            if (emTelaDeLogin) {
                if (playlistRunnable != null) {
                    handler.removeCallbacks(playlistRunnable);
                }
                agendarAutoLogin(navigationId);
            } else {
                injetarViewportVirtual(webView);
                agendarAjusteVisual(webView, navigationId);
                agendarProximaTroca(navigationId);

                if (!usuarioBi.isEmpty()) {
                    agendarAutoLogin(navigationId);
                }
                if (overlayCapturador != null) {
                    overlayCapturador.requestFocus();
                }
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.isForMainFrame()) {
                tratarFalha(view);
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                tratarFalha(view);
            }
        }
    }

    private void tratarFalha(WebView view) {
        if (view == webViewBack) {
            tratarFalhaDePreload();
        } else if (view == webView) {
            tratarFalhaDeCarregamento();
        }
    }

    // ------------------------------------------------------------------------------------------
    // LOGIN
    // ------------------------------------------------------------------------------------------

    private boolean ehUrlLogin(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("login.microsoftonline.com")
                || u.contains("login.live.com")
                || u.contains("login.windows.net")
                || u.contains("/adfs/ls")
                || u.contains("oauth2/authorize");
    }

    private void verificarModoLogin(String url) {
        if (url == null) return;
        boolean isLogin = ehUrlLogin(url);

        if (isLogin != emTelaDeLogin) {
            emTelaDeLogin = isLogin;
            if (emTelaDeLogin) {
                overlayCapturador.setVisibility(View.GONE);
                webView.setFocusable(true);
                webView.setFocusableInTouchMode(true);
                webView.requestFocus();

                bannerStatus.setText("Autenticando no Power BI... (Aperte 3x VOLTAR para Configurações)");
                bannerStatus.setVisibility(View.VISIBLE);
            } else {
                webView.setFocusable(false);
                webView.setFocusableInTouchMode(false);
                overlayCapturador.setVisibility(View.VISIBLE);
                overlayCapturador.requestFocus();

                bannerStatus.setVisibility(View.GONE);
            }
        }
    }

    private void agendarAutoLogin(final long idDaPagina) {
        if (activityDestroyed || webView == null) return;
        if (usuarioBi.isEmpty() && senhaBi.isEmpty()) return;

        if (loginRunnable != null) {
            handler.removeCallbacks(loginRunnable);
        }

        loginRunnable = new Runnable() {
            @Override
            public void run() {
                loginRunnable = null;
                if (activityDestroyed || webView == null || idDaPagina != navigationId) return;

                executarScriptAutoLogin();

                if (emTelaDeLogin) {
                    agendarAutoLogin(idDaPagina);
                }
            }
        };

        handler.postDelayed(loginRunnable, 2000L);
    }

    private void executarScriptAutoLogin() {
        if (webView == null || activityDestroyed) return;

        final String userJson = JSONObject.quote(usuarioBi);
        final String passJson = JSONObject.quote(senhaBi);

        String js =
                "(function() {" +
                "  var USER = " + userJson + ";" +
                "  var PASS = " + passJson + ";" +
                "  function visivel(el) {" +
                "    return el && el.offsetParent !== null && !el.disabled;" +
                "  }" +
                "  function preencherInput(el, val) {" +
                "    el.focus();" +
                "    var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;" +
                "    if (setter) { setter.call(el, val); } else { el.value = val; }" +
                "    el.dispatchEvent(new Event('input', { bubbles: true }));" +
                "    el.dispatchEvent(new Event('change', { bubbles: true }));" +
                "    el.blur();" +
                "  }" +

                "  var kmsi = document.getElementById('KmsiCheckboxField');" +
                "  var btnSubmit = document.getElementById('idSIButton9');" +
                "  if (visivel(kmsi) && visivel(btnSubmit)) {" +
                "    if (!kmsi.checked) kmsi.click();" +
                "    btnSubmit.click();" +
                "    return 'KMSI_CLICKED';" +
                "  }" +

                "  var passInput = document.querySelector('input[name=\"passwd\"], input[type=\"password\"]');" +
                "  if (visivel(passInput) && PASS.length > 0) {" +
                "    if (passInput.getAttribute('data-preenchido') !== '1') {" +
                "      passInput.setAttribute('data-preenchido', '1');" +
                "      preencherInput(passInput, PASS);" +
                "      setTimeout(function() {" +
                "        var btn = document.getElementById('idSIButton9') || document.querySelector('input[type=\"submit\"], button[type=\"submit\"]');" +
                "        if (visivel(btn)) btn.click();" +
                "      }, 600);" +
                "      return 'PASS_FILLED';" +
                "    }" +
                "  }" +

                "  var emailInput = document.querySelector('input[name=\"loginfmt\"], input[type=\"email\"], input#email');" +
                "  if (visivel(emailInput) && USER.length > 0 && !visivel(passInput)) {" +
                "    if (emailInput.getAttribute('data-preenchido') !== '1') {" +
                "      emailInput.setAttribute('data-preenchido', '1');" +
                "      preencherInput(emailInput, USER);" +
                "      setTimeout(function() {" +
                "        var btn = document.getElementById('idSIButton9') || document.getElementById('submitBtn') || document.querySelector('input[type=\"submit\"], button[type=\"submit\"], button.submitBtn');" +
                "        if (visivel(btn)) btn.click();" +
                "      }, 600);" +
                "      return 'USER_FILLED';" +
                "    }" +
                "  }" +

                "  if (USER.length > 0) {" +
                "    var tiles = document.querySelectorAll('[data-test-id], .table[role=\"button\"]');" +
                "    for (var i = 0; i < tiles.length; i++) {" +
                "      if ((tiles[i].innerText || '').toLowerCase().indexOf(USER.toLowerCase()) >= 0) {" +
                "        tiles[i].click();" +
                "        return 'TILE_CLICKED';" +
                "      }" +
                "    }" +
                "  }" +

                "  return 'NONE';" +
                "})();";

        webView.evaluateJavascript(js, null);
    }

    // ------------------------------------------------------------------------------------------
    // CARREGAMENTO E TROCA SEM TELA DE LOADING
    // ------------------------------------------------------------------------------------------

    /** Só na abertura do app: carrega direto na frente (ainda não existe painel anterior). */
    private void carregarPrimeiroLink() {
        if (activityDestroyed || webView == null || playlistLinks.isEmpty()) return;

        cancelarTodasAsTarefas();

        navigationId++;
        pageLoaded = false;
        retryCount = 0;
        cssAttempts = 0;
        fitClicado = false;

        webView.loadUrl(playlistLinks.get(currentIndex));
    }

    /** Agenda o início do pré-carregamento do próximo painel (atrás do atual). */
    private void agendarProximaTroca(final long idAtual) {
        if (playlistRunnable != null) {
            handler.removeCallbacks(playlistRunnable);
        }

        playlistRunnable = new Runnable() {
            @Override
            public void run() {
                playlistRunnable = null;
                if (activityDestroyed || idAtual != navigationId || emTelaDeLogin) return;

                if (playlistLinks.size() == 1) {
                    atualizarRelatorioUnicoOuRecarregar();
                } else {
                    iniciarPreload((currentIndex + 1) % playlistLinks.size());
                }
            }
        };

        // Começa a carregar o próximo um pouco antes do fim do intervalo, para que a troca
        // aconteça aproximadamente quando o tempo configurado acabar.
        long espera = Math.max(1000L, intervaloMs - renderDelayMs);
        handler.postDelayed(playlistRunnable, espera);
    }

    private void atualizarRelatorioUnicoOuRecarregar() {
        if (webView == null || activityDestroyed) return;

        String jsRefresh =
                "(function() {" +
                "  var btns = document.querySelectorAll('button, [role=\"button\"]');" +
                "  for (var i = 0; i < btns.length; i++) {" +
                "    var t = ((btns[i].getAttribute('title') || '') + ' ' + (btns[i].getAttribute('aria-label') || '')).toLowerCase();" +
                "    if (t.indexOf('atualizar') >= 0 || t.indexOf('refresh') >= 0) {" +
                "      btns[i].click();" +
                "      return true;" +
                "    }" +
                "  }" +
                "  return false;" +
                "})();";

        final long idAtual = navigationId;
        webView.evaluateJavascript(jsRefresh, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String clicouRefresh) {
                if (activityDestroyed || idAtual != navigationId) return;
                if ("true".equals(clicouRefresh)) {
                    agendarProximaTroca(navigationId);
                } else {
                    // Sem botão interno: carrega o mesmo link atrás e troca (sem piscar)
                    iniciarPreload(currentIndex);
                }
            }
        });
    }

    /** Carrega o link indicado no WebView de TRÁS, enquanto o da frente continua congelado. */
    private void iniciarPreload(int indice) {
        if (activityDestroyed || webViewBack == null || playlistLinks.isEmpty()) return;

        cancelarTodasAsTarefas();

        navigationId++;
        targetIndex = indice;
        pageLoaded = false;
        retryCount = 0;
        cssAttempts = 0;
        fitClicado = false;

        final long idDoPreload = navigationId;
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                timeoutRunnable = null;
                if (activityDestroyed || idDoPreload != navigationId) return;
                abandonarPreload();
            }
        };
        handler.postDelayed(timeoutRunnable, PRELOAD_TIMEOUT_MS);

        webViewBack.loadUrl(playlistLinks.get(targetIndex));
    }

    /** O WebView de trás terminou de carregar: aplica o ajuste visual e agenda a troca. */
    private void aoTerminarCarregamentoDeTras(String url) {
        pageLoaded = true;
        retryCount = 0;

        // Já recebeu resposta; cancela o timeout de carregamento
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }

        injetarViewportVirtual(webViewBack);
        agendarAjusteVisual(webViewBack, navigationId);

        if (swapRunnable != null) {
            handler.removeCallbacks(swapRunnable);
        }

        final long idDoPreload = navigationId;
        swapRunnable = new Runnable() {
            @Override
            public void run() {
                swapRunnable = null;
                if (activityDestroyed || idDoPreload != navigationId || emTelaDeLogin) return;

                trocarFrenteETras();

                // O painel novo já está carregado e ajustado: só reagenda o ciclo
                agendarProximaTroca(navigationId);
                if (!usuarioBi.isEmpty()) {
                    agendarAutoLogin(navigationId);
                }
                if (overlayCapturador != null) {
                    overlayCapturador.requestFocus();
                }
            }
        };
        handler.postDelayed(swapRunnable, renderDelayMs);
    }

    /** Troca os papéis: o WebView de trás vira a frente (visível) e o antigo vai para trás. */
    private void trocarFrenteETras() {
        if (webView == null || webViewBack == null) return;

        if (swapRunnable != null) {
            handler.removeCallbacks(swapRunnable);
            swapRunnable = null;
        }
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }

        WebView antigaFrente = webView;
        webView = webViewBack;
        webViewBack = antigaFrente;
        currentIndex = targetIndex;

        // Sobe o novo painel na ordem de desenho, mantendo overlay e banner por cima dele
        webView.bringToFront();
        overlayCapturador.bringToFront();
        bannerStatus.bringToFront();

        // O painel antigo vai para trás: para tudo e libera memória
        webViewBack.setFocusable(false);
        webViewBack.setFocusableInTouchMode(false);
        webViewBack.stopLoading();
        webViewBack.loadUrl("about:blank");

        verificarModoLogin(webView.getUrl());
    }

    private void abandonarPreload() {
        if (webViewBack != null) {
            webViewBack.stopLoading();
            webViewBack.loadUrl("about:blank");
        }
        cancelarTodasAsTarefas();
        navigationId++;
        // Mantém o painel atual na tela e tenta o próximo ciclo normalmente
        agendarProximaTroca(navigationId);
    }

    private void tratarFalhaDePreload() {
        if (activityDestroyed || webViewBack == null || retryRunnable != null) return;

        if (retryCount >= MAX_RETRIES) {
            abandonarPreload();
            return;
        }

        final long idDaFalha = navigationId;
        retryCount++;

        retryRunnable = new Runnable() {
            @Override
            public void run() {
                retryRunnable = null;
                if (activityDestroyed || webViewBack == null || idDaFalha != navigationId) return;
                webViewBack.loadUrl(playlistLinks.get(targetIndex));
            }
        };
        handler.postDelayed(retryRunnable, RETRY_DELAY_MS);
    }

    /** Falha no WebView da frente (primeira abertura / login). */
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

    // ------------------------------------------------------------------------------------------
    // AJUSTE VISUAL (viewport, CSS, "Ajustar à página") — recebe o WebView alvo
    // ------------------------------------------------------------------------------------------

    private void injetarViewportVirtual(WebView alvo) {
        if (alvo == null || activityDestroyed) return;

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

        alvo.evaluateJavascript(js, null);
    }

    private void agendarAjusteVisual(final WebView alvo, final long idDaPagina) {
        if (activityDestroyed || alvo == null) return;

        if (cssRunnable != null) {
            handler.removeCallbacks(cssRunnable);
        }

        cssRunnable = new Runnable() {
            @Override
            public void run() {
                cssRunnable = null;
                if (activityDestroyed || idDaPagina != navigationId || !pageLoaded) return;

                aplicarEstiloEClicarEmAjustar(alvo);

                cssAttempts++;
                if (cssAttempts < MAX_CSS_ATTEMPTS) {
                    agendarAjusteVisual(alvo, idDaPagina);
                }
            }
        };

        handler.postDelayed(cssRunnable, CSS_DELAY_MS);
    }

    private void aplicarEstiloEClicarEmAjustar(WebView alvo) {
        if (alvo == null || activityDestroyed) return;

        final String deveClicar = fitClicado ? "false" : "true";

        String js =
                "(function() {" +
                "  var css = '" +
                "    html, body { margin:0 !important; padding:0 !important;" +
                "      background:#141923 !important; overflow:hidden !important; }" +
                "    ::-webkit-scrollbar { display:none !important; width:0 !important; height:0 !important; }" +
                "    .logoBar, .statusbar, .pbi-status-bar, .bottomBar," +
                "    app-header, nav-pane, .appBar, .actionBar, #pbiAppHeader," +
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

        alvo.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String resultado) {
                if ("true".equals(resultado)) {
                    fitClicado = true;
                }
            }
        });
    }

    // ------------------------------------------------------------------------------------------
    // TAREFAS
    // ------------------------------------------------------------------------------------------

    private void cancelarTarefasDePagina() {
        if (retryRunnable != null) {
            handler.removeCallbacks(retryRunnable);
            retryRunnable = null;
        }
        if (cssRunnable != null) {
            handler.removeCallbacks(cssRunnable);
            cssRunnable = null;
        }
        if (loginRunnable != null) {
            handler.removeCallbacks(loginRunnable);
            loginRunnable = null;
        }
        if (swapRunnable != null) {
            handler.removeCallbacks(swapRunnable);
            swapRunnable = null;
        }
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
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

    private boolean processarTeclaControle(int tecla, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        // 1) Botão Voltar 3 vezes seguidas abre as configurações SEMPRE (mesmo em tela de login)
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

        // Na tela de login da Microsoft, deixa as setas e o OK funcionarem dentro da página (MFA)
        if (emTelaDeLogin) {
            if (tecla == KeyEvent.KEYCODE_MENU || tecla == KeyEvent.KEYCODE_SETTINGS) {
                abrirConfiguracoes();
                return true;
            }
            return false;
        }

        // 2) Fora da tela de login (exibindo o painel): qualquer tecla abre as configurações
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
            if (!emTelaDeLogin && overlayCapturador != null) {
                overlayCapturador.requestFocus();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!activityDestroyed && webView != null) {
            ocultarBarrasSistema();
            if (!emTelaDeLogin && overlayCapturador != null) {
                overlayCapturador.requestFocus();
            }
        }
    }

    private void destruirWebView(WebView wv) {
        if (wv == null) return;
        wv.stopLoading();
        wv.setWebViewClient(new WebViewClient());
        wv.loadUrl("about:blank");
        wv.clearHistory();
        wv.removeAllViews();
        wv.destroy();
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        cancelarTodasAsTarefas();
        handler.removeCallbacksAndMessages(null);
        destruirWebView(webView);
        destruirWebView(webViewBack);
        webView = null;
        webViewBack = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Tratado no processarTeclaControle (3x Voltar)
    }
}