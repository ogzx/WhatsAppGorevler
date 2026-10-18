package com.oguz.gorevler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon yeniden başlayınca veya uygulama güncellenince hatırlatmaları yeniden kurar. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Bildirim.kanallar(c);
        Hatirlatici.planla(c);
        Hatirlatici.sonTarihleriPlanla(c);
        PeriyodikGorev.olustur(c);
        GorevWidget.hepsiniYenile(c);
    }
}
