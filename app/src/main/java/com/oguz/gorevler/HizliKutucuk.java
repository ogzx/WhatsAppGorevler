package com.oguz.gorevler;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/**
 * Hızlı Ayarlar kutucuğu: bildirim panelinden dokununca panodaki metni görev yapar.
 * Pano boşsa hızlı ekleme penceresini açar.
 */
public class HizliKutucuk extends TileService {

    @Override
    public void onClick() {
        super.onClick();
        String pano = panoMetni();
        if (pano != null && !pano.trim().isEmpty()) {
            String t = pano.trim();
            long simdi = System.currentTimeMillis();
            long son = Ayarlar.tarihYakala(this) ? Tarih.bul(t, simdi, Ayarlar.basSaat(this)) : 0;
            Db.get(this).ekleId("Pano", null, t, simdi, "", "pano|" + simdi, null, son, null);
            Hatirlatici.sonTarihleriPlanla(this);
            Gorevlendir.ekraniYenile();
            GorevWidget.hepsiniYenile(this);
            Toast.makeText(this, "Panodaki metin görev yapıldı", Toast.LENGTH_SHORT).show();
        } else {
            Intent i = new Intent(this, HizliEkleActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(i);
        }
    }

    private String panoMetni() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                    && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                return cs == null ? null : cs.toString();
            }
        } catch (Exception ignored) { }
        return null;
    }
}
