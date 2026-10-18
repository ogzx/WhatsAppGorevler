package com.oguz.gorevler;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Fotoğraftan görev: herhangi bir uygulamadan (WhatsApp, Galeri...) "Paylaş > WA Görevler"
 * ile gelen ya da uygulama içinden seçilen fotoğraftaki yazıyı okur.
 */
public class FotoActivity extends Activity {

    private static final int SEC = 1;

    private TextView durum;
    private EditText metin;
    private Button ekle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView kaydir = new ScrollView(this);
        LinearLayout kok = new LinearLayout(this);
        kok.setOrientation(LinearLayout.VERTICAL);
        kok.setPadding(dp(16), dp(16), dp(16), dp(16));
        kaydir.addView(kok);

        durum = new TextView(this);
        durum.setTextColor(Color.parseColor("#555555"));
        kok.addView(durum);

        metin = new EditText(this);
        metin.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        metin.setMinLines(6);
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

        Intent i = getIntent();
        if (Intent.ACTION_SEND.equals(i.getAction())) {
            Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u == null) {
                Toast.makeText(this, "Fotoğraf alınamadı", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            oku(u);
        } else {
            durum.setText("Fotoğraf seçin…");
            Intent sec = new Intent(Intent.ACTION_GET_CONTENT);
            sec.setType("image/*");
            startActivityForResult(sec, SEC);
        }
    }

    @Override
    protected void onActivityResult(int istek, int sonuc, Intent veri) {
        super.onActivityResult(istek, sonuc, veri);
        if (istek != SEC) return;
        if (sonuc == RESULT_OK && veri != null && veri.getData() != null) oku(veri.getData());
        else finish();
    }

    private void oku(Uri u) {
        durum.setText("Fotoğraftaki yazı okunuyor…");
        Ocr.oku(this, u, yazi -> {
            if (isFinishing()) return;
            if (yazi == null || yazi.trim().isEmpty()) {
                durum.setText("Fotoğrafta yazı bulunamadı. Görevi kendiniz yazabilirsiniz:");
                metin.setText("");
            } else {
                durum.setText("Okunan yazı (düzenleyip kısaltabilirsiniz):");
                metin.setText(yazi.trim());
            }
            metin.setVisibility(View.VISIBLE);
            ekle.setVisibility(View.VISIBLE);
        });
    }

    private void kaydet() {
        String t = metin.getText().toString().trim();
        if (t.isEmpty()) {
            Toast.makeText(this, "Görev metni boş", Toast.LENGTH_SHORT).show();
            return;
        }
        String eslesen = Ayarlar.eslesen(this, t);
        long simdi = System.currentTimeMillis();
        Db.get(this).ekle("Fotoğraf", null, "📷 " + t, simdi,
                eslesen != null ? eslesen : "", "paylas|" + simdi, null);
        Toast.makeText(this, "Görev eklendi", Toast.LENGTH_SHORT).show();
        Runnable r = MainActivity.degisti;
        if (r != null) r.run();
        finish();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
