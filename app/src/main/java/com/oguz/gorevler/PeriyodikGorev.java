package com.oguz.gorevler;

import android.content.Context;

import java.util.Calendar;
import java.util.List;

/**
 * Her ay tekrar eden hatırlatmalar (KDV, muhtasar, SGK gibi). Kullanıcı ad ve ayın
 * kaçıncı günü olduğunu belirler. Yaklaşan ay için görev yoksa oluşturur.
 *
 * ÖNEMLİ: Buradaki günler yalnızca HATIRLATMADIR, resmî son tarih değildir.
 * Beyanname/ödeme günleri yıla ve mükellef türüne göre değişir; GİB/SGK takvimini
 * esas alın. Varsayılanlar yaygın kullanılan günlerdir, kullanıcı düzenleyebilir.
 */
public final class PeriyodikGorev {

    /** "Ad|gün" satırları. Kullanıcı Ayarlar'da değiştirebilir. */
    static final String VARSAYILAN =
            "KDV Beyannamesi (kontrol et)|28\n"
            + "Muhtasar ve Prim Hizmet Beyannamesi|26\n"
            + "SGK Bildirgesi|23\n"
            + "BA-BS Formları|30";

    /** Görev, son tarihinden kaç gün önce oluşturulsun. */
    private static final int ONCE_GUN = 15;

    private PeriyodikGorev() {}

    /** Yaklaşan periyodik görevleri oluşturur. Gün içinde birden çok kez çağrılabilir. */
    static void olustur(Context c) {
        if (!Ayarlar.periyodik(c)) return;
        String metinAyar = Ayarlar.periyodikSecim(c);
        if (metinAyar.trim().isEmpty()) metinAyar = VARSAYILAN;

        Db db = Db.get(c);
        long simdi = System.currentTimeMillis();
        int saat = Ayarlar.basSaat(c);

        for (String satir : Ayarlar.satirlar(metinAyar)) {
            String[] p = satir.split("\\|");
            if (p.length < 2) continue;
            String ad = p[0].trim();
            int gun;
            try { gun = Integer.parseInt(p[1].trim()); } catch (NumberFormatException e) { continue; }
            if (ad.isEmpty() || gun < 1 || gun > 31) continue;

            long son = birSonrakiGun(simdi, gun, saat);
            // Yalnızca son tarihe ONCE_GUN veya daha az kaldıysa oluştur.
            if (son - simdi > ONCE_GUN * 24L * 3600 * 1000) continue;

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(son);
            String anahtar = "periyodik|" + ad + "|" + cal.get(Calendar.YEAR) + "-"
                    + (cal.get(Calendar.MONTH) + 1);
            if (db.imzaVar(anahtar)) continue;

            String metin = ad + " — son " + Tarih.bicim(son);
            db.ekleId("Periyodik", null, metin, simdi, "periyodik", anahtar, null, son, null);
        }
        Hatirlatici.sonTarihleriPlanla(c);
    }

    /** Verilen günün bu ay ya da (geçtiyse) gelecek aydaki tarihi. */
    private static long birSonrakiGun(long simdi, int gun, int saat) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(simdi);
        c.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, saat)));
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int maxGun = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        c.set(Calendar.DAY_OF_MONTH, Math.min(gun, maxGun));
        if (c.getTimeInMillis() < simdi) {
            c.add(Calendar.MONTH, 1);
            maxGun = c.getActualMaximum(Calendar.DAY_OF_MONTH);
            c.set(Calendar.DAY_OF_MONTH, Math.min(gun, maxGun));
        }
        return c.getTimeInMillis();
    }
}
