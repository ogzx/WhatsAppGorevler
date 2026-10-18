package com.oguz.gorevler;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mesaj metnindeki Türkçe tarih ifadelerini son tarihe (ms) çevirir.
 * Bulamazsa 0 döner. Örnekler: "yarın", "haftaya", "ayın 15'i", "15 Kasım",
 * "15.11", "15/11/2026", "ay sonuna kadar", "pazartesi".
 */
public final class Tarih {

    private Tarih() {}

    private static final String[] AYLAR = {
            "ocak", "şubat", "mart", "nisan", "mayıs", "haziran",
            "temmuz", "ağustos", "eylül", "ekim", "kasım", "aralık"};

    // gün adları: pazartesi=2 ... pazar=1 (Calendar.DAY_OF_WEEK)
    private static final String[] GUNLER = {
            "pazartesi", "salı", "çarşamba", "perşembe", "cuma", "cumartesi", "pazar"};
    private static final int[] GUN_NO = {
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};

    private static final Pattern GG_AA_YYYY =
            Pattern.compile("\\b(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?\\b");
    private static final Pattern AYIN_N =
            Pattern.compile("ay(?:ın|in)?\\s+(\\d{1,2})\\b");
    private static final Pattern GUN_AY_ADI =
            Pattern.compile("\\b(\\d{1,2})\\s*(ocak|şubat|mart|nisan|mayıs|haziran|temmuz|ağustos|eylül|ekim|kasım|aralık)");
    private static final Pattern NINE_KADAR =
            Pattern.compile("\\b(\\d{1,2})['’]?(?:in|ın|ine|ına|une|üne|a|e)?\\s*(?:kadar|son)?\\b");

    /** Varsayılan hatırlatma saati (gün içi). */
    static int saat(Object ctx) { return 9; }

    static long bul(String metin, long now, int saatBasi) {
        if (metin == null) return 0;
        String t = metin.toLowerCase(java.util.Locale.forLanguageTag("tr-TR"));

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        gunAyarla(c, saatBasi);

        // yarın / bugün / haftaya / ay sonu
        if (t.contains("yarın")) { c.add(Calendar.DAY_OF_MONTH, 1); return c.getTimeInMillis(); }
        if (t.contains("bugün")) { return c.getTimeInMillis(); }
        if (t.contains("haftaya") || t.contains("gelecek hafta")) {
            c.add(Calendar.DAY_OF_MONTH, 7); return c.getTimeInMillis();
        }
        if (t.contains("ay sonu") || t.contains("ayın sonu") || t.contains("ay sonuna")
                || t.contains("ayın sonuna")) {
            c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
            return c.getTimeInMillis();
        }

        // gün + ay adı: "15 Kasım"
        Matcher m = GUN_AY_ADI.matcher(t);
        if (m.find()) {
            int gun = Integer.parseInt(m.group(1));
            int ay = ayNo(m.group(2));
            if (ay >= 0 && gun >= 1 && gun <= 31) return kur(now, saatBasi, gun, ay, -1);
        }

        // gg.aa(.yyyy)
        m = GG_AA_YYYY.matcher(t);
        if (m.find()) {
            int gun = Integer.parseInt(m.group(1));
            int ay = Integer.parseInt(m.group(2)) - 1;
            int yil = m.group(3) != null ? yilTamamla(Integer.parseInt(m.group(3))) : -1;
            if (gun >= 1 && gun <= 31 && ay >= 0 && ay <= 11) return kur(now, saatBasi, gun, ay, yil);
        }

        // "ayın 15'i"
        m = AYIN_N.matcher(t);
        if (m.find()) {
            int gun = Integer.parseInt(m.group(1));
            if (gun >= 1 && gun <= 31) return kur(now, saatBasi, gun, -1, -1);
        }

        // gün adı: "pazartesi"
        for (int i = 0; i < GUNLER.length; i++) {
            if (t.contains(GUNLER[i])) {
                Calendar g = Calendar.getInstance();
                g.setTimeInMillis(now);
                gunAyarla(g, saatBasi);
                int fark = (GUN_NO[i] - g.get(Calendar.DAY_OF_WEEK) + 7) % 7;
                if (fark == 0) fark = 7; // bugünse haftaya
                g.add(Calendar.DAY_OF_MONTH, fark);
                return g.getTimeInMillis();
            }
        }

        // "15'ine kadar" gibi sadece gün (ay belirtilmemiş)
        m = NINE_KADAR.matcher(t);
        if ((t.contains("kadar") || t.contains("son gün") || t.contains("kalan")) && m.find()) {
            int gun = Integer.parseInt(m.group(1));
            if (gun >= 1 && gun <= 31) return kur(now, saatBasi, gun, -1, -1);
        }

        return 0;
    }

    private static void gunAyarla(Calendar c, int saatBasi) {
        c.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, saatBasi)));
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    /** gün + (ay, -1=bu ay) + (yıl, -1=bu yıl); geçmişse bir sonraki aya/yıla kaydırır. */
    private static long kur(long now, int saatBasi, int gun, int ay, int yil) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        gunAyarla(c, saatBasi);
        if (yil >= 0) c.set(Calendar.YEAR, yil);
        if (ay >= 0) c.set(Calendar.MONTH, ay);
        int maxGun = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        c.set(Calendar.DAY_OF_MONTH, Math.min(gun, maxGun));

        // Tarih geçmişte kaldıysa ileri al (gün verildi, ay/yıl verilmediyse).
        if (c.getTimeInMillis() < now - 6L * 3600 * 1000) {
            if (ay < 0) c.add(Calendar.MONTH, 1);
            else if (yil < 0) c.add(Calendar.YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    private static int ayNo(String ad) {
        for (int i = 0; i < AYLAR.length; i++) if (AYLAR[i].equals(ad)) return i;
        return -1;
    }

    private static int yilTamamla(int y) { return y < 100 ? 2000 + y : y; }

    static String bicim(long ms) {
        if (ms <= 0) return null;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(java.util.Locale.forLanguageTag("tr-TR"),
                "%02d.%02d.%04d", c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
    }
}
