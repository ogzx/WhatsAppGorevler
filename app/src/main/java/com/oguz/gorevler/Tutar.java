package com.oguz.gorevler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mesaj metnindeki para tutarını ("1.250,50 TL", "₺2.500", "1250 lira") yakalar. */
public final class Tutar {

    private Tutar() {}

    // Sayı + TL/₺/lira; ya da ₺ + sayı. Binlik ayıracı nokta, kuruş virgül olabilir.
    private static final Pattern P1 = Pattern.compile(
            "(\\d{1,3}(?:[.\\s]\\d{3})+(?:,\\d{1,2})?|\\d+(?:,\\d{1,2})?)\\s*(?:tl|try|₺|lira)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P2 = Pattern.compile(
            "[₺]\\s*(\\d{1,3}(?:[.\\s]\\d{3})+(?:,\\d{1,2})?|\\d+(?:,\\d{1,2})?)");

    /** Bulursa düzgün biçimde ("1.250,50 ₺") döndürür, yoksa null. */
    static String bul(String metin) {
        if (metin == null) return null;
        Matcher m = P1.matcher(metin);
        if (m.find()) return bicim(m.group(1));
        m = P2.matcher(metin);
        if (m.find()) return bicim(m.group(1));
        return null;
    }

    private static String bicim(String ham) {
        String s = ham.replace(" ", "").trim();
        return s + " ₺";
    }
}
