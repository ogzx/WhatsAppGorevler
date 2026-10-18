package com.oguz.gorevler;

import android.app.Notification;
import android.app.Person;
import android.content.ComponentName;
import android.content.ContentUris;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * WhatsApp'tan gelen bildirimleri telefonun içinde okur; anahtar kelime geçen ya da
 * önemli kişilerden gelen mesajları ve fotoğraflardaki yazıları görev olarak kaydeder.
 * Hiçbir veri dışarı gönderilmez.
 */
public class WaListenerService extends NotificationListenerService {

    private static final Set<String> PAKETLER =
            new HashSet<>(Arrays.asList("com.whatsapp", "com.whatsapp.w4b"));

    /** "5 yeni mesaj", "3 sohbetten 7 mesaj" gibi özet satırlarını atla. */
    private static final Pattern OZET = Pattern.compile(
            "^\\d+\\s+(yeni\\s+)?(mesaj|sohbet|new message|message)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final long GUN = 24L * 60 * 60 * 1000;
    /** Fotoğraf bildirimi ile galeriye düşen dosyayı eşleştirme süresi. */
    private static final long FOTO_PENCERE = 10L * 60 * 1000;
    private static final int METIN_SINIRI = 1000;

    /** Bildirimi gelmiş ama dosyası henüz okunmamış fotoğraf. */
    private static class Bekleyen {
        final String kimden, sohbet, aciklama;
        final long zaman;
        Bekleyen(String kimden, String sohbet, long zaman, String aciklama) {
            this.kimden = kimden;
            this.sohbet = sohbet;
            this.zaman = zaman;
            this.aciklama = aciklama;
        }
    }

    private final Handler ana = new Handler(Looper.getMainLooper());
    private final LinkedList<Bekleyen> bekleyenler = new LinkedList<>();
    private final Set<String> islenenFotolar = new HashSet<>();
    private final Runnable tara = this::taraHepsi;
    private long sonSs; // ekran görüntüsü tarama su işareti (saniye)

    private void taraHepsi() {
        medyaTara();
        ekranGoruntusuTara();
    }
    private ContentObserver gozlemci;
    private long sonMedya; // saniye (MediaStore DATE_ADDED birimi)

    // ------------------------------------------------------------------ yaşam döngüsü

    @Override
    public void onListenerConnected() {
        gozlemciKur();
    }

    @Override
    public void onListenerDisconnected() {
        gozlemciKaldir();
        // Samsung arka planda kapatırsa yeniden bağlanmayı iste.
        requestRebind(new ComponentName(this, WaListenerService.class));
    }

    @Override
    public void onDestroy() {
        gozlemciKaldir();
        super.onDestroy();
    }

    /** Galeriye yeni WhatsApp fotoğrafı düştüğünde haber alır. */
    private void gozlemciKur() {
        if (gozlemci != null || Build.VERSION.SDK_INT < 29 || !Ocr.izinVar(this)) return;
        sonMedya = System.currentTimeMillis() / 1000 - 5;
        sonSs = System.currentTimeMillis() / 1000 - 5;
        gozlemci = new ContentObserver(ana) {
            @Override
            public void onChange(boolean kendi) {
                ana.removeCallbacks(tara);
                ana.postDelayed(tara, 2500); // dosyanın yazılması bitsin
            }
        };
        try {
            getContentResolver().registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, gozlemci);
        } catch (Exception e) {
            gozlemci = null;
        }
    }

    private void gozlemciKaldir() {
        if (gozlemci != null) {
            try { getContentResolver().unregisterContentObserver(gozlemci); } catch (Exception ignored) { }
            gozlemci = null;
        }
        ana.removeCallbacks(tara);
    }

    // ------------------------------------------------------------------ bildirimler

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            isle(sbn);
        } catch (Exception ignored) {
            // Beklenmeyen bildirim biçimi: sessizce geç, uygulama çökmesin.
        }
    }

    private void isle(StatusBarNotification sbn) {
        if (sbn == null || !PAKETLER.contains(sbn.getPackageName())) return;
        if (!Ayarlar.aktif(this)) return;

        Notification n = sbn.getNotification();
        if (n == null) return;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return; // "mesajlar kontrol ediliyor" vb.

        Bundle ex = n.extras;
        if (ex == null) return;

        gozlemciKur(); // izin sonradan verildiyse şimdi başlasın

        String baslik = yazi(ex.getCharSequence(Notification.EXTRA_TITLE));
        String sohbet = yazi(ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        if (sohbet == null) sohbet = baslik;

        Db db = Db.get(this);
        boolean yeni = false;
        Parcelable[] mesajlar = ex.getParcelableArray(Notification.EXTRA_MESSAGES);

        if (mesajlar != null && mesajlar.length > 0) {
            for (Parcelable p : mesajlar) {
                if (!(p instanceof Bundle)) continue;
                Bundle b = (Bundle) p;
                String metin = yazi(b.getCharSequence("text"));
                long zaman = b.getLong("time", sbn.getPostTime());

                String kimden = null;
                if (Build.VERSION.SDK_INT >= 28) {
                    Person kisi = b.getParcelable("sender_person");
                    if (kisi != null) kimden = yazi(kisi.getName());
                }
                if (kimden == null) kimden = yazi(b.getCharSequence("sender"));
                if (kimden == null) kimden = sohbet;

                db.kisiGor(kimden);
                if (sohbet != null && !sohbet.equals(kimden)) db.kisiGor(sohbet);

                String tip = b.getString("type");
                boolean foto = (tip != null && tip.startsWith("image/"))
                        || (metin != null && metin.startsWith("📷"));
                if (foto && Ayarlar.foto(this)) {
                    Uri uri = b.getParcelable("uri");
                    fotoMesaji(kimden, sohbet, zaman, aciklama(metin), uri);
                    continue;
                }

                String imza = sohbet + "|" + kimden + "|" + zaman + "|" + metin;
                yeni |= kaydet(kimden, sohbet, metin, zaman, imza, null);
            }
        } else {
            String metin = yazi(ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (metin == null) metin = yazi(ex.getCharSequence(Notification.EXTRA_TEXT));
            long zaman = sbn.getPostTime();
            db.kisiGor(baslik);

            if (metin != null && metin.startsWith("📷") && Ayarlar.foto(this)) {
                fotoMesaji(baslik, sohbet, zaman, aciklama(metin), null);
            } else {
                String imza = sohbet + "|" + baslik + "|" + (zaman / GUN) + "|" + metin;
                yeni |= kaydet(baslik, sohbet, metin, zaman, imza, null);
            }
        }

        if (yeni) haberVer();
    }

    // ------------------------------------------------------------------ fotoğraflar

    private void fotoMesaji(String kimden, String sohbet, long zaman, String aciklama, Uri uri) {
        String anahtar = sohbet + "|" + kimden + "|" + zaman;
        if (!islenenFotolar.add(anahtar)) return; // bildirim tekrar gösterildi
        if (islenenFotolar.size() > 500) islenenFotolar.clear();
        if (!Ayarlar.kisiUygun(this, kimden, sohbet)) return;

        Bekleyen bk = new Bekleyen(kimden, sohbet, zaman, aciklama);
        if (uri != null) {
            // Önce bildirimdeki resmi doğrudan okumayı dene.
            Ocr.oku(this, uri, okunan -> {
                if (okunan == null) beklemeyeAl(bk); // erişilemedi: galeriye düşünce okunacak
                else fotoKaydet(bk, okunan, null);
            });
        } else {
            beklemeyeAl(bk);
        }
    }

    private void beklemeyeAl(Bekleyen bk) {
        synchronized (bekleyenler) {
            bekleyenler.add(bk);
        }
    }

    /** Süresi dolmamış en eski bekleyen fotoğrafı verir. */
    private Bekleyen siradakiBekleyen() {
        long simdi = System.currentTimeMillis();
        synchronized (bekleyenler) {
            Iterator<Bekleyen> it = bekleyenler.iterator();
            while (it.hasNext()) {
                Bekleyen b = it.next();
                it.remove();
                if (simdi - b.zaman <= FOTO_PENCERE) return b;
            }
        }
        return null;
    }

    /** Galeriye yeni düşen WhatsApp fotoğraflarını okur. */
    private void medyaTara() {
        if (Build.VERSION.SDK_INT < 29 || !Ayarlar.aktif(this) || !Ayarlar.foto(this)
                || !Ocr.izinVar(this)) return;

        String[] alanlar = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.RELATIVE_PATH
        };
        String secim = MediaStore.MediaColumns.DATE_ADDED + " > ? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + " NOT LIKE ?";
        String[] degerler = {String.valueOf(sonMedya), "%WhatsApp%Images%", "%/Sent/%"};

        try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                alanlar, secim, degerler, MediaStore.MediaColumns.DATE_ADDED + " ASC")) {
            if (c == null) return;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                long eklendi = c.getLong(1);
                if (eklendi > sonMedya) sonMedya = eklendi;

                Uri dosya = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                Bekleyen bk = siradakiBekleyen();
                if (bk == null) {
                    // Kimden geldiği bilinmiyor (ör. sessize alınmış sohbet).
                    if (Ayarlar.sadeceOnemli(this)) continue;
                    bk = new Bekleyen("WhatsApp fotoğrafı", null, eklendi * 1000, null);
                }
                final Bekleyen f = bk;
                final String fotoUri = dosya.toString();
                Ocr.oku(this, dosya, okunan -> fotoKaydet(f, okunan, fotoUri));
            }
        } catch (Exception ignored) { }
    }

    /** Ekran görüntüleri klasörüne düşen yeni görüntüleri okur (ayrı su işareti). */
    private void ekranGoruntusuTara() {
        if (Build.VERSION.SDK_INT < 29 || !Ayarlar.aktif(this) || !Ayarlar.ekranGoruntusu(this)
                || !Ocr.izinVar(this)) return;

        String[] alanlar = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_ADDED
        };
        String secim = MediaStore.MediaColumns.DATE_ADDED + " > ? AND ("
                + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? OR "
                + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?)";
        String[] degerler = {String.valueOf(sonSs), "%Screenshots%", "%Ekran%"};

        try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                alanlar, secim, degerler, MediaStore.MediaColumns.DATE_ADDED + " ASC")) {
            if (c == null) return;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                long eklendi = c.getLong(1);
                if (eklendi > sonSs) sonSs = eklendi;
                Uri dosya = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                final long zaman = eklendi * 1000;
                final String fotoUri = dosya.toString();
                Ocr.oku(this, dosya, okunan -> ekranGoruntusuKaydet(okunan, zaman, fotoUri));
            }
        } catch (Exception ignored) { }
    }

    /** Ekran görüntüsündeki yazı kelime/kişi kuralına uyuyorsa görev yapar. */
    private void ekranGoruntusuKaydet(String okunan, long zaman, String fotoUri) {
        if (okunan == null || okunan.trim().isEmpty()) return;
        String t = okunan.trim();
        if (t.length() > METIN_SINIRI) t = t.substring(0, METIN_SINIRI) + "…";
        // Ekran görüntüsünün kimden geldiği bilinmez: yalnızca kelime kuralıyla değerlendir.
        if (Ayarlar.eslesen(this, t) == null && !Ayarlar.hepsi(this)) return;
        if (Gorevlendir.dene(this, "Ekran görüntüsü", null, t, zaman, "ss|" + fotoUri, fotoUri, false)) {
            haberVer();
        }
    }

    private void fotoKaydet(Bekleyen bk, String okunan, String fotoUri) {
        StringBuilder m = new StringBuilder();
        if (bk.aciklama != null) m.append(bk.aciklama);
        if (okunan != null && !okunan.trim().isEmpty()) {
            String t = okunan.trim();
            if (t.length() > METIN_SINIRI) t = t.substring(0, METIN_SINIRI) + "…";
            if (m.length() > 0) m.append('\n');
            m.append(t);
        }
        String metin = m.length() > 0 ? m.toString() : "(fotoğrafta yazı bulunamadı)";
        String imza = "foto|" + (fotoUri != null ? fotoUri : bk.sohbet + "|" + bk.kimden + "|" + bk.zaman);
        if (kaydet(bk.kimden, bk.sohbet, "📷 " + metin, bk.zaman, imza, fotoUri)) haberVer();
    }

    // ------------------------------------------------------------------ ortak

    private boolean kaydet(String kimden, String sohbet, String metin, long zaman,
                           String imza, String foto) {
        // Ekran okuma aynı mesajı daha önce yakaladıysa tekrar ekleme.
        return Gorevlendir.dene(this, kimden, sohbet, metin, zaman, imza, foto, foto == null);
    }

    private void haberVer() {
        Runnable r = MainActivity.degisti;
        if (r != null) ana.post(r);
    }

    /** "📷 Fotoğraf" gibi kalıp yazıları atar, varsa fotoğraf açıklamasını döndürür. */
    private static String aciklama(String metin) {
        if (metin == null) return null;
        String t = metin.replaceFirst("^📷\\s*", "").trim();
        if (t.equalsIgnoreCase("Fotoğraf") || t.equalsIgnoreCase("Photo")) return null;
        return t.isEmpty() ? null : t;
    }

    private static String yazi(CharSequence cs) {
        if (cs == null) return null;
        String s = cs.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
