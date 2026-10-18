package com.oguz.gorevler;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Uygulama ayarları ve anahtar kelime eşleştirme. */
public final class Ayarlar {

    static final String VARSAYILAN_KELIMELER =
            "beyanname\nfatura\nödeme\nöde\nsgk\nbordro\nkdv\nmuhtasar\ntahakkuk\n"
            + "dekont\nmakbuz\nekstre\nmizan\nbilanço\ngönder\nyolla\nhazırla\n"
            + "lazım\nacil\nson gün\nhatırlat\nrica";

    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    private Ayarlar() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
    }

    static boolean aktif(Context c) { return p(c).getBoolean("aktif", true); }

    static boolean hepsi(Context c) { return p(c).getBoolean("hepsi", false); }

    static String kelimelerMetin(Context c) {
        return p(c).getString("kelimeler", VARSAYILAN_KELIMELER);
    }

    static SharedPreferences.Editor duzenle(Context c) { return p(c).edit(); }

    // --- Önemli kişiler ---
    static String onemliMetin(Context c) { return p(c).getString("onemli", ""); }
    static boolean sadeceOnemli(Context c) { return p(c).getBoolean("sadeceOnemli", false); }
    static boolean onemliHepsi(Context c) { return p(c).getBoolean("onemliHepsi", true); }

    static List<String> onemliler(Context c) { return satirlar(onemliMetin(c)); }

    /** Kişi adı ya da grup adı "önemli kişiler" listesinde mi? */
    static boolean onemli(Context c, String kimden, String sohbet) {
        String k = kimden == null ? null : normalize(kimden).trim();
        String s = sohbet == null ? null : normalize(sohbet).trim();
        for (String ad : onemliler(c)) {
            String n = normalize(ad).trim();
            if (n.isEmpty()) continue;
            if (n.equals(k) || n.equals(s)) return true;
        }
        return false;
    }

    /** Kişi filtresinden geçiyor mu? */
    static boolean kisiUygun(Context c, String kimden, String sohbet) {
        return !sadeceOnemli(c) || onemli(c, kimden, sohbet);
    }

    // --- Fotoğraf ---
    static boolean foto(Context c) { return p(c).getBoolean("foto", true); }

    // --- Bildirimler / saatlik hatırlatma ---
    static boolean yeniBildirim(Context c) { return p(c).getBoolean("yeniBildirim", false); }
    static boolean saatlik(Context c) { return p(c).getBoolean("saatlik", true); }
    static int basSaat(Context c) { return p(c).getInt("basSaat", 9); }
    static int bitSaat(Context c) { return p(c).getInt("bitSaat", 19); }
    static String numara(Context c) { return p(c).getString("numara", ""); }

    static List<String> satirlar(String metin) {
        List<String> sonuc = new ArrayList<>();
        if (metin == null) return sonuc;
        for (String k : metin.split("[\\n,;]+")) {
            String t = k.trim();
            if (!t.isEmpty()) sonuc.add(t);
        }
        return sonuc;
    }

    /** Kullanıcının yazdığı haliyle kelime listesi. */
    static List<String> kelimeler(Context c) { return satirlar(kelimelerMetin(c)); }

    /** Büyük/küçük harf ve Türkçe karakter farkını yok sayar (ödeme = ODEME = Ödeme). */
    static String normalize(String s) {
        String t = s.toLowerCase(TR);
        StringBuilder b = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            switch (ch) {
                case 'ı': ch = 'i'; break;
                case 'ö': ch = 'o'; break;
                case 'ü': ch = 'u'; break;
                case 'ş': ch = 's'; break;
                case 'ç': ch = 'c'; break;
                case 'ğ': ch = 'g'; break;
                case 'â': ch = 'a'; break;
                case 'î': ch = 'i'; break;
                case 'û': ch = 'u'; break;
                default: break;
            }
            if (Character.isWhitespace(ch)) ch = ' ';
            b.append(ch);
        }
        return b.toString().replaceAll(" +", " ");
    }

    /** Metinde geçen ilk anahtar kelimeyi döndürür, yoksa null. */
    static String eslesen(Context c, String metin) {
        if (metin == null) return null;
        String n = normalize(metin);
        for (String k : kelimeler(c)) {
            String nk = normalize(k);
            if (!nk.isEmpty() && n.contains(nk)) return k;
        }
        return null;
    }
}
