package com.oguz.gorevler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Saat başı çalışır: açık görev varsa listeyi bildirir, sonraki saati kurar. */
public class HatirlaticiReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        try {
            if (Ayarlar.saatlik(c) && Hatirlatici.saatUygun(c) && Db.get(c).sayi(false) > 0) {
                Bildirim.liste(c);
            }
        } finally {
            Hatirlatici.planla(c);
        }
    }
}
