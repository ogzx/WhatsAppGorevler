package com.oguz.gorevler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/** Saat başı yapılacaklar bildirimini planlar. */
public final class Hatirlatici {

    private Hatirlatici() {}

    /** Bir sonraki tam saate alarm kurar (ayar kapalıysa iptal eder). */
    static void planla(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = pi(c);
        am.cancel(pi);
        if (!Ayarlar.saatlik(c)) return;

        Calendar k = Calendar.getInstance();
        k.set(Calendar.MINUTE, 0);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        k.add(Calendar.HOUR_OF_DAY, 1);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, k.getTimeInMillis(), pi);
    }

    private static PendingIntent pi(Context c) {
        return PendingIntent.getBroadcast(c, 7, new Intent(c, HatirlaticiReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static final String EXTRA_GOREV = "gorev_id";

    /** Son tarihi olan tüm açık görevler için hatırlatma alarmı kurar. */
    static void sonTarihleriPlanla(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long simdi = System.currentTimeMillis();
        for (Db.Gorev g : Db.get(c).sonTarihliler()) {
            PendingIntent pi = PendingIntent.getBroadcast(c, (int) (100000 + g.id),
                    new Intent(c, SonTarihReceiver.class).putExtra(EXTRA_GOREV, g.id),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            // Zamanı geçmiş ama tamamlanmamışsa kısa süre sonra bir kez hatırlat.
            long neZaman = g.son > simdi ? g.son : simdi + 60_000;
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, neZaman, pi);
        }
    }

    /** Şu anki saat, ayarlardaki aralıkta mı? (başlangıç ve bitiş dahil) */
    static boolean saatUygun(Context c) {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int bas = Ayarlar.basSaat(c);
        int bit = Ayarlar.bitSaat(c);
        if (bas > bit) return h >= bas || h <= bit; // ör. 20 → 2 (gece yarısını geçen aralık)
        return h >= bas && h <= bit;
    }
}
