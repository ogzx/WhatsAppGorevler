package com.oguz.gorevler;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

/** Küçük, hızlı görev ekleme penceresi (widget'taki + ve kısayollar için). */
public class HizliEkleActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Yeni görev");

        LinearLayout kok = new LinearLayout(this);
        kok.setOrientation(LinearLayout.VERTICAL);
        kok.setPadding(dp(20), dp(16), dp(20), dp(8));

        EditText e = new EditText(this);
        e.setHint("Görev…");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setMinLines(2);
        e.setGravity(Gravity.TOP | Gravity.START);
        kok.addView(e);

        LinearLayout butonlar = new LinearLayout(this);
        butonlar.setOrientation(LinearLayout.HORIZONTAL);
        Button vazgec = new Button(this);
        vazgec.setText("Vazgeç");
        vazgec.setOnClickListener(v -> finish());
        Button ekle = new Button(this);
        ekle.setText("Ekle");
        ekle.setOnClickListener(v -> {
            String t = e.getText().toString().trim();
            if (t.isEmpty()) { finish(); return; }
            long simdi = System.currentTimeMillis();
            long son = Ayarlar.tarihYakala(this) ? Tarih.bul(t, simdi, Ayarlar.basSaat(this)) : 0;
            Db.get(this).ekleId("Ben", null, t, simdi, "", "elle|" + simdi, null, son, null);
            Hatirlatici.sonTarihleriPlanla(this);
            Gorevlendir.ekraniYenile();
            GorevWidget.hepsiniYenile(this);
            Toast.makeText(this, "Görev eklendi", Toast.LENGTH_SHORT).show();
            finish();
        });
        butonlar.addView(vazgec, new LinearLayout.LayoutParams(0, -2, 1));
        butonlar.addView(ekle, new LinearLayout.LayoutParams(0, -2, 1));
        kok.addView(butonlar);

        FrameLayout sar = new FrameLayout(this);
        sar.addView(kok);
        setContentView(sar);
        e.requestFocus();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
