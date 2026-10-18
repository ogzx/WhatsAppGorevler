package com.oguz.gorevler;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * PDF / e-fatura okuma: paylaşılan PDF'i sayfa sayfa görüntüye çevirip telefonda OCR ile okur,
 * tutar/tarihi çıkarıp görev yapar. İnternet yok; PDF dışarı gönderilmez.
 */
public class PdfActivity extends Activity {

    private static final int MAX_SAYFA = 3;

    private TextView durum;
    private EditText metin;
    private Button ekle;
    private int kalan;
    private final StringBuilder toplam = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView kaydir = new ScrollView(this);
        LinearLayout kok = new LinearLayout(this);
        kok.setOrientation(LinearLayout.VERTICAL);
        kok.setPadding(dp(16), dp(16), dp(16), dp(16));
        kaydir.addView(kok);

        durum = new TextView(this);
        durum.setTextColor(Color.parseColor("#777777"));
        kok.addView(durum);

        metin = new EditText(this);
        metin.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        metin.setMinLines(5);
        metin.setGravity(Gravity.TOP | Gravity.START);
        metin.setVisibility(View.GONE);
        kok.addView(metin);

        ekle = new Button(this);
        ekle.setText("Görev olarak ekle");
        ekle.setVisibility(View.GONE);
        ekle.setOnClickListener(v -> kaydet());
        kok.addView(ekle);

        Button vazgec = new Button(this);
        vazgec.setText("Vazgeç");
        vazgec.setOnClickListener(v -> finish());
        kok.addView(vazgec);

        setContentView(kaydir);

        Uri u = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        if (u == null && getIntent().getData() != null) u = getIntent().getData();
        if (u == null) {
            Toast.makeText(this, "PDF alınamadı", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        oku(u);
    }

    private void oku(Uri u) {
        durum.setText("PDF okunuyor…");
        File kopya;
        try {
            kopya = cacheKopya(u);
        } catch (Exception e) {
            durum.setText("PDF açılamadı.");
            return;
        }
        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                    kopya, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(pfd);
            int sayfa = Math.min(renderer.getPageCount(), MAX_SAYFA);
            if (sayfa == 0) { durum.setText("PDF boş."); renderer.close(); pfd.close(); return; }
            kalan = sayfa;
            for (int i = 0; i < sayfa; i++) {
                PdfRenderer.Page p = renderer.openPage(i);
                int en = Math.max(1, p.getWidth() * 2);
                int boy = Math.max(1, p.getHeight() * 2);
                // Çok büyük sayfaları sınırla
                if ((long) en * boy > 8_000_000L) { en /= 2; boy /= 2; }
                Bitmap bmp = Bitmap.createBitmap(en, boy, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                p.close();
                Ocr.oku(bmp, okunan -> {
                    if (okunan != null) toplam.append(okunan).append('\n');
                    if (--kalan == 0) {
                        rendererKapat(renderer, pfd);
                        goster();
                    }
                });
            }
        } catch (Exception e) {
            durum.setText("PDF işlenemedi: " + e.getMessage());
        }
    }

    private void rendererKapat(PdfRenderer r, ParcelFileDescriptor pfd) {
        try { r.close(); } catch (Exception ignored) { }
        try { pfd.close(); } catch (Exception ignored) { }
    }

    private void goster() {
        if (isFinishing()) return;
        String t = toplam.toString().trim();
        if (t.isEmpty()) {
            durum.setText("PDF'te yazı okunamadı (taranmış/boş olabilir). Kendiniz yazabilirsiniz:");
            metin.setText("");
        } else {
            if (t.length() > 2000) t = t.substring(0, 2000) + "…";
            durum.setText("Okunan içerik (düzenleyebilirsiniz):");
            metin.setText(t);
        }
        metin.setVisibility(View.VISIBLE);
        ekle.setVisibility(View.VISIBLE);
    }

    private void kaydet() {
        String t = metin.getText().toString().trim();
        if (t.isEmpty()) { Toast.makeText(this, "Metin boş", Toast.LENGTH_SHORT).show(); return; }
        long simdi = System.currentTimeMillis();
        long son = Ayarlar.tarihYakala(this) ? Tarih.bul(t, simdi, Ayarlar.basSaat(this)) : 0;
        String es = Ayarlar.eslesen(this, t);
        long id = Db.get(this).ekleId("PDF", null, "📄 " + t, simdi,
                es != null ? es : "", "pdf|" + simdi, null, son, null);
        String tutar = Tutar.bul(t);
        if (tutar != null) Db.get(this).setTutar(id, tutar);
        Hatirlatici.sonTarihleriPlanla(this);
        GorevWidget.hepsiniYenile(this);
        Runnable r = MainActivity.degisti;
        if (r != null) r.run();
        Toast.makeText(this, "Görev eklendi", Toast.LENGTH_SHORT).show();
        finish();
    }

    private File cacheKopya(Uri u) throws Exception {
        File f = new File(getCacheDir(), "paylasilan.pdf");
        try (InputStream is = getContentResolver().openInputStream(u);
             OutputStream os = new FileOutputStream(f)) {
            if (is == null) throw new Exception("açılamadı");
            byte[] tampon = new byte[8192];
            int n;
            while ((n = is.read(tampon)) > 0) os.write(tampon, 0, n);
        }
        return f;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
