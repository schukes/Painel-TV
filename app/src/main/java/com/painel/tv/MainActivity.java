package com.painel.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

    // Mesmos parâmetros da sua versão original que funcionava perfeitamente!
    private static final int MAX_CSS_ATTEMPTS = 6;
    private static final long CSS_DELAY_MS = 2000L;
    private static final int LARGURA_VIRTUAL = 1920;

    private WebView webView;
    private ImageView cortinaCongelada;       // Segura a imagem do painel atual enquanto carrega o próximo por trás
    private LinearLayout telaLoadingInicial;  // Aparece só nos primeiros segundos ao abrir o app
    private View overlayCapturador;
    private TextView bannerStatus;

    private final List<String> playlistLinks = new ArrayList<>();

    private String usuarioBi = "";
    private String senhaBi = "";

    private int currentIndex = 0;
    private int retryCount = 0;
    private int cssAttempts = 0;

    private long intervaloMs = DEFAULT_INTERVAL_SECONDS * 1000L;
    private long navigationId = 0L;

    private boolean activityDestroyed = false;
    private boolean pageLoaded = false;
    private boolean fitClicado = false;
    private boolean emTelaDeLogin = false;
    private boolean cortinaReveladaNestaPagina = false;

    private int backPressCount = 0;
    private long lastBackPressTime = 0L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Runnable playlistRunnable;
    private Runnable retryRunnable;
    private Runnable cssRunnable;
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
        criarWebView();
        carregarLinkAtual(false);
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

    // ------------------------------------------------------------------
    // CRIAÇÃO DO WEBVIEW (EXATAMENTE COM A ESCALA DO SEU CÓDIGO ORIGINAL)
    // ------------------------------------------------------------------
    private void criarWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#141923"));
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);

        // Escala idêntica à original
        int larguraTela = getResources().getDisplayMetrics().widthPixels;
        int escala = (int) Math.round(larguraTela * 100.0 / LARGURA_VIRTUAL);
        webView.setInitialScale(escala);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

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
                cortinaReveladaNestaPagina = false;
                cancelarTarefasDePagina();
                verificarModoLogin(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (activityDestroyed || view != webView) return;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush();
                }

                pageLoaded = true;
                retryCount = 0;
                cssAttempts = 0;

                verificarModoLogin(url);

                if (emTelaDeLogin) {
                    if (playlistRunnable != null) {
                        handler.removeCallbacks(playlistRunnable);
                    }
                    esconderCortinaCarregamento();
                    agendarAutoLogin(navigationId);
                } else {
                    // 1) Força o viewport virtual de 1920 px exatamente como no original
                    injetarViewportVirtual();

                    // 2) Agenda o ajuste visual (CSS + clique em "Ajustar à página")
                    agendarAjusteVisual(navigationId);

                    if (!usuarioBi.isEmpty()) {
                        agendarAutoLogin(navigationId);
                    }
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

        // Camada que guarda a "foto" do gráfico atual para não mostrar a tela piscando ao recarregar
        cortinaCongelada = new ImageView(this);
        cortinaCongelada.setScaleType(ImageView.ScaleType.FIT_XY);
        cortinaCongelada.setVisibility(View.GONE);
        container.addView(cortinaCongelada, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Tela escura elegante apenas na primeira abertura do app
        telaLoadingInicial = new LinearLayout(this);
        telaLoadingInicial.setOrientation(LinearLayout.VERTICAL);
        telaLoadingInicial.setGravity(Gravity.CENTER);
        telaLoadingInicial.setBackgroundColor(Color.parseColor("#141923"));

        ProgressBar spinner = new ProgressBar(this);
        telaLoadingInicial.addView(spinner, new LinearLayout.LayoutParams(90, 90));

        TextView txtCarregando = new TextView(this);
        txtCarregando.setText("Carregando Painel...");
        txtCarregando.setTextColor(Color.WHITE);
        txtCarregando.setTextSize(18);
        txtCarregando.setPadding(0, 28, 0, 0);
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

    // ------------------------------------------------------------------
    // CONGELAMENTO DE IMAGEM (EVITA TELA FEIA NO CARREGAMENTO)
    // ------------------------------------------------------------------

    /**
     * Tira um print do WebView atual e coloca por cima dele enquanto a próxima página carrega por trás.
     */
    private void congelarTelaAtualAntesDeTrocar() {
        if (webView == null || cortinaCongelada == null || emTelaDeLogin) return;
        if (webView.getWidth() <= 0 || webView.getHeight() <= 0) return;

        try {
            Bitmap bmp = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bmp);
            webView.draw(canvas);
            cortinaCongelada.setImageBitmap(bmp);
            cortinaCongelada.setAlpha(1f);
            cortinaCongelada.setVisibility(View.VISIBLE);
        } catch (Throwable ignored) {
            // Se faltar memória na TV para criar o Bitmap, segue normalmente
        }
    }

    /**
     * Remove suavemente a foto congelada (ou a tela de abertura) depois que o Power BI já carregou
     * e já clicou no botão "Ajustar à página".
     */
    private void esconderCortinaCarregamento() {
        if (cortinaReveladaNestaPagina || activityDestroyed) return;
        cortinaReveladaNestaPagina = true;

        if (telaLoadingInicial != null && telaLoadingInicial.getVisibility() == View.VISIBLE) {
            telaLoadingInicial.animate().alpha(0f).setDuration(500).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (telaLoadingInicial != null) telaLoadingInicial.setVisibility(View.GONE);
                }
            }).start();
        }

        if (cortinaCongelada != null && cortinaCongelada.getVisibility() == View.VISIBLE) {
            cortinaCongelada.animate().alpha(0f).setDuration(500).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (cortinaCongelada != null) {
                        cortinaCongelada.setVisibility(View.GONE);
                        cortinaCongelada.setImageBitmap(null);
                    }
                }
            }).start();
        }
    }

    // ------------------------------------------------------------------
    // PLAYLIST E RECARGA
    // ------------------------------------------------------------------
    private void carregarLinkAtual(boolean congelarAnterior) {
        if (activityDestroyed || webView == null || playlistLinks.isEmpty()) return;

        cancelarTodasAsTarefas();

        if (congelarAnterior) {
            congelarTelaAtualAntesDeTrocar();
        }

        navigationId++;
        final long idAtual = navigationId;

        pageLoaded = false;
        retryCount = 0;
        cssAttempts = 0;
        fitClicado = false;
        cortinaReveladaNestaPagina = false;

        // Reaplica a escala inicial exatamente como no código original antes de carregar
        int larguraTela = getResources().getDisplayMetrics().widthPixels;
        int escala = (int) Math.round(larguraTela * 100.0 / LARGURA_VIRTUAL);
        webView.setInitialScale(escala);

        webView.loadUrl(playlistLinks.get(currentIndex));

        playlistRunnable = new Runnable() {
            @Override
            public void run() {
                if (activityDestroyed || idAtual != navigationId || emTelaDeLogin) return;

                if (!playlistLinks.isEmpty()) {
                    currentIndex = (currentIndex + 1) % playlistLinks.size();
                    carregarLinkAtual(true); // Congela a tela atual antes de carregar o próximo!
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

    // ------------------------------------------------------------------
    // AJUSTE VISUAL ORIGINAL (100% FIEL AO SEU PRIMEIRO ARQUIVO)
    // ------------------------------------------------------------------
    private void injetarViewportVirtual() {
        if (webView == null || activityDestroyed || emTelaDeLogin) return;

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
        if (activityDestroyed || webView == null || emTelaDeLogin) return;

        if (cssRunnable != null) {
            handler.removeCallbacks(cssRunnable);
        }

        cssRunnable = new Runnable() {
            @Override
            public void run() {
                cssRunnable = null;
                if (activityDestroyed || webView == null
                        || idDaPagina != navigationId || !pageLoaded || emTelaDeLogin) return;

                aplicarEstiloEClicarEmAjustar(idDaPagina);

                cssAttempts++;
                if (cssAttempts < MAX_CSS_ATTEMPTS) {
                    agendarAjusteVisual(idDaPagina);
                } else {
                    // Se chegou na última tentativa, garante que a cortina saia da frente
                    esconderCortinaCarregamento();
                }
            }
        };

        handler.postDelayed(cssRunnable, CSS_DELAY_MS);
    }

    /**
     * Exatamente o mesmo CSS e clique no "Ajustar à página" do seu código original!
     * Quando o botão é clicado (ou na 2ª passagem do CSS, ~4 segundos após o load),
     * a cortina congelada sai da frente com fade suave.
     */
    private void aplicarEstiloEClicarEmAjustar(final long idDaPagina) {
        if (webView == null || activityDestroyed || emTelaDeLogin) return;

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
                if (activityDestroyed || idDaPagina != navigationId) return;

                if ("true".equals(resultado)) {
                    fitClicado = true;
                    // Espera 800ms para o clique em "Ajustar à página" terminar de encaixar o gráfico e revela!
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!activityDestroyed && idDaPagina == navigationId) {
                                esconderCortinaCarregamento();
                            }
                        }
                    }, 800L);
                } else if (cssAttempts >= 2) {
                    // Em links reportEmbed que já vêm ajustados sem o botão de rodapé, revela na 2ª passagem (~4s)
                    esconderCortinaCarregamento();
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // AUTO-LOGIN MICROSOFT (PARA LINKS PRIVADOS)
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
                esconderCortinaCarregamento();
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

    // ------------------------------------------------------------------
    // CANCELAMENTO E CONTROLE REMOTO
    // ------------------------------------------------------------------
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
        // Tratado no processarTeclaControle (3x Voltar)
    }
}