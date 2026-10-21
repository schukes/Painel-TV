package com.painel.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
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

/**
 * Tela de configurações do Painel TV.
 *
 * Aparece em duas situações:
 *  1) Na primeira abertura do app (ainda não há links salvos);
 *  2) Quando o usuário aperta OK/Enter/Menu no controle durante o painel.
 *
 * Duas formas de configurar:
 *  A) Direto na TV: o OK do controle em um campo abre o teclado na tela.
 *  B) Pelo celular: enquanto esta tela está aberta, a TV roda um mini servidor web.
 *     No celular (mesma rede Wi-Fi) abra  http://IP-DA-TV:8080 , cole os links e envie.
 *
 * Os dados são salvos em SharedPreferences ("PainelPrefs"), o mesmo arquivo lido
 * pela MainActivity:  "links" (texto) e "tempo" (segundos).
 */
public class SettingsActivity extends Activity {

    // ------------------------------------------------------------------
    // CONSTANTES
    // ------------------------------------------------------------------
    private static final String PREFS_NAME = "PainelPrefs"; // igual ao da MainActivity
    private static final int PORTA = 8080;                  // porta do servidor local
    private static final int TEMPO_PADRAO = 30;
    private static final int TEMPO_MIN = 10;
    private static final int TEMPO_MAX = 3600;

    // ------------------------------------------------------------------
    // ESTADO
    // ------------------------------------------------------------------
    private EditText edLinks;           // campo dos links
    private EditText edTempo;           // campo do tempo (segundos)
    private ServidorLocal servidor;     // mini servidor web para configurar pelo celular

    // ------------------------------------------------------------------
    // CICLO DE VIDA
    // ------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mantém a tela acesa enquanto configura
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // ---- Layout montado por código (não precisa de arquivo XML) ----
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(80, 50, 80, 50);
        raiz.setBackgroundColor(Color.parseColor("#141923"));

        raiz.addView(criarTexto("Configuração do Painel", 30, Color.WHITE));

        // Instrução para configurar pelo celular
        TextView info = criarTexto(
                "Configurar pelo celular (mesma rede Wi-Fi): abra no navegador  http://"
                        + obterIp() + ":" + PORTA, 18, Color.parseColor("#7FD1FF"));
        raiz.addView(info);

        // ---- Campo dos links ----
        raiz.addView(criarTexto(
                "Links do Power BI (um por linha ou separados por vírgula):", 20, Color.WHITE));

        edLinks = new EditText(this);
        edLinks.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_VARIATION_URI);
        edLinks.setMinLines(4);
        edLinks.setGravity(Gravity.TOP);
        edLinks.setTextColor(Color.WHITE);
        edLinks.setTextSize(18);
        edLinks.setText(prefs.getString("links", "")); // mostra o que já estava salvo
        raiz.addView(edLinks);

        // ---- Campo do tempo ----
        raiz.addView(criarTexto("Tempo de cada painel (segundos):", 20, Color.WHITE));

        edTempo = new EditText(this);
        edTempo.setInputType(InputType.TYPE_CLASS_NUMBER);
        edTempo.setTextColor(Color.WHITE);
        edTempo.setTextSize(18);
        edTempo.setText(String.valueOf(prefs.getInt("tempo", TEMPO_PADRAO)));
        raiz.addView(edTempo);

        // ---- Botão salvar ----
        Button btnSalvar = new Button(this);
        btnSalvar.setText("Salvar e Iniciar Painel");
        btnSalvar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salvarEIniciar();
            }
        });
        raiz.addView(btnSalvar);

        // ScrollView para o teclado na tela não esconder os campos
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(raiz);
        setContentView(scroll);

        // Foco inicial no campo de links: o controle já navega e o OK abre o teclado
        edLinks.requestFocus();

        // Liga o servidor para configurar pelo celular
        servidor = new ServidorLocal();
        servidor.start();
    }

    @Override
    protected void onDestroy() {
        // Desliga o servidor ao sair da tela
        if (servidor != null) servidor.parar();
        super.onDestroy();
    }

    /**
     * Botão Voltar:
     *  - se já existe configuração salva, volta ao painel;
     *  - se não existe (primeira vez), apenas fecha o app.
     */
    @Override
    public void onBackPressed() {
        String links = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("links", "");
        if (links != null && links.toLowerCase().contains("http")) {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }

    // ------------------------------------------------------------------
    // SALVAR
    // ------------------------------------------------------------------

    /** Valida os campos, salva nas preferências e abre o painel. */
    private void salvarEIniciar() {
        String links = edLinks.getText().toString().trim();

        // Converte o tempo; se vier vazio/inválido usa o padrão
        int tempo;
        try {
            tempo = Integer.parseInt(edTempo.getText().toString().trim());
        } catch (NumberFormatException e) {
            tempo = TEMPO_PADRAO;
        }
        tempo = Math.max(TEMPO_MIN, Math.min(TEMPO_MAX, tempo));

        // Exige pelo menos um link http/https
        if (!links.toLowerCase().contains("http")) {
            Toast.makeText(this, "Informe ao menos um link http/https", Toast.LENGTH_LONG).show();
            return;
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString("links", links)
                .putInt("tempo", tempo)
                .apply();

        // Abre o painel e fecha esta tela
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // ------------------------------------------------------------------
    // AUXILIARES DE INTERFACE
    // ------------------------------------------------------------------

    /** Cria um TextView padronizado. */
    private TextView criarTexto(String texto, int tamanhoSp, int cor) {
        TextView tv = new TextView(this);
        tv.setText(texto);
        tv.setTextSize(tamanhoSp);
        tv.setTextColor(cor);
        tv.setPadding(0, 20, 0, 8);
        return tv;
    }

    /** Descobre o IP da TV na rede local (para mostrar na tela). */
    private String obterIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> enderecos = ni.getInetAddresses();
                while (enderecos.hasMoreElements()) {
                    InetAddress ip = enderecos.nextElement();
                    // Ignora 127.0.0.1 e endereços IPv6
                    if (!ip.isLoopbackAddress() && ip instanceof Inet4Address) {
                        return ip.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // Se falhar, mostra um texto genérico
        }
        return "IP-DA-TV";
    }

    /** Escapa caracteres especiais para colocar texto dentro do HTML. */
    private static String escaparHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------
    // MINI SERVIDOR WEB (configurar pelo celular)
    // ------------------------------------------------------------------

    /**
     * Servidor HTTP bem simples, só para esta tela:
     *   GET  /        -> devolve a página com o formulário (já preenchido);
     *   POST /salvar  -> recebe links e tempo, preenche os campos da TV e inicia o painel.
     *
     * Só funciona na rede local e só enquanto a tela de configurações está aberta.
     */
    private class ServidorLocal extends Thread {

        private ServerSocket serverSocket;
        private volatile boolean rodando = true;

        /** Encerra o servidor (fecha o socket para destravar o accept()). */
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
                    Socket cliente = serverSocket.accept(); // espera uma conexão
                    try {
                        atender(cliente);
                    } catch (Exception ignored) {
                        // Erro em uma requisição não derruba o servidor
                    }
                    try { cliente.close(); } catch (IOException ignored) { }
                }
            } catch (IOException ignored) {
                // Porta ocupada ou servidor encerrado: apenas sai
            }
        }

        /** Lê a requisição HTTP e responde. */
        private void atender(Socket cliente) throws IOException {
            cliente.setSoTimeout(5000);
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(cliente.getInputStream(), "UTF-8"));

            // Primeira linha: "GET / HTTP/1.1" ou "POST /salvar HTTP/1.1"
            String requisicao = entrada.readLine();
            if (requisicao == null) return;

            // Cabeçalhos: só precisamos do Content-Length (tamanho do corpo do POST)
            int tamanhoCorpo = 0;
            String linha;
            while ((linha = entrada.readLine()) != null && !linha.isEmpty()) {
                if (linha.toLowerCase().startsWith("content-length:")) {
                    tamanhoCorpo = Integer.parseInt(linha.substring(15).trim());
                }
            }

            String html;

            if (requisicao.startsWith("POST")) {
                // ---- Lê o corpo: "links=...&tempo=..." (codificado como URL) ----
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

                // Mexer na interface só pode ser feito na thread principal
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        edLinks.setText(linksFinal);
                        edTempo.setText(tempoFinal);
                        salvarEIniciar(); // valida, salva e abre o painel na TV
                    }
                });

                html = montarPagina("Enviado! A TV vai iniciar o painel.", linksFinal, tempoFinal);

            } else {
                // ---- GET: mostra o formulário com os valores atuais ----
                SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                html = montarPagina("",
                        p.getString("links", ""),
                        String.valueOf(p.getInt("tempo", TEMPO_PADRAO)));
            }

            // ---- Envia a resposta HTTP ----
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

        /** Monta a página HTML exibida no celular. */
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