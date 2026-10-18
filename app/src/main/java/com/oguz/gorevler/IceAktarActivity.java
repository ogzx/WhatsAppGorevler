package com.oguz.gorevler;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * İki iş yapar:
 *  1) WhatsApp "Sohbeti dışa aktar" ile gelen .txt / .zip dosyasını tarar,
 *     kurallara uyan mesajları görev olarak ekler (eski mesajlar dahil).
 *  2) Herhangi bir uygulamadan paylaşılan düz metni görev olarak ekler.
 */
public class IceAktarActivity extends Activity {

    /** "18.10.2026 19:11 - Ahmet: mesaj"  veya  "[18.10.2026 19:11:05] Ahmet: mesaj" */
    private static final Pattern SATIR = Pattern.compile(
            "^\\u200E?\\[?(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4}),?\\s+(\\d{1,2}):(\\d{2})(?::\\d{2})?(?:[\\s\\u202F]?([AaPp])\\.?[Mm]\\.?)?\\]?\\s*[-–]?\\s*([^:]{1,60}?):\\s(.*)$");

    private static final long GUN = 24L * 60 * 60 * 1000;

    private static class Mesaj {
        long zaman;
        String kimden;
        StringBuilder metin;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        String eylem = i.getAction();

        List<Uri> dosyalar = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(eylem)) {
            Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) dosyalar.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(eylem)) {
            ArrayList<Uri> l = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (l != null) dosyalar.addAll(l);
        } else if (Intent.ACTION_VIEW.equals(eylem) && i.getData() != null) {
            dosyalar.add(i.getData());
        }

        if (!dosyalar.isEmpty()) {
            String konu = i.getStringExtra(Intent.EXTRA_SUBJECT);
            sohbetDosyasi(dosyalar, konu);
            return;
        }

        CharSequence metin = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (metin != null && metin.toString().trim().length() > 0) {
            metinGorevi(metin.toString().trim());
            return;
        }

        Toast.makeText(this, "Paylaşılan içerik okunamadı", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ------------------------------------------------------------------ düz metin

    private void metinGorevi(String metin) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setMinLines(3);
        e.setText(metin);
        FrameLayout f = new FrameLayout(this);
        int p = Math.round(20 * getResources().getDisplayMetrics().density);
        f.setPadding(p, p / 2, p, 0);
        f.addView(e);
        new AlertDialog.Builder(this)
                .setTitle("Görev olarak ekle")
                .setView(f)
                .setPositiveButton("Ekle", (d, w) -> {
                    String t = e.getText().toString().trim();
                    if (!t.isEmpty()) {
                        long simdi = System.currentTimeMillis();
                        String eslesen = Ayarlar.eslesen(this, t);
                        Db.get(this).ekle("Paylaşılan", null, t, simdi,
                                eslesen != null ? eslesen : "", "metin|" + simdi, null);
                        Toast.makeText(this, "Görev eklendi", Toast.LENGTH_SHORT).show();
                        Gorevlendir.ekraniYenile();
                    }
                    finish();
                })
                .setNegativeButton("Vazgeç", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    // ------------------------------------------------------------------ sohbet dışa aktarma

    private void sohbetDosyasi(List<Uri> dosyalar, String konu) {
        String yazi = null;
        String dosyaAdi = null;
        for (Uri u : dosyalar) {
            try {
                String ad = ad(u);
                String tip = getContentResolver().getType(u);
                boolean zip = (ad != null && ad.toLowerCase().endsWith(".zip"))
                        || (tip != null && tip.contains("zip"));
                String y = zip ? zipIcindenOku(u) : oku(getContentResolver().openInputStream(u));
                if (y != null && SATIR.matcher(ilkSatir(y)).find()) {
                    yazi = y;
                    dosyaAdi = ad;
                    break;
                }
            } catch (Exception ignored) { }
        }
        if (yazi == null) {
            Toast.makeText(this, "Bu dosya bir WhatsApp sohbet dökümü gibi görünmüyor",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String sohbet = sohbetAdi(konu, dosyaAdi);
        List<Mesaj> mesajlar = coz(yazi);
        final String sonYazi = sohbet;

        String[] secenek = {"Son 7 gün", "Son 30 gün", "Son 90 gün", "Tümü (" + mesajlar.size() + " mesaj)"};
        long[] gun = {7, 30, 90, -1};
        new AlertDialog.Builder(this)
                .setTitle((sohbet != null ? sohbet : "Sohbet") + " · hangi mesajlar taransın?")
                .setItems(secenek, (d, i) -> {
                    long sinir = gun[i] < 0 ? 0 : System.currentTimeMillis() - gun[i] * GUN;
                    int eklenen = isle(mesajlar, sonYazi, sinir);
                    Gorevlendir.ekraniYenile();
                    new AlertDialog.Builder(this)
                            .setMessage(eklenen > 0
                                    ? eklenen + " görev eklendi."
                                    : "Kurallara uyan yeni görev bulunamadı.\n\n"
                                      + "İpucu: Ayarlar'dan anahtar kelimeleri ya da önemli kişileri "
                                      + "kontrol edin.")
                            .setPositiveButton("Tamam", (d2, w) -> {
                                startActivity(new Intent(this, MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                                finish();
                            })
                            .setOnCancelListener(d2 -> finish())
                            .show();
                })
                .setOnCancelListener(d -> finish())
                .show();
    }

    private int isle(List<Mesaj> mesajlar, String sohbet, long sinir) {
        int eklenen = 0;
        Db db = Db.get(this);
        for (Mesaj m : mesajlar) {
            if (m.zaman < sinir) continue;
            String metin = m.metin.toString().trim();
            if (metin.contains("<Medya dahil edilmedi>") || metin.contains("<Media omitted>")
                    || metin.equals("Bu mesaj silindi") || metin.equals("This message was deleted")) continue;
            db.kisiGor(m.kimden);
            String s = sohbet != null ? sohbet : m.kimden;
            String imza = "aktar|" + s + "|" + m.zaman + "|" + m.kimden + "|" + metin;
            // Aynı mesaj bildirimle/ekrandan daha önce yakalandıysa ekleme.
            if (Gorevlendir.dene(this, m.kimden, s, metin, m.zaman, imza, null, true)) eklenen++;
        }
        return eklenen;
    }

    /** Döküm metnini mesajlara ayırır; çok satırlı mesajları birleştirir. */
    static List<Mesaj> coz(String yazi) {
        List<Mesaj> l = new ArrayList<>();
        Mesaj son = null;
        for (String satir : yazi.split("\\r?\\n")) {
            Matcher m = SATIR.matcher(satir);
            if (m.find()) {
                int g = Integer.parseInt(m.group(1));
                int a = Integer.parseInt(m.group(2));
                int y = Integer.parseInt(m.group(3));
                if (y < 100) y += 2000;
                if (a > 12 && g <= 12) { int t = g; g = a; a = t; } // ABD biçimi (ay/gün)
                Calendar k = Calendar.getInstance();
                k.clear();
                int saat = Integer.parseInt(m.group(4));
                String ogle = m.group(6); // ABD biçiminde "A" (AM) / "P" (PM)
                if (ogle != null) {
                    boolean os = ogle.equalsIgnoreCase("p");
                    if (os && saat < 12) saat += 12;
                    if (!os && saat == 12) saat = 0;
                }
                k.set(y, a - 1, g, saat, Integer.parseInt(m.group(5)));
                son = new Mesaj();
                son.zaman = k.getTimeInMillis();
                son.kimden = m.group(7).replace("‎", "").trim();
                son.metin = new StringBuilder(m.group(8));
                l.add(son);
            } else if (son != null && !satir.trim().isEmpty()) {
                son.metin.append('\n').append(satir); // önceki mesajın devamı
            }
        }
        return l;
    }

    private static String sohbetAdi(String konu, String dosyaAdi) {
        String[] onekler = {"WhatsApp Sohbeti - ", "WhatsApp Chat - ", "WhatsApp Chat with ",
                "WhatsApp Sohbeti: ", "WhatsApp sohbeti - "};
        for (String kaynak : new String[]{konu, dosyaAdi}) {
            if (kaynak == null) continue;
            String s = kaynak.replaceAll("(?i)\\.(txt|zip)$", "").trim();
            for (String o : onekler) {
                if (s.startsWith(o)) return s.substring(o.length()).trim();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ dosya okuma

    private String zipIcindenOku(Uri u) throws Exception {
        try (ZipInputStream z = new ZipInputStream(getContentResolver().openInputStream(u))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().toLowerCase().endsWith(".txt")) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] tampon = new byte[8192];
                    int n;
                    while ((n = z.read(tampon)) > 0) b.write(tampon, 0, n);
                    return b.toString("UTF-8");
                }
            }
        }
        return null;
    }

    private static String oku(InputStream in) throws Exception {
        if (in == null) return null;
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String s;
            while ((s = r.readLine()) != null) b.append(s).append('\n');
        }
        return b.toString();
    }

    private String ad(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return u.getLastPathSegment();
    }

    /** Sohbet dökümünün ilk mesaj satırı (baştaki şifreleme bilgi satırı dahil olabilir). */
    private static String ilkSatir(String y) {
        for (String s : y.split("\\r?\\n", 6)) {
            if (SATIR.matcher(s).find()) return s;
        }
        return "";
    }
}
