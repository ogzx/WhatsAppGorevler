package com.oguz.gorevler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon yeniden başlayınca veya uygulama güncellenince saatlik hatırlatmayı yeniden kurar. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Hatirlatici.planla(c);
    }
}
