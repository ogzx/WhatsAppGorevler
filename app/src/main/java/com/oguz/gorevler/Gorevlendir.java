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
        return dene(c, kimden, sohbet, metin, zaman, imza, foto, tekrarKontrol, false);
    }

    /**
     * @param oncelikli alıntılanan/yanıtlanan mesaj gibi önemli sayılan → yıldızlı eklenir
     * @return yeni görev eklendiyse true
     */
    static boolean dene(Context c, String kimden, String sohbet, String metin, long zaman,
                        String imza, String foto, boolean tekrarKontrol, boolean oncelikli) {
        if (metin == null) return false;
        metin = metin.trim();
        if (metin.isEmpty() || OZET.matcher(metin).find()) return false;

        String eslesen;
        boolean yildiz = oncelikli;

        // 1) Kendine görev kalıbı ("#yap ...", "görev: ...") — en güçlü, herkesten ve kendinden kabul.
        String tetikli = Ayarlar.tetikSoyulmus(c, metin);
        if (tetikli != null && !tetikli.isEmpty()) {
            metin = tetikli;
            eslesen = "";
            yildiz = true;
        } else {
            // 2) Kendi not sohbeti: oradaki her mesaj görev (kendi mesajın dahil).
            boolean kendiSohbet = Ayarlar.kendiSohbetMi(c, sohbet);
            if (!kendiSohbet) {
                if (!Ayarlar.kisiUygun(c, kimden, sohbet)) return false;
                if (benim(c, kimden)) return false;
            }
            // 3) Her mesajı görev olan gruplar/kişiler, veya önemli+hepsi, veya genel "hepsi".
            boolean hepsi = kendiSohbet || Ayarlar.herMesaj(c, kimden, sohbet) || Ayarlar.hepsi(c)
                    || (Ayarlar.onemli(c, kimden, sohbet) && Ayarlar.onemliHepsi(c));
            eslesen = Ayarlar.eslesen(c, metin);
            if (eslesen == null) {
                if (hepsi) eslesen = "";
                else return false;
            }
        }

        Db db = Db.get(c);
        if (tekrarKontrol && db.varMi(metin, sohbet, kimden, zaman - TEKRAR_PENCERE)) return false;

        long son = Ayarlar.tarihYakala(c) ? Tarih.bul(metin, zaman, Ayarlar.basSaat(c)) : 0;
        long id = db.ekleId(kimden, sohbet, metin, zaman, eslesen, imza, foto, son, null);
        if (id == -1) return false;
        String tutar = Tutar.bul(metin);
        if (tutar != null) db.setTutar(id, tutar);
        if (yildiz) db.setOncelik(id, true);
        if (son > 0) Hatirlatici.sonTarihleriPlanla(c);
        if (Ayarlar.yeniBildirim(c)) Bildirim.yeniGorev(c, kimden, metin);
        return true;
    }

    /** Son tarihi henüz çıkarılmamış açık görevleri tarar, bulursa işaretler ve alarm kurar. */
    static void tarihleriIsle(Context c) {
        if (!Ayarlar.tarihYakala(c)) return;
        boolean degisti = false;
        for (Db.Gorev g : Db.get(c).liste(false)) {
            if (g.son > 0) continue;
            long son = Tarih.bul(g.metin, g.zaman, Ayarlar.basSaat(c));
            if (son > 0) { Db.get(c).setSon(g.id, son); degisti = true; }
        }
        Hatirlatici.sonTarihleriPlanla(c);
        if (degisti) ekraniYenile();
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
