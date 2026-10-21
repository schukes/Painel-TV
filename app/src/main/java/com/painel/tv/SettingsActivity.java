package com.painel.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.List;

public class SettingsActivity extends Activity {

    private static final String PREFS_NAME = "PainelPrefs";
    private static final int PORTA = 8080;
    private static final int TEMPO_PADRAO = 30;
    private static final int TEMPO_MIN = 10;
    private static final int TEMPO_MAX = 3600;

    private ScrollView scrollView;
    private EditText edLinks;
    private EditText edTempo;
    private TextView txtStatusPermissao;
    private Button btnSalvar;
    private Button btnPermissao;
    private Button btnToggleLauncher;
    private Button btnConfigTv;
    private Button btnSairTv;

    private ServidorLocal servidor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(80, 40, 80, 60);
        raiz.setBackgroundColor(Color.parseColor("#141923"));
        raiz.setClipToPadding(false);
        raiz.setClipChildren(false);

        raiz.addView(criarTexto("Configuração do Painel TV", 26, Color.WHITE));

        // Instrução para configurar pelo celular
        TextView info = criarTexto(
                "📱 Pelo celular (mesmo Wi-Fi): abra http://" + obterIp() + ":" + PORTA,
                16, Color.parseColor("#7FD1FF"));
        raiz.addView(info);

        // Status da Permissão de Inicialização
        txtStatusPermissao = criarTexto("", 15, Color.YELLOW);
        raiz.addView(txtStatusPermissao);

        // ---- 1. Campo dos links ----
        raiz.addView(criarTexto("Links do Power BI (um por linha ou separados por vírgula):", 16, Color.LTGRAY));

        edLinks = criarCampoTexto(
                prefs.getString("links", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI,
                3
        );
        raiz.addView(edLinks);

        // ---- 2. Campo do tempo ----
        raiz.addView(criarTexto("Tempo de cada painel (segundos):", 16, Color.LTGRAY));

        edTempo = criarCampoTexto(
                String.valueOf(prefs.getInt("tempo", TEMPO_PADRAO)),
                InputType.TYPE_CLASS_NUMBER,
                1
        );
        raiz.addView(edTempo);

        // ---- 3. Botão Salvar ----
        btnSalvar = criarBotaoTV("1. Salvar e Iniciar Painel", "#2E7D32", "#43A047");
        btnSalvar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salvarEIniciar();
            }
        });
        raiz.addView(btnSalvar);

        // ---- Seção de Inicialização e Modo Launcher ----
        raiz.addView(criarTexto("Opções do Sistema e Launcher da TV:", 18, Color.parseColor("#7FD1FF")));

        // ---- 4. Botão Permissão de Sobreposição ----
        btnPermissao = criarBotaoTV("2. Permitir Iniciar com a TV (Sobreposição)", "#1565C0", "#1E88E5");
        btnPermissao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pedirPermissaoSobreposicao();
            }
        });
        raiz.addView(btnPermissao);

        // ---- 5. Botão Ativar/Desativar Modo Launcher ----
        btnToggleLauncher = criarBotaoTV("", "#6A1B9A", "#8E24AA");
        atualizarTextoBotaoLauncher();
        btnToggleLauncher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alternarModoLauncher();
            }
        });
        raiz.addView(btnToggleLauncher);

        // ---- 6. Botão Configurações da TV ----
        btnConfigTv = criarBotaoTV("4. Abrir Configurações da TV (Wi-Fi / Rede / Sistema)", "#37474F", "#546E7A");
        btnConfigTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirConfiguracoesDaTv();
            }
        });
        raiz.addView(btnConfigTv);

        // ---- 7. Botão Sair para o Menu Original ----
        btnSairTv = criarBotaoTV("5. Sair para o Menu Original da TV", "#C62828", "#E53935");
        btnSairTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sairParaMenuOriginalDaTv();
            }
        });
        raiz.addView(btnSairTv);

        // Configura o ScrollView para NÃO roubar o foco dos botões na TV
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setFocusable(false);
        scrollView.setFocusableInTouchMode(false);
        scrollView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scrollView.addView(raiz);
        setContentView(scrollView);

        // Amarra a ordem exata das setas CIMA / BAIXO para nunca travar
        configurarOrdemDeNavegacao();

        // Começa com o botão Salvar já selecionado e destacado na tela!
        btnSalvar.post(new Runnable() {
            @Override
            public void run() {
                btnSalvar.requestFocus();
            }
        });

        servidor = new ServidorLocal();
        servidor.start();
    }

    /**
     * Define exatamente para onde o foco vai ao apertar Seta para Cima ou Seta para Baixo.
     */
    private void configurarOrdemDeNavegacao() {
        View[] ordem = new View[]{
                edLinks,
                edTempo,
                btnSalvar,
                btnPermissao,
                btnToggleLauncher,
                btnConfigTv,
                btnSairTv
        };

        for (int i = 0; i < ordem.length; i++) {
            ordem[i].setId(1000 + i);
        }

        for (int i = 0; i < ordem.length; i++) {
            View atual = ordem[i];
            View anterior = ordem[(i - 1 + ordem.length) % ordem.length]; // Loop para o fim se subir no topo
            View proximo = ordem[(i + 1) % ordem.length];                  // Loop para o topo se descer no fim

            atual.setNextFocusUpId(anterior.getId());
            atual.setNextFocusDownId(proximo.getId());
        }
    }

    /**
     * Cria um botão estilizado para TV que muda de cor, ganha borda branca grossa,
     * aumenta de tamanho (zoom) e mostra uma seta "▶ " quando está selecionado pelo controle.
     */
    private Button criarBotaoTV(String textoInicial, final String corNormalHex, final String corFocoHex) {
        final Button b = new Button(this);
        b.setTag(textoInicial); // Guarda o texto limpo
        b.setText("   " + textoInicial);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(36, 22, 36, 22);

        b.setFocusable(true);
        b.setFocusableInTouchMode(false); // Essencial para TV: clicar com 1 único toque no OK!

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 14, 0, 6);
        b.setLayoutParams(lp);

        // Aplica visual inicial (sem foco)
        aplicarEstiloCaixa(b, corNormalHex, false);

        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                String textoBase = (String) b.getTag();
                if (hasFocus) {
                    aplicarEstiloCaixa(b, corFocoHex, true);
                    b.setText("▶  " + textoBase);
                    b.animate().scaleX(1.03f).scaleY(1.05f).setDuration(120).start();
                    rolarParaElemento(b);
                } else {
                    aplicarEstiloCaixa(b, corNormalHex, false);
                    b.setText("   " + textoBase);
                    b.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
            }
        });

        return b;
    }

    /**
     * Cria um EditText preparado para controle remoto de TV:
     * - Setas Cima/Baixo saem do campo normalmente (não prendem o foco lá dentro).
     * - Apertar OK abre o teclado virtual da TV.
     * - Ganha borda branca quando está selecionado.
     */
    private EditText criarCampoTexto(String valorInicial, int inputType, int minLines) {
        final EditText ed = new EditText(this);
        ed.setInputType(inputType);
        ed.setMinLines(minLines);
        ed.setMaxLines(minLines == 1 ? 1 : 5);
        ed.setGravity(minLines == 1 ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        ed.setTextColor(Color.WHITE);
        ed.setTextSize(16);
        ed.setPadding(24, 18, 24, 18);
        ed.setText(valorInicial);

        ed.setFocusable(true);
        ed.setFocusableInTouchMode(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 6, 0, 12);
        ed.setLayoutParams(lp);

        aplicarEstiloCaixa(ed, "#1E2636", false);

        ed.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    aplicarEstiloCaixa(ed, "#28354A", true);
                    rolarParaElemento(ed);
                } else {
                    aplicarEstiloCaixa(ed, "#1E2636", false);
                    esconderTeclado(ed);
                }
            }
        });

        // Corrige a "armadilha" do EditText na TV: Cima/Baixo mudam de botão em vez de travar no texto
        ed.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        View next = v.focusSearch(View.FOCUS_DOWN);
                        if (next != null) {
                            next.requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        View prev = v.focusSearch(View.FOCUS_UP);
                        if (prev != null) {
                            prev.requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                        mostrarTeclado(ed);
                        return true;
                    }
                }
                return false;
            }
        });

        return ed;
    }

    /**
     * Desenha o fundo arredondado com borda branca brilhante quando selecionado na TV.
     */
    private void aplicarEstiloCaixa(View view, String corFundoHex, boolean focado) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(corFundoHex));
        gd.setCornerRadius(14f);
        if (focado) {
            // Borda branca de 6px super visível na TV
            gd.setStroke(6, Color.WHITE);
        } else {
            // Borda discreta quando não selecionado
            gd.setStroke(2, Color.parseColor("#44FFFFFF"));
        }
        view.setBackground(gd);
    }

    /** Faz o ScrollView acompanhar o botão selecionado suavemente. */
    private void rolarParaElemento(final View v) {
        if (scrollView == null) return;
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                int scrollY = v.getTop() - 150;
                scrollView.smoothScrollTo(0, Math.max(0, scrollY));
            }
        });
    }

    private void mostrarTeclado(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void esconderTeclado(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarStatusPermissao();
        atualizarTextoBotaoLauncher();
    }

    @Override
    protected void onDestroy() {
        if (servidor != null) servidor.parar();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        String links = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("links", "");
        if (links != null && links.toLowerCase().contains("http")) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------
    // SALVAR E INICIAR
    // ------------------------------------------------------------------
    private void salvarEIniciar() {
        String links = edLinks.getText().toString().trim();

        int tempo;
        try {
            tempo = Integer.parseInt(edTempo.getText().toString().trim());
        } catch (NumberFormatException e) {
            tempo = TEMPO_PADRAO;
        }
        tempo = Math.max(TEMPO_MIN, Math.min(TEMPO_MAX, tempo));

        if (!links.toLowerCase().contains("http")) {
            Toast.makeText(this, "Informe ao menos um link http:// ou https://", Toast.LENGTH_LONG).show();
            edLinks.requestFocus();
            return;
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString("links", links)
                .putInt("tempo", tempo)
                .apply();

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // ------------------------------------------------------------------
    // CONTROLE DE PERMISSÃO E MODO LAUNCHER
    // ------------------------------------------------------------------
    private void atualizarStatusPermissao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                txtStatusPermissao.setText("✓ Permissão para iniciar no Boot: ATIVA");
                txtStatusPermissao.setTextColor(Color.parseColor("#81C784"));
            } else {
                txtStatusPermissao.setText("⚠ Permissão para iniciar no Boot: PENDENTE (Use o botão 2 ou ative o botão 3 - Modo Launcher)");
                txtStatusPermissao.setTextColor(Color.parseColor("#FFD54F"));
            }
        }
    }

    private void pedirPermissaoSobreposicao() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "A permissão já está ativa!", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                Intent i2 = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i2);
                Toast.makeText(this, "Procure por 'Permissões' -> 'Sobrepor a outros apps'.", Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Toast.makeText(this, "Sua TV bloqueia essa tela. Ative o botão '3. Modo Launcher' abaixo!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isModoLauncherAtivo() {
        ComponentName cn = new ComponentName(this, getPackageName() + ".HomeLauncherAlias");
        int state = getPackageManager().getComponentEnabledSetting(cn);
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private void atualizarTextoBotaoLauncher() {
        String novoTexto;
        if (isModoLauncherAtivo()) {
            novoTexto = "3. Modo Launcher: ATIVADO (Clique para Desativar e liberar o botão Home)";
        } else {
            novoTexto = "3. Modo Launcher: DESATIVADO (Clique para Ativar e iniciar junto com a TV)";
        }
        btnToggleLauncher.setTag(novoTexto);
        if (btnToggleLauncher.hasFocus()) {
            btnToggleLauncher.setText("▶  " + novoTexto);
        } else {
            btnToggleLauncher.setText("   " + novoTexto);
        }
    }

    private void alternarModoLauncher() {
        ComponentName cn = new ComponentName(this, getPackageName() + ".HomeLauncherAlias");
        PackageManager pm = getPackageManager();

        if (isModoLauncherAtivo()) {
            pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            atualizarTextoBotaoLauncher();
            Toast.makeText(this, "Modo Launcher DESATIVADO. O botão Home voltou ao normal da TV.", Toast.LENGTH_LONG).show();
        } else {
            pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            atualizarTextoBotaoLauncher();

            try {
                Intent seletorHome = new Intent(Intent.ACTION_MAIN);
                seletorHome.addCategory(Intent.CATEGORY_HOME);
                seletorHome.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(seletorHome);
                Toast.makeText(this, "Selecione 'Painel TV' como tela inicial!", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Modo Launcher ATIVADO! Aperte o botão HOME do controle e escolha o Painel TV.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void abrirConfiguracoesDaTv() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir as configurações da TV.", Toast.LENGTH_SHORT).show();
        }
    }

    private void sairParaMenuOriginalDaTv() {
        PackageManager pm = getPackageManager();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);

        List<ResolveInfo> launchers = pm.queryIntentActivities(homeIntent, 0);
        for (ResolveInfo info : launchers) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(getPackageName()) && !pkg.contains("settings")) {
                Intent launch = new Intent(Intent.ACTION_MAIN);
                launch.addCategory(Intent.CATEGORY_HOME);
                launch.setComponent(new ComponentName(pkg, info.activityInfo.name));
                launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(launch);
                    finish();
                    return;
                } catch (Exception ignored) { }
            }
        }

        Toast.makeText(this, "Desative a opção '3. Modo Launcher' acima e aperte o botão Home do controle.", Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------
    // AUXILIARES
    // ------------------------------------------------------------------
    private TextView criarTexto(String texto, int tamanhoSp, int cor) {
        TextView tv = new TextView(this);
        tv.setText(texto);
        tv.setTextSize(tamanhoSp);
        tv.setTextColor(cor);
        tv.setPadding(0, 14, 0, 6);
        tv.setFocusable(false);
        return tv;
    }

    private String obterIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> enderecos = ni.getInetAddresses();
                while (enderecos.hasMoreElements()) {
                    InetAddress ip = enderecos.nextElement();
                    if (!ip.isLoopbackAddress() && ip instanceof Inet4Address) {
                        return ip.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return "IP-DA-TV";
    }

    private static String escaparHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------
    // MINI SERVIDOR WEB (configurar pelo celular)
    // ------------------------------------------------------------------
    private class ServidorLocal extends Thread {
        private ServerSocket serverSocket;
        private volatile boolean rodando = true;

        void parar() {
            rodando = false;
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (IOException ignored) { }
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(PORTA);
                while (rodando) {
                    Socket cliente = serverSocket.accept();
                    try {
                        atender(cliente);
                    } catch (Exception ignored) { }
                    try { cliente.close(); } catch (IOException ignored) { }
                }
            } catch (IOException ignored) { }
        }

        private void atender(Socket cliente) throws IOException {
            cliente.setSoTimeout(5000);
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(cliente.getInputStream(), "UTF-8"));

            String requisicao = entrada.readLine();
            if (requisicao == null) return;

            int tamanhoCorpo = 0;
            String linha;
            while ((linha = entrada.readLine()) != null && !linha.isEmpty()) {
                if (linha.toLowerCase().startsWith("content-length:")) {
                    tamanhoCorpo = Integer.parseInt(linha.substring(15).trim());
                }
            }

            String html;

            if (requisicao.startsWith("POST")) {
                char[] buffer = new char[tamanhoCorpo];
                int lido = 0;
                while (lido < tamanhoCorpo) {
                    int n = entrada.read(buffer, lido, tamanhoCorpo - lido);
                    if (n < 0) break;
                    lido += n;
                }
                String corpo = new String(buffer, 0, lido);

                String links = "";
                String tempo = String.valueOf(TEMPO_PADRAO);
                for (String par : corpo.split("&")) {
                    String[] kv = par.split("=", 2);
                    if (kv.length < 2) continue;
                    String valor = URLDecoder.decode(kv[1], "UTF-8");
                    if (kv[0].equals("links")) links = valor;
                    if (kv[0].equals("tempo")) tempo = valor;
                }

                final String linksFinal = links.trim();
                final String tempoFinal = tempo.trim();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        edLinks.setText(linksFinal);
                        edTempo.setText(tempoFinal);
                        salvarEIniciar();
                    }
                });

                html = montarPagina("Enviado! A TV vai iniciar o painel.", linksFinal, tempoFinal);
            } else {
                SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                html = montarPagina("",
                        p.getString("links", ""),
                        String.valueOf(p.getInt("tempo", TEMPO_PADRAO)));
            }

            byte[] bytes = html.getBytes("UTF-8");
            OutputStream saida = cliente.getOutputStream();
            String cabecalho = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/html; charset=utf-8\r\n"
                    + "Content-Length: " + bytes.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            saida.write(cabecalho.getBytes("UTF-8"));
            saida.write(bytes);
            saida.flush();
        }

        private String montarPagina(String mensagem, String links, String tempo) {
            return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                    + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<title>Painel TV</title></head>"
                    + "<body style='font-family:sans-serif;padding:16px'>"
                    + "<h2>Painel TV - Power BI</h2>"
                    + "<p style='color:green'>" + mensagem + "</p>"
                    + "<form method='post' action='/salvar'>"
                    + "<p>Links (um por linha):</p>"
                    + "<textarea name='links' rows='8' style='width:100%'>" + escaparHtml(links) + "</textarea>"
                    + "<p>Tempo por painel (segundos):</p>"
                    + "<input name='tempo' type='number' value='" + escaparHtml(tempo) + "'>"
                    + "<p><button type='submit' style='padding:12px 24px;font-size:18px'>"
                    + "Salvar e iniciar</button></p>"
                    + "</form></body></html>";
        }
    }
}