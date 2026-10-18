package com.oguz.gorevler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Bir görevin son tarihi geldiğinde çalışır: görev hâlâ açıksa hatırlatma gösterir. */
public class SonTarihReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        long id = intent.getLongExtra(Hatirlatici.EXTRA_GOREV, -1);
        if (id < 0) return;
        Db.Gorev g = Db.get(c).getir(id);
        if (g != null && !g.tamam) Bildirim.sonTarih(c, g);
    }
}
