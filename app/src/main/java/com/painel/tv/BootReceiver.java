package com.painel.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Inicia o painel automaticamente quando a TV liga (boot concluído).
 *
 * Requisitos:
 *  - Permissão RECEIVE_BOOT_COMPLETED no AndroidManifest;
 *  - Permissão "Sobrepor a outros aplicativos" concedida (Android 10+ bloqueia
 *    abrir telas em segundo plano sem ela);
 *  - O app precisa ter sido aberto manualmente pelo menos uma vez após instalar.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String acao = intent.getAction();

        // BOOT_COMPLETED: boot normal. QUICKBOOT_POWERON: "boot rápido" de alguns aparelhos.
        if (Intent.ACTION_BOOT_COMPLETED.equals(acao)
                || "android.intent.action.QUICKBOOT_POWERON".equals(acao)) {

            // A MainActivity decide sozinha: sem configuração abre as Configurações,
            // com configuração abre direto o painel.
            Intent abrir = new Intent(context, MainActivity.class);
            abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // obrigatório fora de uma Activity
            context.startActivity(abrir);
        }
    }
}