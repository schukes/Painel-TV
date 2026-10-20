package com.painel.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

public class SettingsActivity extends Activity {

    private EditText etLinks, etTempo;
    private Button btnSalvar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etLinks = findViewById(R.id.etLinks);
        etTempo = findViewById(R.id.etTempo);
        btnSalvar = findViewById(R.id.btnSalvar);

        SharedPreferences prefs = getSharedPreferences("PainelPrefs", MODE_PRIVATE);
        String linksSalvos = prefs.getString("links", "");
        int tempoSalvo = prefs.getInt("tempo", 30);

        if (!linksSalvos.isEmpty()) {
            etLinks.setText(linksSalvos);
        }
        etTempo.setText(String.valueOf(tempoSalvo));

        btnSalvar.setOnClickListener(v -> {
            String links = etLinks.getText().toString().trim();
            int tempo = 30;
            try {
                tempo = Integer.parseInt(etTempo.getText().toString().trim());
            } catch (Exception e) {
                tempo = 30;
            }

            prefs.edit()
                    .putString("links", links)
                    .putInt("tempo", tempo)
                    .apply();

            iniciarPainel();
        });
    }

    private void iniciarPainel() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}