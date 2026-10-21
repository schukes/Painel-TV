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
import android.widget.ProgressBar;
import android.widget.LinearLayout;
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

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 10000L;

    // Verificações de prontidão do relatório em segundo plano
    private static final int MAX_CHECK_ATTEMPTS = 12; // Até ~18s esperando os gráficos desenharem em background
    private static final long CHECK_DELAY_MS = 1500L;

    private static final int LARGURA_VIRTUAL = 1920;

    // DUPLO BUFFER: dois WebViews para carregar em segundo plano sem piscar a tela!
    private WebView webViewA;
    private WebView webViewB;
    private WebView webViewVisivel;   // O que está aparecendo na TV agora
    private WebView webViewCarregando; // O que está carregando escondido atrás

    private LinearLayout telaLoadingInicial; // Só aparece no 1º carregamento ao abrir o app
    private View overlayCapturador;
    private TextView bannerStatus;

    private final List<String> playlistLinks = new ArrayList<>();

    private String usuarioBi = "";
    private String senhaBi = "";

    private int currentIndex = 0;
    private int retryCount = 0;
    private int checkAttempts = 0;

    private long intervaloMs = DEFAULT_INTERVAL_SECONDS * 1000L;
    private long navigationId = 0L;

    private boolean activityDestroyed = false;
    private boolean pageLoaded = false;
    private boolean fitClicado = false;
    private boolean emTelaDeLogin = false;
    private boolean primeiropainelExibido = false;

    private int backPressCount = 0;
    private long lastBackPressTime = 0L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Runnable playlistRunnable;
    private Runnable retryRunnable;
    private Runnable checkReadyRunnable;
    private Runnable loginRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        carregarConfiguracoes();

        if (playlistLinks.isEmpty()) {
            abrirConfiguracoes();
            return;
        }

        configurarTela();
        criarInterfaceDuploBuffer();
        iniciarCarregamentoEmSegundoPlano();
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
        usuarioBi = prefs.getString("usuario", "");
        senhaBi = prefs.getString("senha", "");

        tempoSegundos = Math.max(MIN_INTERVAL_SECONDS, Math.min(TEMPO_MAX_PERMITIDO(), tempoSegundos));
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

    private int TEMPO_MAX_PERMITIDO() {
        return MAX_INTERVAL_SECONDS;
    }

    // ------------------------------------------------------------------
    // CRIAÇÃO DOS DOIS WEBVIEWS (DUPLO BUFFER)
    // ------------------------------------------------------------------
    private void criarInterfaceDuploBuffer() {
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(Color.parseColor("#141923"));

        webViewA = criarInstanciaWebView();
        webViewB = criarInstanciaWebView();

        // No início, A é o que vai carregar primeiro, B fica de reserva
        webViewVisivel = webViewB;
        webViewCarregando = webViewA;

        webViewA.setAlpha(0f);
        webViewB.setAlpha(0f);

        container.addView(webViewB, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        container.addView(webViewA, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Tela escura elegante apenas para o PRIMEIRO carregamento ao ligar a TV
        telaLoadingInicial = new LinearLayout(this);
        telaLoadingInicial.setOrientation(LinearLayout.VERTICAL);
        telaLoadingInicial.setGravity(Gravity.CENTER);
        telaLoadingInicial.setBackgroundColor(Color.parseColor("#141923"));

        ProgressBar spinner = new ProgressBar(this);
        telaLoadingInicial.addView(spinner, new LinearLayout.LayoutParams(100, 100));

        TextView txtCarregando = new TextView(this);
        txtCarregando.setText("Preparando Painel Power BI...");
        txtCarregando.setTextColor(Color.WHITE);
        txtCarregando.setTextSize(20);
        txtCarregando.setPadding(0, 32, 0, 0);
        txtCarregando.setGravity(Gravity.CENTER);
        telaLoadingInicial.addView(txtCarregando);

        container.addView(telaLoadingInicial, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Camada transparente para capturar o controle remoto
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

    private WebView criarInstanciaWebView() {
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

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(wv, true);
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

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (view != webViewCarregando) return;

                pageLoaded = false;
                fitClicado = false;
                cancelarTarefasDePagina();
                verificarModoLogin(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (activityDestroyed || view != webViewCarregando) return;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush();
                }

                pageLoaded = true;
                retryCount = 0;
                checkAttempts = 0;

                verificarModoLogin(url);

                if (emTelaDeLogin) {
                    if (playlistRunnable != null) {
                        handler.removeCallbacks(playlistRunnable);
                    }
                    agendarAutoLogin(navigationId);
                } else {
                    // Página terminou o HTML inicial: injeta o viewport e começa a preparar os gráficos escondido!
                    injetarViewportVirtual(webViewCarregando);
                    if (!usuarioBi.isEmpty()) {
                        agendarAutoLogin(navigationId);
                    }
                    verificarSePainelEstaProntoParaExibir(navigationId);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (view == webViewCarregando && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.isForMainFrame()) {
                    tratarFalhaDeCarregamento();
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                if (view == webViewCarregando && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    tratarFalhaDeCarregamento();
                }
            }
        });

        return wv;
    }

    // ------------------------------------------------------------------
    // TRANSIÇÃO SUAVE E PREPARAÇÃO EM SEGUNDO PLANO
    // ------------------------------------------------------------------

    /**
     * Inicia o carregamento do próximo link no WebView que está OCULTO (alpha 0),
     * mantendo o WebView atual visível e congelado na tela para o usuário não ver o carregamento!
     */
    private void iniciarCarregamentoEmSegundoPlano() {
        if (activityDestroyed || playlistLinks.isEmpty()) return;

        cancelarTodasAsTarefas();

        navigationId++;
        pageLoaded = false;
        retryCount = 0;
        checkAttempts = 0;
        fitClicado = false;

        // Garante que o WebView que vai carregar esteja invisível atrás do atual
        webViewCarregando.setAlpha(0f);
        webViewCarregando.loadUrl(playlistLinks.get(currentIndex));
    }

    /**
     * Roda em segundo plano no WebView oculto:
     * 1) Limpa barras do Power BI com CSS;
     * 2) Clica em "Ajustar à página";
     * 3) Verifica se os visuais/gráficos do Power BI já renderizaram e se os spinners de loading sumiram.
     * Só quando está bonito (ou após o limite de segurança), faz o Fade-In para a TV!
     */
    private void verificarSePainelEstaProntoParaExibir(final long idDaPagina) {
        if (activityDestroyed || webViewCarregando == null || emTelaDeLogin) return;

        if (checkReadyRunnable != null) {
            handler.removeCallbacks(checkReadyRunnable);
        }

        checkReadyRunnable = new Runnable() {
            @Override
            public void run() {
                checkReadyRunnable = null;
                if (activityDestroyed || webViewCarregando == null
                        || idDaPagina != navigationId || !pageLoaded || emTelaDeLogin) return;

                checkAttempts++;
                final String deveClicar = fitClicado ? "false" : "true";

                String js =
                        "(function() {" +
                        // 1) Aplica o CSS que limpa margens e esconde barras e logos de carregamento feios
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

                        // 2) Clica uma única vez em 'Ajustar à página'
                        "  var clicouFit = false;" +
                        "  if (" + deveClicar + ") {" +
                        "    var els = document.querySelectorAll('button, [role=\"button\"]');" +
                        "    for (var i = 0; i < els.length; i++) {" +
                        "      var t = ((els[i].getAttribute('title') || '') + ' ' +" +
                        "               (els[i].getAttribute('aria-label') || '')).toLowerCase();" +
                        "      if (t.indexOf('ajustar à página') >= 0 || t.indexOf('fit to page') >= 0) {" +
                        "        els[i].click();" +
                        "        clicouFit = true;" +
                        "        break;" +
                        "      }" +
                        "    }" +
                        "  }" +
                        "  window.dispatchEvent(new Event('resize'));" +

                        // 3) Verifica se os gráficos do Power BI já apareceram na tela e se o spinner principal sumiu
                        "  var temGraficos = document.querySelectorAll('visual-modern, .visualContainer, .visual, canvas, svg').length > 2;" +
                        "  var carregandoSpinner = document.querySelector('.pbi-svg-loading, .spinner, .loading-spinner, #pbi-svg-loading');" +
                        "  var spinnerVisivel = carregandoSpinner && carregandoSpinner.offsetParent !== null;" +
                        "  var pronto = temGraficos && !spinnerVisivel;" +
                        "  return (clicouFit ? '1' : '0') + ':' + (pronto ? 'READY' : 'WAIT');" +
                        "})();";

                webViewCarregando.evaluateJavascript(js, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String valor) {
                        if (activityDestroyed || idDaPagina != navigationId) return;

                        String resp = valor != null ? valor.replace("\"", "") : "";
                        if (resp.startsWith("1:")) {
                            fitClicado = true;
                        }

                        boolean relatorioPronto = resp.endsWith(":READY");

                        // Esperamos pelo menos 2 ciclos (3 segundos) para o "Ajustar à página" assentar
                        // e trocamos assim que os gráficos estiverem prontos (ou se atingir o tempo máximo)
                        if ((relatorioPronto && checkAttempts >= 2) || checkAttempts >= MAX_CHECK_ATTEMPTS) {
                            revelarNovoPainelComFade(idDaPagina);
                        } else {
                            verificarSePainelEstaProntoParaExibir(idDaPagina);
                        }
                    }
                });
            }
        };

        handler.postDelayed(checkReadyRunnable, CHECK_DELAY_MS);
    }

    /**
     * Faz a troca suave (Crossfade) entre o painel antigo e o painel novo já 100% carregado!
     */
    private void revelarNovoPainelComFade(final long idDaPagina) {
        if (activityDestroyed || idDaPagina != navigationId) return;

        final WebView novoVisivel = webViewCarregando;
        final WebView antigoVisivel = webViewVisivel;

        // Traz o WebView recém-carregado para a frente com transição suave de 600ms
        novoVisivel.bringToFront();
        if (overlayCapturador != null) overlayCapturador.bringToFront();
        if (bannerStatus != null) bannerStatus.bringToFront();

        novoVisivel.animate()
                .alpha(1f)
                .setDuration(600)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (activityDestroyed) return;
                        antigoVisivel.setAlpha(0f);

                        // Esconde a tela de loading inicial (se ainda estava visível na 1ª abertura)
                        if (!primeiropainelExibido && telaLoadingInicial != null) {
                            primeiropainelExibido = true;
                            telaLoadingInicial.animate().alpha(0f).setDuration(400).withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    telaLoadingInicial.setVisibility(View.GONE);
                                }
                            }).start();
                        }

                        if (!emTelaDeLogin && overlayCapturador != null) {
                            overlayCapturador.requestFocus();
                        }
                    }
                }).start();

        // Inverte os papéis para o próximo ciclo da playlist!
        webViewVisivel = novoVisivel;
        webViewCarregando = antigoVisivel;

        // Continua aplicando o CSS mais 2 vezes no painel visível caso algum gráfico atrase
        aplicarReforcoCssPosExibicao(novoVisivel, 2);

        // Agenda a contagem de tempo (ex: 30s) a partir de AGORA (que o painel realmente apareceu!)
        agendarProximaTroca(idDaPagina);
    }

    private void aplicarReforcoCssPosExibicao(final WebView alvo, final int restantes) {
        if (restantes <= 0 || activityDestroyed || alvo == null) return;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activityDestroyed || alvo != webViewVisivel || emTelaDeLogin) return;
                injetarViewportVirtual(alvo);
                aplicarReforcoCssPosExibicao(alvo, restantes - 1);
            }
        }, 2500L);
    }

    // ------------------------------------------------------------------
    // LOGIN AUTOMÁTICO E PLAYLIST
    // ------------------------------------------------------------------
    private void verificarModoLogin(String url) {
        if (url == null) return;
        String u = url.toLowerCase(Locale.ROOT);
        boolean isLogin = u.contains("login.microsoftonline.com")
                || u.contains("login.live.com")
                || u.contains("login.windows.net")
                || u.contains("/adfs/ls")
                || u.contains("oauth2/authorize");

        if (isLogin != emTelaDeLogin) {
            emTelaDeLogin = isLogin;
            if (emTelaDeLogin) {
                // Se precisar de login manual/2FA na 1ª vez, mostra o WebView de login na tela
                if (telaLoadingInicial != null) telaLoadingInicial.setVisibility(View.GONE);
                webViewCarregando.bringToFront();
                webViewCarregando.setAlpha(1f);

                overlayCapturador.setVisibility(View.GONE);
                webViewCarregando.setFocusable(true);
                webViewCarregando.setFocusableInTouchMode(true);
                webViewCarregando.requestFocus();

                bannerStatus.bringToFront();
                bannerStatus.setText("Autenticando no Power BI... (Aperte 3x VOLTAR para Configurações)");
                bannerStatus.setVisibility(View.VISIBLE);
            } else {
                webViewCarregando.setFocusable(false);
                webViewCarregando.setFocusableInTouchMode(false);
                overlayCapturador.setVisibility(View.VISIBLE);
                overlayCapturador.bringToFront();
                overlayCapturador.requestFocus();

                bannerStatus.setVisibility(View.GONE);
            }
        }
    }

    private void agendarAutoLogin(final long idDaPagina) {
        if (activityDestroyed || webViewCarregando == null) return;
        if (usuarioBi.isEmpty() && senhaBi.isEmpty()) return;

        if (loginRunnable != null) {
            handler.removeCallbacks(loginRunnable);
        }

        loginRunnable = new Runnable() {
            @Override
            public void run() {
                loginRunnable = null;
                if (activityDestroyed || webViewCarregando == null || idDaPagina != navigationId) return;

                executarScriptAutoLogin(webViewCarregando);

                if (emTelaDeLogin) {
                    agendarAutoLogin(idDaPagina);
                }
            }
        };

        handler.postDelayed(loginRunnable, 2000L);
    }

    private void executarScriptAutoLogin(WebView alvo) {
        if (alvo == null || activityDestroyed) return;

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

        alvo.evaluateJavascript(js, null);
    }

    private void agendarProximaTroca(final long idAtual) {
        if (playlistRunnable != null) {
            handler.removeCallbacks(playlistRunnable);
        }

        playlistRunnable = new Runnable() {
            @Override
            public void run() {
                if (activityDestroyed || idAtual != navigationId || emTelaDeLogin) return;

                if (playlistLinks.size() == 1) {
                    // Com 1 link, tenta atualizar os dados sem recarregar a página;
                    // se não tiver botão interno, carrega no WebView oculto e faz crossfade!
                    atualizarRelatorioUnicoSemPiscar();
                } else if (!playlistLinks.isEmpty()) {
                    currentIndex = (currentIndex + 1) % playlistLinks.size();
                    iniciarCarregamentoEmSegundoPlano();
                }
            }
        };

        handler.postDelayed(playlistRunnable, intervaloMs);
    }

    private void atualizarRelatorioUnicoSemPiscar() {
        if (webViewVisivel == null || activityDestroyed) return;

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

        webViewVisivel.evaluateJavascript(jsRefresh, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String clicouRefresh) {
                if ("true".equals(clicouRefresh)) {
                    agendarProximaTroca(navigationId);
                } else {
                    // Carrega a nova versão no WebView oculto enquanto mantém o atual congelado na tela!
                    iniciarCarregamentoEmSegundoPlano();
                }
            }
        });
    }

    private void tratarFalhaDeCarregamento() {
        if (activityDestroyed || pageLoaded || webViewCarregando == null
                || retryRunnable != null || retryCount >= MAX_RETRIES) return;

        final long idDaFalha = navigationId;
        retryCount++;

        retryRunnable = new Runnable() {
            @Override
            public void run() {
                retryRunnable = null;
                if (activityDestroyed || webViewCarregando == null
                        || idDaFalha != navigationId || pageLoaded) return;

                webViewCarregando.loadUrl(playlistLinks.get(currentIndex));
            }
        };

        handler.postDelayed(retryRunnable, RETRY_DELAY_MS);
    }

    private void injetarViewportVirtual(WebView alvo) {
        if (alvo == null || activityDestroyed || emTelaDeLogin) return;

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

    private void cancelarTarefasDePagina() {
        if (retryRunnable != null) {
            handler.removeCallbacks(retryRunnable);
            retryRunnable = null;
        }
        if (checkReadyRunnable != null) {
            handler.removeCallbacks(checkReadyRunnable);
            checkReadyRunnable = null;
        }
        if (loginRunnable != null) {
            handler.removeCallbacks(loginRunnable);
            loginRunnable = null;
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

        if (emTelaDeLogin) {
            if (tecla == KeyEvent.KEYCODE_MENU || tecla == KeyEvent.KEYCODE_SETTINGS) {
                abrirConfiguracoes();
                return true;
            }
            return false;
        }

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
        if (hasFocus && !activityDestroyed) {
            ocultarBarrasSistema();
            if (!emTelaDeLogin && overlayCapturador != null) {
                overlayCapturador.requestFocus();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!activityDestroyed) {
            ocultarBarrasSistema();
            if (!emTelaDeLogin && overlayCapturador != null) {
                overlayCapturador.requestFocus();
            }
        }
    }

    private void destruirWebViewSeguro(WebView wv) {
        if (wv != null) {
            wv.stopLoading();
            wv.setWebViewClient(new WebViewClient());
            wv.loadUrl("about:blank");
            wv.clearHistory();
            wv.removeAllViews();
            wv.destroy();
        }
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        cancelarTodasAsTarefas();
        handler.removeCallbacksAndMessages(null);
        destruirWebViewSeguro(webViewA);
        destruirWebViewSeguro(webViewB);
        webViewA = null;
        webViewB = null;
        webViewVisivel = null;
        webViewCarregando = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Tratado no processarTeclaControle (3x Voltar)
    }
}