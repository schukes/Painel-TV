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

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 10000L;

    private static final int MAX_CSS_ATTEMPTS = 8;
    private static final long CSS_DELAY_MS = 2500L;

    private static final int LARGURA_VIRTUAL = 1920;

    private WebView webView;
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

    private void criarWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#141923"));
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);

        int larguraTela = getResources().getDisplayMetrics().widthPixels;
        int escala = (int) Math.round(larguraTela * 100.0 / LARGURA_VIRTUAL);
        webView.setInitialScale(escala);

        // --- HABILITA COOKIES PERSISTENTES E DE TERCEIROS (ESSENCIAL PARA POWER BI PRIVADO) ---
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
                cancelarTarefasDePagina();
                verificarModoLogin(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (activityDestroyed || view != webView) return;

                // Salva os cookies de login na memória flash da TV imediatamente
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush();
                }

                pageLoaded = true;
                retryCount = 0;
                cssAttempts = 0;

                verificarModoLogin(url);

                if (emTelaDeLogin) {
                    // Estamos na página de login da Microsoft: pausa a troca de links e faz o Auto-Login
                    if (playlistRunnable != null) {
                        handler.removeCallbacks(playlistRunnable);
                    }
                    agendarAutoLogin(navigationId);
                } else {
                    // Estamos no relatório do Power BI: aplica viewport, esconde barras e agenda próxima troca
                    injetarViewportVirtual();
                    agendarAjusteVisual(navigationId);
                    agendarProximaTroca(navigationId);

                    // Também verifica se o próprio Power BI abriu a telinha inicial de pedir e-mail
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

    /**
     * Detecta se a URL atual é uma página de autenticação da Microsoft / Azure AD / ADFS.
     */
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
                // Na tela de login, libera o foco do WebView caso o usuário precise aprovar MFA (2 fatores)
                // e avisa no canto da tela que "3x Voltar" continua abrindo as configurações.
                overlayCapturador.setVisibility(View.GONE);
                webView.setFocusable(true);
                webView.setFocusableInTouchMode(true);
                webView.requestFocus();

                bannerStatus.setText("Autenticando no Power BI... (Aperte 3x VOLTAR para Configurações)");
                bannerStatus.setVisibility(View.VISIBLE);
            } else {
                // Saiu do login e entrou no relatório: volta a bloquear o foco do WebView!
                webView.setFocusable(false);
                webView.setFocusableInTouchMode(false);
                overlayCapturador.setVisibility(View.VISIBLE);
                overlayCapturador.requestFocus();

                bannerStatus.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Agenda tentativas periódicas de preencher o usuário, senha e botão "Continuar conectado".
     */
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

                // Se ainda estiver na tela de login, repete a checagem a cada 2.5 segundos
                // porque a Microsoft carrega a tela de E-mail -> Senha -> Continuar Conectado via AJAX
                if (emTelaDeLogin) {
                    agendarAutoLogin(idDaPagina);
                }
            }
        };

        handler.postDelayed(loginRunnable, 2000L);
    }

    /**
     * Injeta JavaScript que preenche automaticamente:
     * 1) Tela inicial do Power BI caso peça o e-mail antes de redirecionar;
     * 2) Campo de e-mail da Microsoft (input[name="loginfmt"]);
     * 3) Campo de senha da Microsoft (input[name="passwd"]);
     * 4) Pergunta "Continuar conectado?" (KMSI - clica em "Sim").
     */
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

                // 1) Verifica se está na pergunta 'Continuar conectado?' (KMSI) e clica em Sim
                "  var kmsi = document.getElementById('KmsiCheckboxField');" +
                "  var btnSubmit = document.getElementById('idSIButton9');" +
                "  if (visivel(kmsi) && visivel(btnSubmit)) {" +
                "    if (!kmsi.checked) kmsi.click();" +
                "    btnSubmit.click();" +
                "    return 'KMSI_CLICKED';" +
                "  }" +

                // 2) Verifica se o campo de SENHA da Microsoft está visível
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

                // 3) Verifica se o campo de E-MAIL da Microsoft ou do Power BI está visível
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

                // 4) Caso apareça a lista 'Escolha uma conta' (Pick an account) na Microsoft
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

    private void carregarLinkAtual() {
        if (activityDestroyed || webView == null || playlistLinks.isEmpty()) return;

        cancelarTodasAsTarefas();

        navigationId++;
        pageLoaded = false;
        retryCount = 0;
        cssAttempts = 0;
        fitClicado = false;

        webView.loadUrl(playlistLinks.get(currentIndex));
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
                    // Se houver apenas 1 link privado, tenta primeiro clicar no botão "Atualizar" interno do Power BI
                    // para atualizar os visuais sem piscar a tela inteira; ou recarrega a URL.
                    atualizarRelatorioUnicoOuRecarregar();
                } else if (!playlistLinks.isEmpty()) {
                    currentIndex = (currentIndex + 1) % playlistLinks.size();
                    carregarLinkAtual();
                }
            }
        };

        handler.postDelayed(playlistRunnable, intervaloMs);
    }

    /**
     * Para quando há 1 único link: tenta acionar o botão de "Atualizar visuais" do próprio Power BI
     * ou recarrega a página mantendo os cookies de login.
     */
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

        webView.evaluateJavascript(jsRefresh, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String clicouRefresh) {
                if ("true".equals(clicouRefresh)) {
                    // Conseguiu atualizar por dentro do Power BI sem piscar a tela! Agenda o próximo ciclo.
                    agendarProximaTroca(navigationId);
                } else {
                    // Não achou botão interno (ex: modo reportEmbed puro), então recarrega o link
                    carregarLinkAtual();
                }
            }
        });
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
        if (webView == null || activityDestroyed || emTelaDeLogin) return;

        final String deveClicar = fitClicado ? "false" : "true";

        String js =
                "(function() {" +
                "  var css = '" +
                "    html, body { margin:0 !important; padding:0 !important;" +
                "      background:#141923 !important; overflow:hidden !important; }" +
                "    ::-webkit-scrollbar { display:none !important; width:0 !important; height:0 !important; }" +
                // Esconde barras inferiores e também menus superiores/laterais de links privados do Power BI
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

        // Se estiver na tela de login da Microsoft, deixa as setas e o OK funcionarem dentro da página
        // caso a empresa exija clicar em um aprovação de 2 fatores (MFA) na primeira vez!
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