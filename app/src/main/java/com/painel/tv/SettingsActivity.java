package com.painel.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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

    private EditText edLinks;
    private EditText edTempo;
    private TextView txtStatusPermissao;
    private Button btnToggleLauncher;
    private ServidorLocal servidor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(60, 40, 60, 40);
        raiz.setBackgroundColor(Color.parseColor("#141923"));

        raiz.addView(criarTexto("Configuração do Painel TV", 28, Color.WHITE));

        // Instrução para configurar pelo celular
        TextView info = criarTexto(
                "Para configurar pelo celular (Wi-Fi): acesse http://" + obterIp() + ":" + PORTA,
                16, Color.parseColor("#7FD1FF"));
        raiz.addView(info);

        // Status da Permissão de Inicialização
        txtStatusPermissao = criarTexto("", 15, Color.YELLOW);
        raiz.addView(txtStatusPermissao);

        // ---- Campo dos links ----
        raiz.addView(criarTexto("Links do Power BI (um por linha ou vírgula):", 18, Color.WHITE));

        edLinks = new EditText(this);
        edLinks.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_VARIATION_URI);
        edLinks.setMinLines(3);
        edLinks.setGravity(Gravity.TOP);
        edLinks.setTextColor(Color.WHITE);
        edLinks.setBackgroundColor(Color.parseColor("#1E2636"));
        edLinks.setPadding(16, 16, 16, 16);
        edLinks.setTextSize(16);
        edLinks.setText(prefs.getString("links", ""));
        raiz.addView(edLinks);

        // ---- Campo do tempo ----
        raiz.addView(criarTexto("Tempo de cada painel (segundos):", 18, Color.WHITE));

        edTempo = new EditText(this);
        edTempo.setInputType(InputType.TYPE_CLASS_NUMBER);
        edTempo.setTextColor(Color.WHITE);
        edTempo.setBackgroundColor(Color.parseColor("#1E2636"));
        edTempo.setPadding(16, 16, 16, 16);
        edTempo.setTextSize(16);
        edTempo.setText(String.valueOf(prefs.getInt("tempo", TEMPO_PADRAO)));
        raiz.addView(edTempo);

        // ---- Botão Salvar ----
        Button btnSalvar = criarBotao("1. Salvar e Iniciar Painel", "#2E7D32");
        btnSalvar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salvarEIniciar();
            }
        });
        raiz.addView(btnSalvar);

        // ---- Seção de Inicialização e Modo Launcher ----
        raiz.addView(criarTexto("Opções da TV, Inicialização e Launcher:", 20, Color.parseColor("#7FD1FF")));

        // Botão Permissão de Sobreposição (Boot)
        Button btnPermissao = criarBotao("2. Permitir Iniciar com a TV (Sobreposição)", "#1565C0");
        btnPermissao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pedirPermissaoSobreposicao();
            }
        });
        raiz.addView(btnPermissao);

        // Botão Ativar/Desativar Modo Launcher
        btnToggleLauncher = criarBotao("", "#6A1B9A");
        atualizarTextoBotaoLauncher();
        btnToggleLauncher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alternarModoLauncher();
            }
        });
        raiz.addView(btnToggleLauncher);

        // ---- Seção de Saída para a TV ----
        // Botão para abrir as Configurações do Android da TV (Wi-Fi, Rede, Som, Apps)
        Button btnConfigTv = criarBotao("Abrir Configurações da TV (Wi-Fi / Sistema)", "#455A64");
        btnConfigTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirConfiguracoesDaTv();
            }
        });
        raiz.addView(btnConfigTv);

        // Botão para sair do App e abrir o Menu Original da TV
        Button btnSairTv = criarBotao("Sair para o Menu Original da TV", "#C62828");
        btnSairTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sairParaMenuOriginalDaTv();
            }
        });
        raiz.addView(btnSairTv);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(raiz);
        setContentView(scroll);

        btnSalvar.requestFocus();

        servidor = new ServidorLocal();
        servidor.start();
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
                txtStatusPermissao.setText("⚠ Permissão para iniciar no Boot: PENDENTE (Clique no botão 2 ou ative o Modo Launcher)");
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
                // Tenta abrir a tela geral de aplicativos caso a tela direta de Overlay não exista na TV
                Intent i2 = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i2);
                Toast.makeText(this, "Procure por 'Permissões' ou 'Sobrepor a outros apps'. Ou use o Modo Launcher!", Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Toast.makeText(this, "Sua TV bloqueia essa tela. Use o botão '3. Ativar Modo Launcher' para iniciar com a TV!", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Verifica se o componente HomeLauncherAlias está ativado. */
    private boolean isModoLauncherAtivo() {
        ComponentName cn = new ComponentName(this, getPackageName() + ".HomeLauncherAlias");
        int state = getPackageManager().getComponentEnabledSetting(cn);
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private void atualizarTextoBotaoLauncher() {
        if (isModoLauncherAtivo()) {
            btnToggleLauncher.setText("3. Modo Launcher: ATIVADO (Clique para Desativar e liberar o botão Home)");
        } else {
            btnToggleLauncher.setText("3. Modo Launcher: DESATIVADO (Clique para Ativar e iniciar junto com a TV)");
        }
    }

    /**
     * Ativa ou desativa a capacidade deste app ser o Launcher (Tela Inicial) da TV.
     * Isso garante que você NUNCA fique preso no app: basta desativar aqui e o botão
     * Home volta imediatamente para a TV normal.
     */
    private void alternarModoLauncher() {
        ComponentName cn = new ComponentName(this, getPackageName() + ".HomeLauncherAlias");
        PackageManager pm = getPackageManager();

        if (isModoLauncherAtivo()) {
            // Desativa o modo Launcher deste app
            pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            atualizarTextoBotaoLauncher();
            Toast.makeText(this, "Modo Launcher DESATIVADO. O botão Home voltou ao normal da TV.", Toast.LENGTH_LONG).show();
        } else {
            // Ativa o modo Launcher e abre o seletor para o usuário escolher "Painel TV" como Sempre
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
                Toast.makeText(this, "Modo Launcher ATIVADO! Aperte o botão HOME (Casinha) do controle e escolha o Painel TV.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Abre as configurações nativas da TV (Wi-Fi, Rede, Display, Apps, etc). */
    private void abrirConfiguracoesDaTv() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir as configurações da TV.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Procura o Launcher original da TV (Google TV, Android TV Home, etc.) e abre ele
     * diretamente, permitindo sair do Painel sem precisar desinstalar nada.
     */
    private void sairParaMenuOriginalDaTv() {
        PackageManager pm = getPackageManager();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);

        List<ResolveInfo> launchers = pm.queryIntentActivities(homeIntent, 0);
        for (ResolveInfo info : launchers) {
            String pkg = info.activityInfo.packageName;
            // Procura um launcher que NÃO seja o nosso próprio app nem a tela de configurações
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

        // Se não achou pela categoria HOME, tenta pela categoria LEANBACK_LAUNCHER
        Toast.makeText(this, "Para voltar ao menu padrão da TV, desative a opção '3. Modo Launcher' acima e aperte Home.", Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------
    // AUXILIARES DE INTERFACE
    // ------------------------------------------------------------------
    private TextView criarTexto(String texto, int tamanhoSp, int cor) {
        TextView tv = new TextView(this);
        tv.setText(texto);
        tv.setTextSize(tamanhoSp);
        tv.setTextColor(cor);
        tv.setPadding(0, 16, 0, 6);
        return tv;
    }

    private Button criarBotao(String texto, String corHex) {
        Button b = new Button(this);
        b.setText(texto);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(corHex));
        b.setPadding(24, 18, 24, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 12, 0, 4);
        b.setLayoutParams(lp);
        return b;
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