package com.oguz.gorevler;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Erişilebilirlik servisi: YALNIZCA WhatsApp açıkken ekranda görünen yazıları okur.
 *  - Açık sohbetteki gelen mesajlar (sohbet açıkken bildirim gelmeyen mesajlar dahil)
 *  - Sohbet listesindeki son mesaj önizlemeleri (sessize alınmış sohbetler dahil)
 * Hiçbir şeye dokunmaz, tıklamaz, yazmaz; veriler telefondan çıkmaz.
 */
public class EkranOkuyucu extends AccessibilityService {

    private final Handler ana = new Handler(Looper.getMainLooper());
    private String sonPaket = "com.whatsapp";
    private final Runnable oku = this::ekraniOku;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        String paket = e.getPackageName().toString();
        if (!paket.startsWith("com.whatsapp")) return;
        sonPaket = paket;
        // Kaydırma/yazma sırasında yüzlerce olay gelir: 1 sn durulunca bir kez oku.
        ana.removeCallbacks(oku);
        ana.postDelayed(oku, 1000);
    }

    @Override
    public void onInterrupt() { }

    private void ekraniOku() {
        if (!Ayarlar.aktif(this) || !Ayarlar.ekran(this)) return;
        AccessibilityNodeInfo kok;
        try {
            kok = getRootInActiveWindow();
        } catch (Exception e) {
            return;
        }
        if (kok == null) return;
        try {
            boolean yeni;
            String baslik = ilkYazi(kok, "conversation_contact_name");
            if (baslik != null) yeni = sohbetiOku(kok, baslik);
            else yeni = listeyiOku(kok);
            if (yeni) Gorevlendir.ekraniYenile();
        } catch (Exception ignored) {
            // WhatsApp ekran yapısı değişirse sessizce geç.
        }
    }

    // ------------------------------------------------------------------ açık sohbet

    private boolean sohbetiOku(AccessibilityNodeInfo kok, String sohbet) {
        boolean yeni = false;
        long simdi = System.currentTimeMillis();
        Db db = Db.get(this);
        db.kisiGor(sohbet);

        for (AccessibilityNodeInfo m : idIle(kok, "message_text")) {
            String metin = yazi(m);
            if (metin == null) continue;

            AccessibilityNodeInfo balon = balon(m);
            // ✓✓ = benim gönderdiğim: normalde atla; ama kendi not sohbetinde ya da
            // "#yap" gibi bir kalıpla başlıyorsa kendi mesajım da görev olabilsin.
            boolean benimMesajim = balon != null && icindeVar(balon, "status", 4);
            boolean kendiNot = Ayarlar.kendiSohbetMi(this, sohbet)
                    || Ayarlar.tetikSoyulmus(this, metin) != null;
            if (benimMesajim && !kendiNot) continue;

            String kimden = balon != null ? ilkYazi(balon, "name_in_group_tv") : null;
            if (kimden == null && balon != null) kimden = ilkYazi(balon, "name_in_group");
            if (kimden == null) kimden = sohbet;
            else db.kisiGor(kimden);

            String saat = balon != null ? ilkYazi(balon, "date") : null;
            // Alıntılı / yanıtlı mesajlar genelde önemlidir → öncelikli (yıldızlı) ekle.
            boolean alinti = balon != null && (icindeVar(balon, "quoted_message", 4)
                    || icindeVar(balon, "quoted_text_holder", 4)
                    || icindeVar(balon, "conversation_row_quoted", 4));
            String imza = "ekran|" + sohbet + "|" + kimden + "|" + saat + "|" + metin;
            yeni |= Gorevlendir.dene(this, kimden, sohbet, metin, simdi, imza, null, true, alinti);
        }
        return yeni;
    }

    /** Mesaj yazısının içinde bulunduğu balonu (satırı) bulur. */
    private static AccessibilityNodeInfo balon(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo p = n.getParent();
        for (int i = 0; i < 3 && p != null; i++) {
            if (icindeVar(p, "date", 3)) return p;
            p = p.getParent();
        }
        return n.getParent();
    }

    // ------------------------------------------------------------------ sohbet listesi

    private boolean listeyiOku(AccessibilityNodeInfo kok) {
        boolean yeni = false;
        long simdi = System.currentTimeMillis();
        List<AccessibilityNodeInfo> adlar = idIle(kok, "conversations_row_contact_name");
        Db db = Db.get(this);

        for (AccessibilityNodeInfo adNode : adlar) {
            String sohbet = yazi(adNode);
            if (sohbet == null) continue;
            AccessibilityNodeInfo satir = adNode.getParent();
            for (int i = 0; i < 2 && satir != null && !icindeVar(satir, "single_msg_tv", 4); i++) {
                satir = satir.getParent();
            }
            if (satir == null) continue;

            String onizleme = ilkYazi(satir, "single_msg_tv");
            if (onizleme == null) continue;
            if (icindeVar(satir, "status_indicator", 4) || icindeVar(satir, "msg_from_tv_status", 4)) continue;

            // Grup önizlemesi "Ahmet: mesaj" biçimindedir.
            String kimden = sohbet;
            String metin = onizleme;
            String grupKisi = ilkYazi(satir, "msg_from_tv");
            if (grupKisi != null) {
                kimden = grupKisi.replaceFirst(":\\s*$", "").trim();
            } else {
                int iki = onizleme.indexOf(": ");
                if (iki > 0 && iki < 40) {
                    kimden = onizleme.substring(0, iki).trim();
                    metin = onizleme.substring(iki + 2).trim();
                }
            }
            if (kimden.equalsIgnoreCase("Siz") || kimden.equalsIgnoreCase("You")) continue;

            db.kisiGor(sohbet);
            if (!kimden.equals(sohbet)) db.kisiGor(kimden);

            String imza = "liste|" + sohbet + "|" + kimden + "|" + metin;
            yeni |= Gorevlendir.dene(this, kimden, sohbet, metin, simdi, imza, null, true);
        }
        return yeni;
    }

    // ------------------------------------------------------------------ yardımcılar

    private List<AccessibilityNodeInfo> idIle(AccessibilityNodeInfo kok, String id) {
        List<AccessibilityNodeInfo> l = kok.findAccessibilityNodeInfosByViewId(sonPaket + ":id/" + id);
        return l != null ? l : new ArrayList<>();
    }

    private String ilkYazi(AccessibilityNodeInfo kok, String id) {
        for (AccessibilityNodeInfo n : idIle(kok, id)) {
            String y = yazi(n);
            if (y != null) return y;
        }
        return null;
    }

    /** Düğümün alt ağacında (derinlik sınırlı) verilen kimlikte bir öğe var mı? */
    private static boolean icindeVar(AccessibilityNodeInfo n, String id, int derinlik) {
        if (n == null || derinlik < 0) return false;
        String vid = n.getViewIdResourceName();
        if (vid != null && vid.endsWith(":id/" + id)) return true;
        for (int i = 0; i < n.getChildCount(); i++) {
            if (icindeVar(n.getChild(i), id, derinlik - 1)) return true;
        }
        return false;
    }

    private static String yazi(AccessibilityNodeInfo n) {
        if (n == null || n.getText() == null) return null;
        String s = n.getText().toString().trim();
        return s.isEmpty() ? null : s;
    }
}
