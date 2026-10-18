package com.oguz.gorevler;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.regex.Pattern;

/**
 * Tek karar noktası: bir mesajın görev olup olmayacağına karar verir ve kaydeder.
 * Bildirim, ekran okuma, sohbet içe aktarma ve paylaşım hepsi buradan geçer.
 */
public final class Gorevlendir {

    /** "5 yeni mesaj", "3 sohbetten 7 mesaj" gibi özet satırları. */
    private static final Pattern OZET = Pattern.compile(
            "^\\d+\\s+(yeni\\s+)?(mesaj|sohbet|new message|message)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Farklı kaynaklardan gelen aynı mesajı tekrar eklememek için bakılan süre. */
    static final long TEKRAR_PENCERE = 3L * 24 * 60 * 60 * 1000;

    private Gorevlendir() {}

    /**
     * @param tekrarKontrol aynı sohbetten aynı metin yakın zamanda eklendiyse ekleme
     *                      (ekran okuma / içe aktarma bildirimle yakalananı tekrarlamasın)
     * @return yeni görev eklendiyse true
     */
    static boolean dene(Context c, String kimden, String sohbet, String metin, long zaman,
                        String imza, String foto, boolean tekrarKontrol) {
        if (metin == null) return false;
        metin = metin.trim();
        if (metin.isEmpty() || OZET.matcher(metin).find()) return false;
        if (!Ayarlar.kisiUygun(c, kimden, sohbet)) return false;
        if (benim(c, kimden)) return false;

        boolean onemli = Ayarlar.onemli(c, kimden, sohbet);
        String eslesen = Ayarlar.eslesen(c, metin);
        if (eslesen == null) {
            if (Ayarlar.hepsi(c) || (onemli && Ayarlar.onemliHepsi(c))) eslesen = "";
            else return false;
        }

        Db db = Db.get(c);
        if (tekrarKontrol && db.varMi(metin, sohbet, kimden, zaman - TEKRAR_PENCERE)) return false;

        boolean eklendi = db.ekle(kimden, sohbet, metin, zaman, eslesen, imza, foto);
        if (eklendi && Ayarlar.yeniBildirim(c)) Bildirim.yeniGorev(c, kimden, metin);
        return eklendi;
    }

    /** Kullanıcının kendi gönderdiği mesaj mı? (ayarlardaki "WhatsApp'taki adım") */
    private static boolean benim(Context c, String kimden) {
        String ad = Ayarlar.benimAdim(c);
        if (ad.isEmpty() || kimden == null) return false;
        return Ayarlar.normalize(ad).trim().equals(Ayarlar.normalize(kimden).trim());
    }

    /** Ana ekran açıksa listeyi yeniler. */
    static void ekraniYenile() {
        Runnable r = MainActivity.degisti;
        if (r != null) new Handler(Looper.getMainLooper()).post(r);
    }
}
