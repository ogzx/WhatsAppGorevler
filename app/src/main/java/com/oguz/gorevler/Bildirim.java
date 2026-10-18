package com.oguz.gorevler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;

import java.util.List;

/** Telefonun kendi bildirimleri: saatlik liste ve yeni görev uyarısı. */
public final class Bildirim {

    static final String KANAL_LISTE = "liste";
    static final String KANAL_YENI = "yeni";
    static final String KANAL_SON = "sontarih";
    private static final int ID_LISTE = 1001;

    private Bildirim() {}

    static void kanallar(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                KANAL_LISTE, "Saatlik yapılacaklar listesi", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(
                KANAL_YENI, "Yeni görev", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(
                KANAL_SON, "Son tarih hatırlatması", NotificationManager.IMPORTANCE_HIGH));
    }

    /** Bir görevin son tarihi geldiğinde gösterilen hatırlatma (Tamamla / Ertele düğmeleriyle). */
    static void sonTarih(Context c, Db.Gorev g) {
        String baslik = "Son tarih" + (g.firma != null ? " · " + g.firma : "");

        PendingIntent tamamla = PendingIntent.getBroadcast(c, (int) (300000 + g.id),
                new Intent(c, GorevAksiyonReceiver.class).setAction(GorevAksiyonReceiver.TAMAMLA)
                        .putExtra(GorevAksiyonReceiver.EXTRA_ID, g.id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent ertele = PendingIntent.getBroadcast(c, (int) (400000 + g.id),
                new Intent(c, GorevAksiyonReceiver.class).setAction(GorevAksiyonReceiver.ERTELE)
                        .putExtra(GorevAksiyonReceiver.EXTRA_ID, g.id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Icon ikon = Icon.createWithResource(c, R.drawable.ic_bildirim);

        Notification n = new Notification.Builder(c, KANAL_SON)
                .setSmallIcon(R.drawable.ic_bildirim)
                .setContentTitle(baslik)
                .setContentText(tekSatir(g.metin, 120))
                .setStyle(new Notification.BigTextStyle().bigText(g.metin))
                .setContentIntent(uygulamayiAc(c))
                .addAction(new Notification.Action.Builder(ikon, "Tamamla", tamamla).build())
                .addAction(new Notification.Action.Builder(ikon, "Ertele (1 gün)", ertele).build())
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class)
                .notify((int) (2_000_000 + g.id), n);
    }

    /** Açık görevlerin düz metin listesi; görev yoksa null. */
    static String listeMetni(Context c) {
        List<Db.Gorev> acik = Db.get(c).liste(false);
        if (acik.isEmpty()) return null;
        StringBuilder b = new StringBuilder("*YAPILACAKLAR (" + acik.size() + ")*\n\n");
        for (Db.Gorev g : acik) {
            b.append("☐ ").append(tekSatir(g.metin, 160));
            if (g.kimden != null) b.append("  _(").append(g.kimden).append(")_");
            b.append('\n');
        }
        return b.toString();
    }

    /**
     * Listeyi WhatsApp'ta açar. Kendi numara ayarlıysa doğrudan "kendime mesaj" sohbeti açılır,
     * metin hazır gelir; gönder tuşuna basmak yeterlidir.
     */
    static Intent whatsappIntent(Context c, String metin) {
        String num = Ayarlar.numara(c).replaceAll("[^0-9]", "");
        Intent i;
        if (!num.isEmpty()) {
            i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/" + num + "?text=" + Uri.encode(metin)));
        } else {
            i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, metin);
        }
        i.setPackage("com.whatsapp");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** Saatlik bildirim: açık görevler + "WhatsApp'a gönder" düğmesi. */
    static void liste(Context c) {
        List<Db.Gorev> acik = Db.get(c).liste(false);
        if (acik.isEmpty()) return;

        Notification.InboxStyle stil = new Notification.InboxStyle();
        int gosterilen = Math.min(acik.size(), 7);
        for (int i = 0; i < gosterilen; i++) {
            stil.addLine("☐ " + tekSatir(acik.get(i).metin, 80));
        }
        if (acik.size() > gosterilen) stil.setSummaryText("+" + (acik.size() - gosterilen) + " görev daha");

        PendingIntent wa = PendingIntent.getActivity(c, 2,
                whatsappIntent(c, listeMetni(c)),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(c, KANAL_LISTE)
                .setSmallIcon(R.drawable.ic_bildirim)
                .setContentTitle("Yapılacaklar: " + acik.size() + " görev")
                .setContentText(tekSatir(acik.get(0).metin, 80))
                .setStyle(stil)
                .setContentIntent(uygulamayiAc(c))
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(c, R.drawable.ic_bildirim), "WhatsApp'a gönder", wa).build())
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify(ID_LISTE, n);
    }

    static void yeniGorev(Context c, String kimden, String metin) {
        Notification n = new Notification.Builder(c, KANAL_YENI)
                .setSmallIcon(R.drawable.ic_bildirim)
                .setContentTitle("Yeni görev" + (kimden != null ? " · " + kimden : ""))
                .setContentText(tekSatir(metin, 100))
                .setStyle(new Notification.BigTextStyle().bigText(metin))
                .setContentIntent(uygulamayiAc(c))
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class)
                .notify((int) (System.currentTimeMillis() & 0x7fffffff), n);
    }

    private static PendingIntent uygulamayiAc(Context c) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static String tekSatir(String s, int sinir) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').trim();
        return t.length() > sinir ? t.substring(0, sinir) + "…" : t;
    }
}
