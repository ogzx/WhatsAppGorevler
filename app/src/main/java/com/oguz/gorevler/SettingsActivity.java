package com.oguz.gorevler;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends Activity {

    private LinearLayout kok;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        ScrollView kaydir = new ScrollView(this);
        kok = new LinearLayout(this);
        kok.setOrientation(LinearLayout.VERTICAL);
        kok.setPadding(dp(16), dp(8), dp(16), dp(32));
        kaydir.addView(kok);

        // ---------------- Genel
        baslik("Genel");
        Switch aktif = anahtar("WhatsApp mesajlarını dinle", Ayarlar.aktif(this));
        Switch hepsi = anahtar("Anahtar kelime aramadan TÜM mesajları ekle", Ayarlar.hepsi(this));

        // ---------------- Ekran okuma
        baslik("Ekran okuma (bildirimsiz mesajlar)");
        Switch ekran = anahtar("WhatsApp açıkken ekrandaki mesajları da oku", Ayarlar.ekran(this));
        aciklama("Sessize alınmış sohbetleri (sohbet listesindeki son mesaj) ve WhatsApp açıkken "
                + "gelen mesajları yakalar. Telefon ayarlarında açılması gerekir: Erişilebilirlik > "
                + "Yüklü uygulamalar > WA Görevler ekran okuma. Kilitliyse önce: Ayarlar > Uygulamalar > "
                + "WA Görevler > ⋮ > Kısıtlı ayarlara izin ver.\n"
                + "Durum: " + (MainActivity.ekranOkumaAcik(this) ? "AÇIK ✓" : "KAPALI"));
        Button ekranAc = dugme("Ekran okuma ayarını aç");
        ekranAc.setOnClickListener(v -> MainActivity.ekranOkumaAyarlariniAc(this));
        aciklama("WhatsApp'taki kendi adınız (isteğe bağlı). Yazarsanız sohbet içe aktarırken "
                + "kendi gönderdiğiniz mesajlar görev sayılmaz.");
        EditText benimAdim = new EditText(this);
        benimAdim.setSingleLine(true);
        benimAdim.setHint("Ör. Oğuz");
        benimAdim.setText(Ayarlar.benimAdim(this));
        kok.addView(benimAdim);

        // ---------------- Anahtar kelimeler
        baslik("Anahtar kelimeler");
        aciklama("Her satıra bir tane. Büyük/küçük harf ve ş/ç/ö/ü/ğ/ı farkı önemsizdir. "
                + "Kelimenin bir parçası da eşleşir: \"öde\" → \"ödeme\", \"ödedim\".");
        EditText kelimeler = cokSatir(Ayarlar.kelimelerMetin(this), 7);
        Button varsayilan = dugme("Varsayılan kelimelere dön");
        varsayilan.setOnClickListener(v -> kelimeler.setText(Ayarlar.VARSAYILAN_KELIMELER));

        // ---------------- Önemli kişiler
        baslik("Önemli kişiler / gruplar");
        aciklama("Adları WhatsApp bildiriminde göründüğü gibi yazın (her satıra bir tane) "
                + "ya da aşağıdaki düğmeyle mesaj gelenlerden seçin.");
        EditText onemliler = cokSatir(Ayarlar.onemliMetin(this), 4);
        Button kisiSec = dugme("Mesaj gelen kişilerden seç");
        kisiSec.setOnClickListener(v -> kisiSecDialog(onemliler));
        Switch onemliHepsi = anahtar("Önemli kişilerin HER mesajını görev say "
                + "(anahtar kelime aranmaz)", Ayarlar.onemliHepsi(this));
        Switch sadeceOnemli = anahtar("SADECE önemli kişileri takip et "
                + "(diğerlerinden hiçbir şey eklenmez)", Ayarlar.sadeceOnemli(this));

        // ---------------- Fotoğraflar
        baslik("Fotoğraflar");
        Switch foto = anahtar("WhatsApp fotoğraflarındaki yazıları oku", Ayarlar.foto(this));
        aciklama("Fotoğraf telefonda okunur, dışarı gönderilmez. Bunun için fotoğraf izni "
                + "\"Tümüne izin ver\" olmalı ve WhatsApp'ta Ayarlar > Sohbetler > "
                + "\"Medya görünürlüğü\" açık olmalı. Herhangi bir fotoğrafı da Paylaş > "
                + "WA Görevler ile görev yapabilirsiniz.");
        Button fotoIzin = dugme("Fotoğraf iznini ver");
        fotoIzin.setOnClickListener(v -> {
            if (Ocr.izinVar(this)) {
                Toast.makeText(this, "İzin zaten verilmiş", Toast.LENGTH_SHORT).show();
            } else {
                requestPermissions(new String[]{Build.VERSION.SDK_INT >= 33
                        ? Manifest.permission.READ_MEDIA_IMAGES
                        : Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
            }
        });

        // ---------------- Saatlik hatırlatma
        baslik("Saat başı hatırlatma");
        Switch saatlik = anahtar("Saat başı yapılacaklar listesini bildir", Ayarlar.saatlik(this));
        LinearLayout saatler = new LinearLayout(this);
        saatler.setOrientation(LinearLayout.HORIZONTAL);
        saatler.setGravity(Gravity.CENTER_VERTICAL);
        EditText bas = sayiKutusu(Ayarlar.basSaat(this));
        EditText bit = sayiKutusu(Ayarlar.bitSaat(this));
        saatler.addView(etiket("Saat "));
        saatler.addView(bas);
        saatler.addView(etiket(":00  ile  "));
        saatler.addView(bit);
        saatler.addView(etiket(":00  arası"));
        kok.addView(saatler);
        aciklama("Kendi WhatsApp numaranız (ör. 905321234567). Yazarsanız bildirimdeki "
                + "\"WhatsApp'a gönder\" düğmesi listeyi doğrudan kendi sohbetinize hazır getirir; "
                + "sadece gönder tuşuna basarsınız.");
        EditText numara = new EditText(this);
        numara.setInputType(InputType.TYPE_CLASS_PHONE);
        numara.setHint("90532…");
        numara.setText(Ayarlar.numara(this));
        kok.addView(numara);
        Button dene = dugme("Şimdi bir deneme bildirimi gönder");
        dene.setOnClickListener(v -> {
            if (Db.get(this).sayi(false) == 0) {
                Toast.makeText(this, "Açık görev yok, önce bir görev ekleyin", Toast.LENGTH_SHORT).show();
            } else {
                Ayarlar.duzenle(this).putString("numara", numara.getText().toString().trim()).apply();
                Bildirim.liste(this);
            }
        });

        // ---------------- Diğer
        baslik("Diğer");
        Switch yeniBildirim = anahtar("Yeni görev eklenince hemen bildirim göster",
                Ayarlar.yeniBildirim(this));

        // ---------------- Kaydet
        Button kaydet = dugme("KAYDET");
        kaydet.setTypeface(null, Typeface.BOLD);
        kaydet.setOnClickListener(v -> {
            Ayarlar.duzenle(this)
                    .putBoolean("aktif", aktif.isChecked())
                    .putBoolean("hepsi", hepsi.isChecked())
                    .putBoolean("ekran", ekran.isChecked())
                    .putString("benimAdim", benimAdim.getText().toString().trim())
                    .putString("kelimeler", kelimeler.getText().toString())
                    .putString("onemli", onemliler.getText().toString())
                    .putBoolean("onemliHepsi", onemliHepsi.isChecked())
                    .putBoolean("sadeceOnemli", sadeceOnemli.isChecked())
                    .putBoolean("foto", foto.isChecked())
                    .putBoolean("saatlik", saatlik.isChecked())
                    .putInt("basSaat", saat(bas, 9))
                    .putInt("bitSaat", saat(bit, 19))
                    .putString("numara", numara.getText().toString().trim())
                    .putBoolean("yeniBildirim", yeniBildirim.isChecked())
                    .apply();
            Hatirlatici.planla(this);
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show();
            finish();
        });

        // ---------------- Yardım
        baslik("Samsung için önemli");
        aciklama("Samsung arka plandaki uygulamaları kapatabilir. Kesintisiz çalışması için: "
                + "Ayarlar > Pil > Arka plan kullanım sınırları > Hiç uyumayan uygulamalar > "
                + "WA Görevler'i ekleyin.");
        Button erisim = dugme("Bildirim erişimi ayarları");
        erisim.setOnClickListener(v -> MainActivity.erisimAyarlariniAc(this));
        Button pil = dugme("Pil optimizasyonu ayarları");
        pil.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });

        baslik("Gizlilik");
        aciklama("Bu uygulamanın internet izni yoktur. Mesajlar ve fotoğraflar yalnızca bu "
                + "telefonda işlenir ve saklanır. Liste yalnızca siz \"WhatsApp'a gönder\" ya da "
                + "\"Paylaş\" dediğinizde uygulamadan çıkar.");

        setContentView(kaydir);
    }

    // ---------------------------------------------------------------- kişi seçimi

    private void kisiSecDialog(EditText alan) {
        List<String> adlar = Db.get(this).sonKisiler(80);
        if (adlar.isEmpty()) {
            Toast.makeText(this, "Henüz WhatsApp mesajı yakalanmadı. "
                    + "Bildirim erişimini açtıktan sonra mesaj geldikçe kişiler burada listelenir.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        List<String> mevcut = Ayarlar.satirlar(alan.getText().toString());
        Set<String> mevcutNorm = new HashSet<>();
        for (String m : mevcut) mevcutNorm.add(Ayarlar.normalize(m).trim());

        String[] dizi = adlar.toArray(new String[0]);
        boolean[] secili = new boolean[dizi.length];
        Set<String> listedekiler = new HashSet<>();
        for (int i = 0; i < dizi.length; i++) {
            String n = Ayarlar.normalize(dizi[i]).trim();
            listedekiler.add(n);
            secili[i] = mevcutNorm.contains(n);
        }

        new AlertDialog.Builder(this)
                .setTitle("Kişi / grup seç")
                .setMultiChoiceItems(dizi, secili, (d, hangisi, isaretli) -> secili[hangisi] = isaretli)
                .setPositiveButton("Tamam", (d, w) -> {
                    List<String> sonuc = new ArrayList<>();
                    for (String m : mevcut) {
                        if (!listedekiler.contains(Ayarlar.normalize(m).trim())) sonuc.add(m);
                    }
                    for (int i = 0; i < dizi.length; i++) if (secili[i]) sonuc.add(dizi[i]);
                    alan.setText(String.join("\n", sonuc));
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    // ---------------------------------------------------------------- yardımcılar

    private void baslik(String metin) {
        TextView t = new TextView(this);
        t.setText(metin);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(Color.parseColor("#128C7E"));
        t.setPadding(0, dp(20), 0, dp(4));
        kok.addView(t);
    }

    private void aciklama(String metin) {
        TextView t = new TextView(this);
        t.setText(metin);
        t.setTextColor(Color.parseColor("#666666"));
        t.setPadding(0, dp(2), 0, dp(4));
        kok.addView(t);
    }

    private TextView etiket(String metin) {
        TextView t = new TextView(this);
        t.setText(metin);
        return t;
    }

    private Switch anahtar(String metin, boolean acik) {
        Switch s = new Switch(this);
        s.setText(metin);
        s.setChecked(acik);
        s.setPadding(0, dp(8), 0, dp(8));
        kok.addView(s);
        return s;
    }

    private Button dugme(String metin) {
        Button b = new Button(this);
        b.setText(metin);
        kok.addView(b);
        return b;
    }

    private EditText cokSatir(String metin, int satir) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMinLines(satir);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setText(metin);
        kok.addView(e);
        return e;
    }

    private EditText sayiKutusu(int deger) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setEms(2);
        e.setGravity(Gravity.CENTER);
        e.setText(String.valueOf(deger));
        return e;
    }

    private static int saat(EditText e, int varsayilan) {
        try {
            int h = Integer.parseInt(e.getText().toString().trim());
            return Math.max(0, Math.min(23, h));
        } catch (NumberFormatException ex) {
            return varsayilan;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
