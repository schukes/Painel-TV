
package com.painel.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "PainelTV";

    private static final String ACTION_QUICKBOOT_POWERON =
            "android.intent.action.QUICKBOOT_POWERON";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();

        boolean inicializacaoNormal =
                Intent.ACTION_BOOT_COMPLETED.equals(action);

        boolean inicializacaoRapida =
                ACTION_QUICKBOOT_POWERON.equals(action);

        if (!inicializacaoNormal && !inicializacaoRapida) {
            return;
        }

        try {
            Intent painel = new Intent(context, MainActivity.class);

            painel.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            context.startActivity(painel);

            Log.i(TAG, "Solicitada abertura automática do painel.");

        } catch (Exception e) {
            Log.e(TAG, "Não foi possível abrir o painel após a inicialização.", e);
        }
    }
}
