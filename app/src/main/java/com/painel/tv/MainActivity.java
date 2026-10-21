package com.painel.tv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Painel TV - Power BI (modo quiosque)
 *
 * Mostra uma playlist de links públicos do Power BI em tela cheia,
 * alternando entre eles a cada X segundos.
 *
 * CORREÇÃO PRINCIPAL desta versão:
 * O WebView agora renderiza a página num "viewport virtual" de 1920 px de largura
 * (igual a um monitor Full HD) e depois escala tudo para caber na TV.
 * Assim o Power BI se comporta como no navegador do computador e o relatório
 * aparece inteiro, sem cortes e sem cair no layout "mobile".
 */
public class MainActivity extends Activity {

    // ------------------------------------------------------------------
    // CONSTANTES DE CONFIGURAÇÃO
    // ------------------------------------------------------------------
    private static final String PREFS_NAME = "PainelPrefs";
    private static final String DEFAULT_URL = "https://app.powerbi.com/";

    // Tempo de exibição de cada painel (em segundos)
    private static final int DEFAULT_INTERVAL_SECONDS = 30;
    private static final int MIN_INTERVAL_SECONDS = 10;
    private static final int MAX_INTERVAL_SECONDS = 3600;

    // Tentativas de recarregar a página quando ocorre erro de rede
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 10000L;

    // Quantas vezes o app reaplica o ajuste visual (CSS) depois que a página carrega.
    // O Power BI monta a tela aos poucos, então repetir ajuda a pegar elementos tardios.
    private static final int MAX_CSS_ATTEMPTS = 6;
    private static final long CSS_DELAY_MS = 2000L;

    // Largura do "monitor virtual" em que o Power BI vai renderizar.
    // Se na TV sobrar margem ou cortar um pouco, teste valores como 1600 ou 2000:
    //   - número MENOR  = conteúdo maior (aproxima)
    //   - número MAIOR  = conteúdo menor (afasta)
    private static final int LARGURA_VIRTUAL = 1920;

    // ------------------------------------------------------------------
    // ESTADO DO APP
    // ------------------------------------------------------------------
    private WebView webView;
    private final List<String> playlistLinks = new ArrayList<>();

    private int currentIndex = 0;     // posição do link atual na playlist
    private int retryCount = 0;       // tentativas de recarga após erro
    private int cssAttempts = 0;      // quantas vezes o ajuste visual já rodou

    private long intervaloMs = DEFAULT_INTERVAL_SECONDS * 1000L;

    // Identificador da navegação atual. Cada vez que um link novo é carregado, ele aumenta.
    // Serve para ignorar tarefas agendadas que pertencem a uma página antiga.
    private long navigationId = 0L;

    private boolean activityDestroyed = false;
    private boolean pageLoaded = false;

    // Garante que o botão "Ajustar à página" seja clicado UMA única vez por página.
    // (Esse botão alterna entre modos; clicar várias vezes desfaz o ajuste.)
    private boolean fitClicado = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Runnable playlistRunnable; // troca para o próximo link
    private Runnable retryRunnable;    // tenta recarregar após erro
    private Runnable cssRunnable;      // aplica o ajuste visual

    // ------------------------------------------------------------------
    // CICLO DE VIDA
    // ------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        configurarTela();
        carregarConfiguracoes();
        criarWebView();

        // Se não houver nenhum link salvo, usa o link padrão
        if (playlistLinks.isEmpty()) {
            playlistLinks.add(DEFAULT_URL);
        }

        carregarLinkAtual();
    }

    /** Tela cheia, sem barra de título e sempre acesa. */
    private void configurarTela() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        // Impede que a TV apague a tela ou entre em protetor de tela
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ocultarBarrasSistema();
    }

    /** Lê os links e o tempo de exibição salvos nas SharedPreferences. */
    private void carregarConfiguracoes() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String rawLinks = prefs.getString("links", "");
        int tempoSegundos = prefs.getInt("tempo", DEFAULT_INTERVAL_SECONDS);

        // Mantém o tempo dentro dos limites permitidos
        tempoSegundos = Math.max(MIN_INTERVAL_SECONDS, Math.min(MAX_INTERVAL_SECONDS, tempoSegundos));
        intervaloMs = tempoSegundos * 1000L;

        playlistLinks.clear();
        if (rawLinks != null && !rawLinks.trim().isEmpty()) {
            // Os links podem estar separados por vírgula ou por quebra de linha
            String[] links = rawLinks.split("[,\\r\\n]+");
            for (String link : links) {
                String limpo = link.trim();
                if (limpo.isEmpty()) continue;

                // Só aceita endereços http/https
                String urlLower = limpo.toLowerCase(Locale.ROOT);
                if (urlLower.startsWith("https://") || urlLower.startsWith("http://")) {
                    playlistLinks.add(limpo);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // CRIAÇÃO E CONFIGURAÇÃO DO WEBVIEW
    // ------------------------------------------------------------------
    private void criarWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#141923"));
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        // --- ESCALA: o coração da correção ---
        // Calculamos quanto precisamos "encolher" uma página de 1920 px
        // para ela ocupar exatamente a largura real da TV.
        // Exemplo: TV com 1920 px de largura -> escala 100%.
        //          TV com 1280 px de largura -> escala 67%.
        int larguraTela = getResources().getDisplayMetrics().widthPixels;
        int escala = (int) Math.round(larguraTela * 100.0 / LARGURA_VIRTUAL);
        webView.setInitialScale(escala);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // useWideViewPort = true  -> o WebView respeita a largura definida pela página (meta viewport)
        // loadWithOverviewMode = false -> NÃO deixa o WebView "dar zoom out" por conta própria,
        //                                 pois isso brigava com o setInitialScale.
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);

        // Zoom desativado (é uma TV, ninguém vai dar pinça)
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Finge ser um Chrome de computador para o Power BI entregar o layout de desktop
        settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageLoaded = false;
                fitClicado = false;          // página nova: o clique em "Ajustar" vale de novo
                cancelarTarefasDePagina();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (activityDestroyed || view != webView) return;

                pageLoaded = true;
                retryCount = 0;
                cssAttempts = 0;

                // 1) Força o viewport virtual de 1920 px
                injetarViewportVirtual();

                // 2) Agenda o ajuste visual (CSS + clique em "Ajustar à página")
                agendarAjusteVisual(navigationId);
            }

            // Erro de carregamento (Android 6.0 ou mais novo)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.isForMainFrame()) {
                    tratarFalhaDeCarregamento();
                }
            }

            // Erro de carregamento (Android antigo)
            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    tratarFalhaDeCarregamento();
                }
            }
        });

        setContentView(webView);
    }

    // ------------------------------------------------------------------
    // PLAYLIST: CARREGAR E ALTERNAR LINKS
    // ------------------------------------------------------------------

    /** Carrega o link atual e agenda a troca para o próximo. */
    private void carregarLinkAtual() {
        if (activityDestroyed || webView == null || playlistLinks.isEmpty()) return;

        // Cancela qualquer tarefa pendente do link anterior
        cancelarTodasAsTarefas();

        navigationId++;
        final long idAtual = navigationId;

        pageLoaded = false;
        retryCount = 0;
        cssAttempts = 0;
        fitClicado = false;

        // Obs.: o parâmetro "chromeless=true" foi removido, pois não tem efeito em links "view?r="
        webView.loadUrl(playlistLinks.get(currentIndex));

        // Depois do tempo configurado, passa para o próximo link da playlist
        playlistRunnable = new Runnable() {
            @Override
            public void run() {
                // Se a tela foi fechada ou outro link já foi carregado, ignora
                if (activityDestroyed || idAtual != navigationId) return;

                if (!playlistLinks.isEmpty()) {
                    currentIndex = (currentIndex + 1) % playlistLinks.size();
                    carregarLinkAtual();
                }
            }
        };

        handler.postDelayed(playlistRunnable, intervaloMs);
    }

    /** Se a página falhar (ex.: sem internet), tenta recarregar algumas vezes. */
    private void tratarFalhaDeCarregamento() {
        if (activityDestroyed || pageLoaded || webView == null
                || retryRunnable != null || retryCount >= MAX_RETRIES) return;

        final long idDaFalha = navigationId;
        retryCount++;

        retryRunnable = new Runnable() {
            @Override
            public void run() {
                retryRunnable = null;
                // Só recarrega se ainda estivermos na mesma navegação e a página não carregou
                if (activityDestroyed || webView == null
                        || idDaFalha != navigationId || pageLoaded) return;

                webView.loadUrl(playlistLinks.get(currentIndex));
            }
        };

        handler.postDelayed(retryRunnable, RETRY_DELAY_MS);
    }

    // ------------------------------------------------------------------
    // AJUSTE VISUAL (viewport virtual, CSS e botão "Ajustar à página")
    // ------------------------------------------------------------------

    /**
     * Diz à página que ela está num monitor de LARGURA_VIRTUAL pixels.
     * Combinado com o setInitialScale, isso faz o Power BI desenhar o relatório
     * como se estivesse num PC Full HD e o WebView reduz tudo para caber na TV.
     */
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
                // Avisa o Power BI que o tamanho mudou, para ele recalcular o layout
                "  window.dispatchEvent(new Event('resize'));" +
                "})();";

        webView.evaluateJavascript(js, null);
    }

    /** Agenda o ajuste visual e repete algumas vezes (o Power BI demora para montar a tela). */
    private void agendarAjusteVisual(final long idDaPagina) {
        if (activityDestroyed || webView == null) return;

        if (cssRunnable != null) {
            handler.removeCallbacks(cssRunnable);
        }

        cssRunnable = new Runnable() {
            @Override
            public void run() {
                cssRunnable = null;
                // Ignora se a página mudou ou ainda não terminou de carregar
                if (activityDestroyed || webView == null
                        || idDaPagina != navigationId || !pageLoaded) return;

                aplicarEstiloEClicarEmAjustar();

                // Repete até o limite de tentativas
                cssAttempts++;
                if (cssAttempts < MAX_CSS_ATTEMPTS) {
                    agendarAjusteVisual(idDaPagina);
                }
            }
        };

        handler.postDelayed(cssRunnable, CSS_DELAY_MS);
    }

    /**
     * Injeta CSS para limpar a tela e clica (uma única vez) no botão "Ajustar à página".
     *
     * O que isso faz:
     *  - remove margens e barras de rolagem;
     *  - esconde o rodapé "Microsoft Power BI" e a barra de zoom (best effort:
     *    se o Power BI mudar os nomes de classe, ajuste os seletores abaixo);
     *  - procura o botão cujo título/aria-label seja "Ajustar à página" ou
     *    "Fit to page" e clica nele só uma vez.
     */
    private void aplicarEstiloEClicarEmAjustar() {
        if (webView == null || activityDestroyed) return;

        // Se já clicamos nesta página, o JS não tenta clicar de novo
        final String deveClicar = fitClicado ? "false" : "true";

        String js =
                "(function() {" +

                // ---------- 1) CSS de limpeza ----------
                "  var css = '" +
                "    html, body { margin:0 !important; padding:0 !important;" +
                "      background:#141923 !important; overflow:hidden !important; }" +
                "    ::-webkit-scrollbar { display:none !important; width:0 !important; height:0 !important; }" +
                // Possíveis nomes de classe do rodapé/barra de status do Power BI
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

                // ---------- 2) Clique único em 'Ajustar à página' ----------
                "  var achou = false;" +
                "  if (" + deveClicar + ") {" +
                "    var els = document.querySelectorAll('button, [role=\"button\"]');" +
                "    for (var i = 0; i < els.length; i++) {" +
                "      var t = ((els[i].getAttribute('title') || '') + ' ' +" +
                "               (els[i].getAttribute('aria-label') || '')).toLowerCase();" +
                // Texto exato, para não clicar em qualquer elemento que tenha 'fit' no nome
                "      if (t.indexOf('ajustar à página') >= 0 || t.indexOf('fit to page') >= 0) {" +
                "        els[i].click();" +
                "        achou = true;" +
                "        break;" +
                "      }" +
                "    }" +
                "  }" +

                // ---------- 3) Pede ao Power BI para recalcular o layout ----------
                "  window.dispatchEvent(new Event('resize'));" +
                "  return achou;" +
                "})();";

        // O retorno do JS (true/false) diz se o botão foi encontrado e clicado
        webView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String resultado) {
                if ("true".equals(resultado)) {
                    fitClicado = true; // não clica de novo nesta página
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // CANCELAMENTO DE TAREFAS AGENDADAS
    // ------------------------------------------------------------------

    /** Cancela retentativa e ajuste visual (tarefas ligadas à página atual). */
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

    /** Cancela tudo, inclusive a troca automática de link. */
    private void cancelarTodasAsTarefas() {
        if (playlistRunnable != null) {
            handler.removeCallbacks(playlistRunnable);
            playlistRunnable = null;
        }
        cancelarTarefasDePagina();
    }

    // ------------------------------------------------------------------
    // MODO QUIOSQUE (barras do sistema e botão Voltar)
    // ------------------------------------------------------------------

    /** Esconde a barra de status e a de navegação (modo imersivo). */
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

    // Sempre que a janela recupera o foco, esconde as barras de novo
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !activityDestroyed) ocultarBarrasSistema();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!activityDestroyed) ocultarBarrasSistema();
    }

    // Libera tudo ao fechar o app, evitando vazamento de memória
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

    // Bloqueia o botão Voltar do controle remoto
    @Override
    public void onBackPressed() {
        // Não faz nada de propósito (modo quiosque)
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        return super.onKeyDown(keyCode, event);
    }
}