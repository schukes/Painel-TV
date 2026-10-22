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
import android.webkit.CookieManager;
import android.webkit.WebStorage;
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

    // Delay de carregamento (segundos que o painel atual fica congelado após o próximo carregar)
    private static final int DELAY_PADRAO = 10;
    private static final int DELAY_MIN = 0;
    private static final int DELAY_MAX = 60;

    private ScrollView scrollView;
    private EditText edLinks;
    private EditText edTempo;
    private EditText edDelay;
    private EditText edUsuario;
    private EditText edSenha;
    private TextView txtStatusPermissao;

    private Button btnSalvar;
    private Button btnLimparSessao;
    private Button btnLoginManual;
    private Button btnPermissao;
    private Button btnToggleLauncher;
    private Button btnConfigTv;
    private Button btnSairTv;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(80, 70, 80, 70);
        raiz.setBackgroundColor(Color.parseColor("#141923"));
        raiz.setClipToPadding(false);
        raiz.setClipChildren(false);

        raiz.addView(criarTexto("Configuração do Painel TV", 26, Color.WHITE));

        raiz.addView(criarTexto("📱 Configurar pelo celular (mesmo Wi-Fi), acesse:",
                16, Color.parseColor("#7FD1FF")));
        TextView endereco = criarTexto("http://" + ServidorConfig.obterIp() + ":" + ServidorConfig.PORTA,
                24, Color.WHITE);
        endereco.setTypeface(null, android.graphics.Typeface.BOLD);
        raiz.addView(endereco);

        txtStatusPermissao = criarTexto("", 15, Color.YELLOW);
        raiz.addView(txtStatusPermissao);

        // ---- 1. Campo dos links ----
        raiz.addView(criarTexto("Links do Power BI (públicos ou privados / reportEmbed):", 16, Color.LTGRAY));
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

        // ---- 2.1 Campo do delay de carregamento ----
        raiz.addView(criarTexto(
                "Delay de carregamento (segundos, 0 a " + DELAY_MAX + "):", 16, Color.LTGRAY));
        raiz.addView(criarTexto(
                "Tempo que o painel atual fica congelado, depois que o próximo termina de carregar, "
                + "para os gráficos aparecerem antes da troca.", 13, Color.GRAY));
        edDelay = criarCampoTexto(
                String.valueOf(prefs.getInt("delay", DELAY_PADRAO)),
                InputType.TYPE_CLASS_NUMBER,
                1
        );
        raiz.addView(edDelay);

        // ---- 3. Campos de Login (Opcional para Links Privados) ----
        raiz.addView(criarTexto("Login do Power BI / Microsoft 365 (para links privados):", 18, Color.parseColor("#7FD1FF")));

        raiz.addView(criarTexto("E-mail corporativo (ex: usuario@empresa.com.br):", 15, Color.LTGRAY));
        edUsuario = criarCampoTexto(
                prefs.getString("usuario", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                1
        );
        raiz.addView(edUsuario);

        raiz.addView(criarTexto("Senha do Power BI:", 15, Color.LTGRAY));
        edSenha = criarCampoTexto(
                prefs.getString("senha", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                1
        );
        raiz.addView(edSenha);

        // ---- 4. Botão Salvar ----
        btnSalvar = criarBotaoTV("1. Salvar e Iniciar Painel", "#2E7D32", "#43A047");
        btnSalvar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salvarEIniciar();
            }
        });
        raiz.addView(btnSalvar);

        // ---- 5. Botão Limpar Sessão / Cookies ----
        btnLimparSessao = criarBotaoTV("Limpar Sessão / Trocar Conta Microsoft", "#E65100", "#FB8C00");
        btnLimparSessao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                limparCookiesESessao();
            }
        });
        raiz.addView(btnLimparSessao);

        // ---- 5.1 Login manual (digitar usuário/senha direto no navegador) ----
        btnLoginManual = criarBotaoTV("Login Manual (digitar no navegador)", "#00838F", "#00ACC1");
        btnLoginManual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salvarEIniciar(true);
            }
        });
        raiz.addView(btnLoginManual);

        // ---- Seção de Inicialização e Modo Launcher ----
        raiz.addView(criarTexto("Opções do Sistema e Launcher da TV:", 18, Color.parseColor("#7FD1FF")));

        btnPermissao = criarBotaoTV("2. Permitir Iniciar com a TV (Sobreposição)", "#1565C0", "#1E88E5");
        btnPermissao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pedirPermissaoSobreposicao();
            }
        });
        raiz.addView(btnPermissao);

        btnToggleLauncher = criarBotaoTV("", "#6A1B9A", "#8E24AA");
        atualizarTextoBotaoLauncher();
        btnToggleLauncher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alternarModoLauncher();
            }
        });
        raiz.addView(btnToggleLauncher);

        btnConfigTv = criarBotaoTV("4. Abrir Configurações da TV (Wi-Fi / Rede / Sistema)", "#37474F", "#546E7A");
        btnConfigTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirConfiguracoesDaTv();
            }
        });
        raiz.addView(btnConfigTv);

        btnSairTv = criarBotaoTV("5. Sair para o Menu Original da TV", "#C62828", "#E53935");
        btnSairTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sairParaMenuOriginalDaTv();
            }
        });
        raiz.addView(btnSairTv);

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setFocusable(false);
        scrollView.setFocusableInTouchMode(false);
        scrollView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scrollView.addView(raiz);
        setContentView(scrollView);

        configurarOrdemDeNavegacao();

        btnSalvar.post(new Runnable() {
            @Override
            public void run() {
                btnSalvar.requestFocus();
            }
        });

        ServidorConfig.iniciar(this);
    }

    private void configurarOrdemDeNavegacao() {
        View[] ordem = new View[]{
                edLinks,
                edTempo,
                edDelay,
                edUsuario,
                edSenha,
                btnSalvar,
                btnLimparSessao,
                btnLoginManual,
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
            View anterior = ordem[(i - 1 + ordem.length) % ordem.length];
            View proximo = ordem[(i + 1) % ordem.length];

            atual.setNextFocusUpId(anterior.getId());
            atual.setNextFocusDownId(proximo.getId());
        }
    }

    private Button criarBotaoTV(String textoInicial, final String corNormalHex, final String corFocoHex) {
        final Button b = new Button(this);
        b.setTag(textoInicial);
        b.setText("   " + textoInicial);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(36, 22, 36, 22);

        b.setFocusable(true);
        b.setFocusableInTouchMode(false);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 14, 0, 6);
        b.setLayoutParams(lp);

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

    private void aplicarEstiloCaixa(View view, String corFundoHex, boolean focado) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(corFundoHex));
        gd.setCornerRadius(14f);
        if (focado) {
            gd.setStroke(6, Color.WHITE);
        } else {
            gd.setStroke(2, Color.parseColor("#44FFFFFF"));
        }
        view.setBackground(gd);
    }

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
        ServidorConfig.definirOuvinte(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SettingsActivity.this, MainActivity.class));
                finish();
            }
        });
        atualizarStatusPermissao();
        atualizarTextoBotaoLauncher();
    }

    @Override
    protected void onPause() {
        ServidorConfig.definirOuvinte(null);
        super.onPause();
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
    // SALVAR E SESSÃO
    // ------------------------------------------------------------------
    private void salvarEIniciar() {
        salvarEIniciar(false);
    }

    private void salvarEIniciar(boolean manual) {
        String links = edLinks.getText().toString().trim();
        String usuario = edUsuario.getText().toString().trim();
        String senha = edSenha.getText().toString(); // Sem trim para não cortar espaços se houver na senha

        int tempo;
        try {
            tempo = Integer.parseInt(edTempo.getText().toString().trim());
        } catch (NumberFormatException e) {
            tempo = TEMPO_PADRAO;
        }
        tempo = Math.max(TEMPO_MIN, Math.min(TEMPO_MAX, tempo));

        int delay;
        try {
            delay = Integer.parseInt(edDelay.getText().toString().trim());
        } catch (NumberFormatException e) {
            delay = DELAY_PADRAO;
        }
        delay = Math.max(DELAY_MIN, Math.min(DELAY_MAX, delay));

        if (!links.toLowerCase().contains("http")) {
            Toast.makeText(this, "Informe ao menos um link http:// ou https://", Toast.LENGTH_LONG).show();
            edLinks.requestFocus();
            return;
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString("links", links)
                .putInt("tempo", tempo)
                .putInt("delay", delay)
                .putString("usuario", usuario)
                .putString("senha", senha)
                .apply();

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("modoManual", manual);
        startActivity(intent);
        finish();
    }

    private void limparCookiesESessao() {
        CookieManager cm = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.removeAllCookies(null);
            cm.flush();
        } else {
            cm.removeAllCookie();
        }
        WebStorage.getInstance().deleteAllData();
        Toast.makeText(this, "Sessão limpa! O Power BI fará um novo login ao iniciar.", Toast.LENGTH_LONG).show();
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

}