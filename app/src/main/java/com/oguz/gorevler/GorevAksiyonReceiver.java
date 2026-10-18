package com.oguz.gorevler;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Bildirimdeki "Tamamla" ve "Ertele" düğmelerini işler. */
public class GorevAksiyonReceiver extends BroadcastReceiver {

    static final String TAMAMLA = "com.oguz.gorevler.TAMAMLA";
    static final String ERTELE = "com.oguz.gorevler.ERTELE";
    static final String EXTRA_ID = "id";

    @Override
    public void onReceive(Context c, Intent intent) {
        long id = intent.getLongExtra(EXTRA_ID, -1);
        if (id < 0) return;
        String a = intent.getAction();
        if (TAMAMLA.equals(a)) {
            Db.get(c).setTamam(id, true);
        } else if (ERTELE.equals(a)) {
            Db.get(c).setSon(id, System.currentTimeMillis() + 24L * 3600 * 1000);
            Hatirlatici.sonTarihleriPlanla(c);
        }
        // Bildirimi kapat
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel((int) (2_000_000 + id));
        Gorevlendir.ekraniYenile();
        GorevWidget.hepsiniYenile(c);
    }
}
