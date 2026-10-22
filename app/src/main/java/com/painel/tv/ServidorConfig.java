package com.painel.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Enumeration;

/**
 * Mini servidor web para configurar o painel pelo celular (mesmo Wi-Fi).
 * Roda enquanto o app estiver aberto, inclusive durante o painel (sem abrir as Configurações).
 * Acesso: http://IP_DA_TV:8080
 */
public class ServidorConfig extends Thread {

    public static final int PORTA = 8080;
    private static final String PREFS_NAME = "PainelPrefs";

    private static final int TEMPO_PADRAO = 30, TEMPO_MIN = 10, TEMPO_MAX = 3600;
    private static final int DELAY_PADRAO = 10, DELAY_MIN = 0, DELAY_MAX = 60;

    private static ServidorConfig instancia;
    private static volatile Runnable aoSalvar;
    private static final Handler handlerUi = new Handler(Looper.getMainLooper());

    private final Context contexto;

    private ServidorConfig(Context ctx) {
        this.contexto = ctx.getApplicationContext();
        setDaemon(true);
    }

    /** Inicia o servidor (uma única vez por processo). */
    public static synchronized void iniciar(Context ctx) {
        if (instancia != null && instancia.isAlive()) return;
        instancia = new ServidorConfig(ctx);
        instancia.start();
    }

    /** A Activity visível registra o que fazer depois que a configuração for salva pelo celular. */
    public static void definirOuvinte(Runnable r) {
        aoSalvar = r;
    }

    public static String obterIp() {
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

    @Override
    public void run() {
        ServerSocket servidor = null;
        try {
            servidor = new ServerSocket();
            servidor.setReuseAddress(true);
            servidor.bind(new InetSocketAddress(PORTA));
            while (true) {
                Socket cliente = servidor.accept();
                try {
                    atender(cliente);
                } catch (Exception ignored) { }
                try { cliente.close(); } catch (IOException ignored) { }
            }
        } catch (IOException ignored) {
            // Se falhar, o próximo iniciar() tenta de novo.
        } finally {
            try { if (servidor != null) servidor.close(); } catch (IOException ignored) { }
        }
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

        SharedPreferences p = contexto.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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

            String links = "", tempo = "", delay = "", usuario = "", senha = "";
            for (String par : corpo.split("&")) {
                String[] kv = par.split("=", 2);
                if (kv.length < 2) continue;
                String valor = URLDecoder.decode(kv[1], "UTF-8");
                if (kv[0].equals("links")) links = valor;
                if (kv[0].equals("tempo")) tempo = valor;
                if (kv[0].equals("delay")) delay = valor;
                if (kv[0].equals("usuario")) usuario = valor;
                if (kv[0].equals("senha")) senha = valor;
            }

            links = links.trim();
            usuario = usuario.trim();

            if (!links.toLowerCase().contains("http")) {
                html = montarPagina("Informe ao menos um link http:// ou https://",
                        links, tempo, delay, usuario, senha, true);
            } else {
                int t = converter(tempo, TEMPO_PADRAO, TEMPO_MIN, TEMPO_MAX);
                int d = converter(delay, DELAY_PADRAO, DELAY_MIN, DELAY_MAX);

                p.edit()
                        .putString("links", links)
                        .putInt("tempo", t)
                        .putInt("delay", d)
                        .putString("usuario", usuario)
                        .putString("senha", senha)
                        .apply();

                final Runnable ouvinte = aoSalvar;
                if (ouvinte != null) {
                    handlerUi.post(ouvinte);
                }

                html = montarPagina("Enviado! A TV vai reiniciar o painel.",
                        links, String.valueOf(t), String.valueOf(d), usuario, senha, false);
            }
        } else {
            html = montarPagina("",
                    p.getString("links", ""),
                    String.valueOf(p.getInt("tempo", TEMPO_PADRAO)),
                    String.valueOf(p.getInt("delay", DELAY_PADRAO)),
                    p.getString("usuario", ""),
                    p.getString("senha", ""), false);
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

    private static int converter(String valor, int padrao, int min, int max) {
        int n;
        try {
            n = Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            n = padrao;
        }
        return Math.max(min, Math.min(max, n));
    }

    private static String escaparHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String montarPagina(String mensagem, String links, String tempo, String delay,
                                String usuario, String senha, boolean erro) {
        String cor = erro ? "#E57373" : "#81C784";
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>Painel TV</title></head>"
                + "<body style='font-family:sans-serif;padding:16px;background:#141923;color:#fff'>"
                + "<h2>Painel TV - Power BI</h2>"
                + "<p style='color:" + cor + ";font-weight:bold'>" + escaparHtml(mensagem) + "</p>"
                + "<form method='post' action='/salvar'>"
                + "<p>Links (um por linha):</p>"
                + "<textarea name='links' rows='6' style='width:100%;padding:8px'>" + escaparHtml(links) + "</textarea>"
                + "<p>Tempo por painel (segundos):</p>"
                + "<input name='tempo' type='number' style='width:100%;padding:8px' value='" + escaparHtml(tempo) + "'>"
                + "<p>Delay de carregamento (segundos, 0 a " + DELAY_MAX + "):</p>"
                + "<input name='delay' type='number' min='" + DELAY_MIN + "' max='" + DELAY_MAX + "' style='width:100%;padding:8px' value='" + escaparHtml(delay) + "'>"
                + "<h3>Login Microsoft / Power BI (para links privados)</h3>"
                + "<p>E-mail corporativo:</p>"
                + "<input name='usuario' type='email' style='width:100%;padding:8px' value='" + escaparHtml(usuario) + "'>"
                + "<p>Senha:</p>"
                + "<input name='senha' type='password' style='width:100%;padding:8px' value='" + escaparHtml(senha) + "'>"
                + "<p style='margin-top:20px'><button type='submit' style='width:100%;padding:14px;font-size:18px;background:#2E7D32;color:#fff;border:none;border-radius:8px'>"
                + "Salvar e Iniciar na TV</button></p>"
                + "</form></body></html>";
    }
}
