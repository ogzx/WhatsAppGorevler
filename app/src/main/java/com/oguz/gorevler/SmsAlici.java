package com.oguz.gorevler;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gelen SMS'leri telefonun içinde okur; anahtar kelime geçenleri görev yapar.
 * Banka/GİB/SGK ödeme ve son tarih SMS'leri için. İnternet yok; SMS dışarı gönderilmez.
 */
public class SmsAlici extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Ayarlar.aktif(c) || !Ayarlar.sms(c)) return;
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        Bundle b = intent.getExtras();
        if (b == null) return;
        Object[] pdus = (Object[]) b.get("pdus");
        if (pdus == null) return;
        String format = b.getString("format");

        // Çok parçalı SMS'leri gönderene göre birleştir.
        Map<String, StringBuilder> parcali = new LinkedHashMap<>();
        long zaman = System.currentTimeMillis();
        for (Object pdu : pdus) {
            SmsMessage m;
            try {
                m = format != null
                        ? SmsMessage.createFromPdu((byte[]) pdu, format)
                        : SmsMessage.createFromPdu((byte[]) pdu);
            } catch (Exception e) { continue; }
            if (m == null) continue;
            String gonderen = m.getOriginatingAddress();
            String govde = m.getMessageBody();
            if (govde == null) continue;
            parcali.computeIfAbsent(gonderen == null ? "SMS" : gonderen,
                    k -> new StringBuilder()).append(govde);
            zaman = m.getTimestampMillis();
        }

        for (Map.Entry<String, StringBuilder> e : parcali.entrySet()) {
            isle(c, e.getKey(), e.getValue().toString(), zaman);
        }
        GorevWidget.hepsiniYenile(c);
    }

    private static void isle(Context c, String gonderen, String govde, long zaman) {
        String metin = govde == null ? "" : govde.trim();
        if (metin.isEmpty()) return;
        String ad = "SMS: " + (gonderen == null ? "?" : gonderen);
        Gorevlendir.dene(c, ad, ad, metin, zaman,
                "sms|" + gonderen + "|" + zaman + "|" + metin.hashCode(), null, false);
    }

    static boolean izinVar(Context c) {
        return c.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean okumaIzniVar(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Gelen kutusundaki son SMS'leri tarar (READ_SMS izni gerekir). Eklenen görev sayısını döner. */
    static int sonlariTara(Context c, int adet) {
        if (!okumaIzniVar(c)) return -1;
        int eklenen = 0;
        try (Cursor cur = c.getContentResolver().query(
                Uri.parse("content://sms/inbox"),
                new String[]{"address", "body", "date"},
                null, null, "date DESC")) {
            if (cur == null) return 0;
            int i = 0;
            while (cur.moveToNext() && i < adet) {
                i++;
                String adres = cur.getString(0);
                String govde = cur.getString(1);
                long zaman = cur.getLong(2);
                int once = Db.get(c).sayi(false);
                isle(c, adres, govde, zaman);
                if (Db.get(c).sayi(false) > once) eklenen++;
            }
        } catch (Exception e) {
            return eklenen;
        }
        GorevWidget.hepsiniYenile(c);
        return eklenen;
    }
}
