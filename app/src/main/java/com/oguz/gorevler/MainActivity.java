package com.oguz.gorevler;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    /** Servis yeni görev eklediğinde ekranı yenilemek için. */
    static volatile Runnable degisti;

    private static final int YESIL = Color.parseColor("#128C7E");
    private static final int GRI = Color.parseColor("#8A8A8A");

    private final List<Db.Gorev> veriler = new ArrayList<>();
    private final SimpleDateFormat tarih =
            new SimpleDateFormat("dd.MM HH:mm", Locale.forLanguageTag("tr-TR"));

    private boolean tamamGoster = false;
    private String firmaFiltre = null; // null = tümü
    private LinearLayout uyari, ekranUyari;
    private Button sekmeAcik, sekmeTamam;
    private TextView bos;
    private GorevAdapter adapter;

    private static final int ISTEK_YEDEK = 11;
    private static final int ISTEK_GERIYUKLE = 12;
    private final SimpleDateFormat sonBicim =
            new SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("tr-TR"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout kok = new LinearLayout(this);
        kok.setOrientation(LinearLayout.VERTICAL);

        // Bildirim erişimi kapalıysa görünen uyarı
        uyari = new LinearLayout(this);
        uyari.setOrientation(LinearLayout.VERTICAL);
        uyari.setBackgroundColor(Color.parseColor("#FFF3CD"));
        uyari.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView uyariMetin = new TextView(this);
        uyariMetin.setText("WhatsApp mesajlarını yakalamak için bildirim erişimi gerekiyor.\n"
                + "Ayar gri/kilitli görünürse: Ayarlar > Uygulamalar > WA Görevler > "
                + "sağ üstteki ⋮ > \"Kısıtlı ayarlara izin ver\" deyip tekrar deneyin.");
        uyariMetin.setTextColor(Color.parseColor("#664D03"));
        Button uyariButon = new Button(this);
        uyariButon.setText("Bildirim erişimini aç");
        uyariButon.setOnClickListener(v -> erisimAyarlariniAc(this));
        uyari.addView(uyariMetin);
        uyari.addView(uyariButon);
        kok.addView(uyari);

        // Ekran okuma kapalıysa görünen uyarı
        ekranUyari = new LinearLayout(this);
        ekranUyari.setOrientation(LinearLayout.VERTICAL);
        ekranUyari.setBackgroundColor(Color.parseColor("#E7F1FF"));
        ekranUyari.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView ekranMetin = new TextView(this);
        ekranMetin.setText("Sessize alınmış sohbetleri ve WhatsApp açıkken gelen mesajları da "
                + "yakalamak için ekran okumayı açın: Yüklü uygulamalar > WA Görevler ekran okuma > Aç.");
        ekranMetin.setTextColor(Color.parseColor("#084298"));
        Button ekranButon = new Button(this);
        ekranButon.setText("Ekran okumayı aç");
        ekranButon.setOnClickListener(v -> ekranOkumaAyarlariniAc(this));
        ekranUyari.addView(ekranMetin);
        ekranUyari.addView(ekranButon);
        kok.addView(ekranUyari);

        // Sekmeler
        LinearLayout sekmeler = new LinearLayout(this);
        sekmeler.setOrientation(LinearLayout.HORIZONTAL);
        sekmeler.setPadding(dp(8), dp(4), dp(8), 0);
        sekmeAcik = new Button(this);
        sekmeTamam = new Button(this);
        sekmeAcik.setOnClickListener(v -> { tamamGoster = false; yenile(); });
        sekmeTamam.setOnClickListener(v -> { tamamGoster = true; yenile(); });
        sekmeler.addView(sekmeAcik, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sekmeler.addView(sekmeTamam, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        kok.addView(sekmeler);

        // Liste + boş durum
        FrameLayout kap = new FrameLayout(this);
        ListView liste = new ListView(this);
        liste.setDividerHeight(dp(1));
        bos = new TextView(this);
        bos.setGravity(Gravity.CENTER);
        bos.setPadding(dp(32), dp(32), dp(32), dp(32));
        bos.setTextColor(GRI);
        kap.addView(liste, new FrameLayout.LayoutParams(-1, -1));
        kap.addView(bos, new FrameLayout.LayoutParams(-1, -1));
        liste.setEmptyView(bos);
        kok.addView(kap, new LinearLayout.LayoutParams(-1, 0, 1));

        adapter = new GorevAdapter();
        liste.setAdapter(adapter);

        liste.setOnItemClickListener((p, v, konum, id) -> {
            Db.Gorev g = veriler.get(konum);
            Db.get(this).setTamam(g.id, !g.tamam);
            Toast.makeText(this, g.tamam ? "Yapılacaklara geri alındı" : "Tamamlandı ✓",
                    Toast.LENGTH_SHORT).show();
            yenile();
        });

        liste.setOnItemLongClickListener((p, v, konum, id) -> {
            secenekler(veriler.get(konum));
            return true;
        });

        setContentView(kok);

        Bildirim.kanallar(this);
        izinleriIste();
    }

    /** Bildirim gösterme ve fotoğraf okuma izinleri. */
    private void izinleriIste() {
        List<String> izinler = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                izinler.add(Manifest.permission.POST_NOTIFICATIONS);
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                izinler.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            izinler.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!izinler.isEmpty()) requestPermissions(izinler.toArray(new String[0]), 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        degisti = this::yenile;
        uyari.setVisibility(erisimVar() ? View.GONE : View.VISIBLE);
        ekranUyari.setVisibility(!Ayarlar.ekran(this) || ekranOkumaAcik(this) ? View.GONE : View.VISIBLE);
        Hatirlatici.planla(this);
        PeriyodikGorev.olustur(this);
        Gorevlendir.tarihleriIsle(this);
        GorevWidget.hepsiniYenile(this);
        yenile();
    }

    @Override
    protected void onPause() {
        super.onPause();
        degisti = null;
    }

    private void yenile() {
        Db db = Db.get(this);
        veriler.clear();
        veriler.addAll(db.liste(tamamGoster, firmaFiltre));
        adapter.notifyDataSetChanged();

        String ek = firmaFiltre != null ? " · " + firmaFiltre : "";
        sekmeAcik.setText("Yapılacak (" + db.sayi(false) + ")" + (firmaFiltre != null && !tamamGoster ? ek : ""));
        sekmeTamam.setText("Tamamlanan (" + db.sayi(true) + ")");
        sekmeAcik.setTypeface(null, tamamGoster ? Typeface.NORMAL : Typeface.BOLD);
        sekmeTamam.setTypeface(null, tamamGoster ? Typeface.BOLD : Typeface.NORMAL);
        sekmeAcik.setAlpha(tamamGoster ? 0.6f : 1f);
        sekmeTamam.setAlpha(tamamGoster ? 1f : 0.6f);

        if (firmaFiltre != null) {
            bos.setText("“" + firmaFiltre + "” firmasında "
                    + (tamamGoster ? "tamamlanan" : "açık") + " görev yok.");
        } else {
            bos.setText(tamamGoster
                    ? "Tamamlanan görev yok."
                    : "Yapılacak görev yok.\n\nWhatsApp'a anahtar kelime içeren bir mesaj "
                      + "geldiğinde burada görünecek. Bir göreve dokununca tamamlanır, "
                      + "basılı tutunca seçenekler açılır.");
        }
    }

    private boolean erisimVar() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    static void erisimAyarlariniAc(Context c) {
        c.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    static boolean ekranOkumaAcik(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/");
    }

    static void ekranOkumaAyarlariniAc(Context c) {
        c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    // ---- Seçenekler / menü ----

    private void secenekler(Db.Gorev g) {
        List<String> secimler = new ArrayList<>();
        secimler.add("Kopyala");
        secimler.add("Düzenle");
        secimler.add(g.son > 0 ? "Son tarihi değiştir/kaldır" : "Son tarih belirle");
        secimler.add(g.firma != null ? "Firmayı değiştir (" + g.firma + ")" : "Firma ata");
        secimler.add("Sil");
        if (g.foto != null) secimler.add("Fotoğrafı aç");
        String[] secim = secimler.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(g.kimden != null ? g.kimden : "Görev")
                .setItems(secim, (d, i) -> {
                    String s = secim[i];
                    if (s.equals("Kopyala")) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("görev", g.metin));
                        Toast.makeText(this, "Kopyalandı", Toast.LENGTH_SHORT).show();
                    } else if (s.equals("Düzenle")) {
                        metinDialog("Görevi düzenle", g.metin, yeni -> {
                            Db.get(this).guncelle(g.id, yeni);
                            yenile();
                        });
                    } else if (s.startsWith("Son tarih")) {
                        sonTarihSec(g);
                    } else if (s.startsWith("Firma")) {
                        firmaSec(g);
                    } else if (s.equals("Sil")) {
                        new AlertDialog.Builder(this)
                                .setMessage("Bu görev silinsin mi?")
                                .setPositiveButton("Sil", (d2, w) -> { Db.get(this).sil(g.id); yenile(); })
                                .setNegativeButton("Vazgeç", null)
                                .show();
                    } else if (s.equals("Fotoğrafı aç")) {
                        try {
                            Intent ac = new Intent(Intent.ACTION_VIEW);
                            ac.setDataAndType(Uri.parse(g.foto), "image/*");
                            ac.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(ac);
                        } catch (Exception e) {
                            Toast.makeText(this, "Fotoğraf açılamadı (silinmiş olabilir)", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    /** Göreve son tarih yaz: metinden yakala, elle gir ya da kaldır. */
    private void sonTarihSec(Db.Gorev g) {
        metinDialog("Son tarih (gg.aa.yyyy) — boş bırakırsan kaldırılır",
                g.son > 0 ? sonBicim.format(new Date(g.son)) : "", t -> {
            long son = Tarih.bul(t, System.currentTimeMillis(), Ayarlar.basSaat(this));
            Db.get(this).setSon(g.id, son);
            Hatirlatici.sonTarihleriPlanla(this);
            Toast.makeText(this, son > 0 ? "Son tarih: " + Tarih.bicim(son) : "Son tarih kaldırıldı",
                    Toast.LENGTH_SHORT).show();
            yenile();
        });
    }

    /** Göreve firma/mükellef ata. Mevcut firmalar listeden ya da yeni ad. */
    private void firmaSec(Db.Gorev g) {
        List<String> firmalar = Db.get(this).firmalar();
        List<String> secenek = new ArrayList<>(firmalar);
        secenek.add("+ Yeni firma…");
        secenek.add("Firmayı kaldır");
        new AlertDialog.Builder(this)
                .setTitle("Firma / mükellef")
                .setItems(secenek.toArray(new String[0]), (d, i) -> {
                    if (i == secenek.size() - 1) {
                        Db.get(this).setFirma(g.id, null);
                        yenile();
                    } else if (i == secenek.size() - 2) {
                        metinDialog("Yeni firma adı", null, ad -> {
                            Db.get(this).setFirma(g.id, ad);
                            yenile();
                        });
                    } else {
                        Db.get(this).setFirma(g.id, firmalar.get(i));
                        yenile();
                    }
                })
                .show();
    }

    private interface MetinAlici { void al(String metin); }

    private void metinDialog(String baslik, String ilk, MetinAlici alici) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setMinLines(2);
        if (ilk != null) e.setText(ilk);
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        f.addView(e);
        new AlertDialog.Builder(this)
                .setTitle(baslik)
                .setView(f)
                .setPositiveButton("Kaydet", (d, w) -> {
                    String t = e.getText().toString().trim();
                    if (!t.isEmpty()) alici.al(t);
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "+ Ekle").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, 8, 1, "Firmaya göre süz");
        menu.add(0, 5, 2, "📷 Fotoğraftan görev");
        menu.add(0, 7, 3, "Sohbet içe aktar (nasıl?)");
        menu.add(0, 6, 4, "Listeyi WhatsApp'a gönder");
        menu.add(0, 3, 5, "Listeyi paylaş");
        menu.add(0, 9, 6, "Yedekle (dosyaya)");
        menu.add(0, 10, 7, "Geri yükle (dosyadan)");
        menu.add(0, 2, 8, "Ayarlar");
        menu.add(0, 4, 9, "Tamamlananları temizle");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                metinDialog("Yeni görev", null, t -> {
                    long simdi = System.currentTimeMillis();
                    long son = Ayarlar.tarihYakala(this) ? Tarih.bul(t, simdi, Ayarlar.basSaat(this)) : 0;
                    Db.get(this).ekleId("Ben", null, t, simdi, "", "elle|" + simdi, null, son, firmaFiltre);
                    Hatirlatici.sonTarihleriPlanla(this);
                    tamamGoster = false;
                    yenile();
                });
                return true;
            case 8:
                firmayaGoreSuz();
                return true;
            case 9:
                yedekle();
                return true;
            case 10:
                geriYukleSec();
                return true;
            case 5:
                startActivity(new Intent(this, FotoActivity.class));
                return true;
            case 6:
                whatsappaGonder();
                return true;
            case 7:
                new AlertDialog.Builder(this)
                        .setTitle("Sohbet içe aktarma")
                        .setMessage("Eski mesajları ya da kaçan mesajları taramak için:\n\n"
                                + "1. WhatsApp'ta sohbeti açın.\n"
                                + "2. Sağ üst ⋮ > Diğer > Sohbeti dışa aktar.\n"
                                + "3. \"Medya olmadan\" seçin.\n"
                                + "4. Açılan listeden \"WA Görevler'e aktar\"ı seçin.\n\n"
                                + "Uygulama mesajları anahtar kelime ve önemli kişi kurallarına "
                                + "göre tarar; daha önce eklenenleri tekrar eklemez.\n\n"
                                + "Tek bir mesajı eklemek için: mesajı kopyalayıp + Ekle'ye yapıştırın "
                                + "ya da herhangi bir uygulamadan Paylaş > WA Görevler'e aktar deyin.")
                        .setPositiveButton("WhatsApp'ı aç", (d, w) -> {
                            Intent wa = getPackageManager().getLaunchIntentForPackage("com.whatsapp");
                            if (wa != null) startActivity(wa);
                        })
                        .setNegativeButton("Kapat", null)
                        .show();
                return true;
            case 2:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case 3:
                paylas();
                return true;
            case 4:
                new AlertDialog.Builder(this)
                        .setMessage("Tamamlanan tüm görevler silinsin mi?")
                        .setPositiveButton("Temizle", (d, w) -> { Db.get(this).tamamlananlariSil(); yenile(); })
                        .setNegativeButton("Vazgeç", null)
                        .show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /** Yalnızca kullanıcı isterse, seçtiği uygulamaya metin olarak paylaşır. */
    private void paylas() {
        String metin = Bildirim.listeMetni(this);
        if (metin == null) {
            Toast.makeText(this, "Paylaşılacak görev yok", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, metin);
        startActivity(Intent.createChooser(i, "Listeyi paylaş"));
    }

    private void whatsappaGonder() {
        String metin = Bildirim.listeMetni(this);
        if (metin == null) {
            Toast.makeText(this, "Gönderilecek görev yok", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(Bildirim.whatsappIntent(this, metin));
        } catch (Exception e) {
            paylas(); // WhatsApp bulunamazsa genel paylaşım
        }
    }

    // ---- Firma süzme ----

    private void firmayaGoreSuz() {
        List<String> firmalar = Db.get(this).firmalar();
        List<String> secenek = new ArrayList<>();
        secenek.add("Tümü");
        secenek.addAll(firmalar);
        if (firmalar.isEmpty()) {
            Toast.makeText(this, "Henüz firma etiketi yok. Bir görevi basılı tutup firma atayın.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Firmaya göre süz")
                .setItems(secenek.toArray(new String[0]), (d, i) -> {
                    firmaFiltre = i == 0 ? null : secenek.get(i);
                    yenile();
                })
                .show();
    }

    // ---- Yedekleme / geri yükleme (dosya; internet yok) ----

    private void yedekle() {
        String ad = "wa-gorevler-yedek-"
                + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()) + ".json";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, ad);
        try {
            startActivityForResult(i, ISTEK_YEDEK);
        } catch (Exception e) {
            Toast.makeText(this, "Dosya seçici açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void geriYukleSec() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        try {
            startActivityForResult(i, ISTEK_GERIYUKLE);
        } catch (Exception e) {
            Toast.makeText(this, "Dosya seçici açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int istek, int sonuc, Intent veri) {
        super.onActivityResult(istek, sonuc, veri);
        if (sonuc != RESULT_OK || veri == null || veri.getData() == null) return;
        Uri u = veri.getData();
        try {
            if (istek == ISTEK_YEDEK) {
                Yedek.disaAktar(this, u);
                Toast.makeText(this, "Yedek kaydedildi", Toast.LENGTH_SHORT).show();
            } else if (istek == ISTEK_GERIYUKLE) {
                int n = Yedek.iceAktar(this, u);
                Toast.makeText(this, n + " görev geri yüklendi", Toast.LENGTH_SHORT).show();
                yenile();
            }
        } catch (Exception e) {
            Toast.makeText(this, "İşlem başarısız: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---- Liste görünümü ----

    private class GorevAdapter extends BaseAdapter {
        @Override public int getCount() { return veriler.size(); }
        @Override public Object getItem(int i) { return veriler.get(i); }
        @Override public long getItemId(int i) { return veriler.get(i).id; }

        @Override
        public View getView(int i, View eski, ViewGroup ebeveyn) {
            Satir s;
            if (eski == null) {
                s = new Satir(MainActivity.this);
                eski = s.kok;
                eski.setTag(s);
            } else {
                s = (Satir) eski.getTag();
            }
            s.bagla(veriler.get(i));
            return eski;
        }
    }

    private class Satir {
        final LinearLayout kok;
        final TextView kutu, ust, metin, etiket;

        Satir(Context c) {
            kok = new LinearLayout(c);
            kok.setOrientation(LinearLayout.HORIZONTAL);
            kok.setPadding(dp(14), dp(12), dp(14), dp(12));

            kutu = new TextView(c);
            kutu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            kutu.setTextColor(YESIL);
            kutu.setPadding(0, 0, dp(12), 0);
            kok.addView(kutu);

            LinearLayout sag = new LinearLayout(c);
            sag.setOrientation(LinearLayout.VERTICAL);
            ust = new TextView(c);
            ust.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            ust.setTextColor(GRI);
            ust.setSingleLine(true);
            ust.setEllipsize(TextUtils.TruncateAt.END);
            metin = new TextView(c);
            metin.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            metin.setMaxLines(6);
            metin.setEllipsize(TextUtils.TruncateAt.END);
            metin.setPadding(0, dp(2), 0, dp(4));
            etiket = new TextView(c);
            etiket.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            etiket.setTextColor(YESIL);
            etiket.setTypeface(null, Typeface.BOLD);
            sag.addView(ust);
            sag.addView(metin);
            sag.addView(etiket);
            kok.addView(sag, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }

        void bagla(Db.Gorev g) {
            kutu.setText(g.tamam ? "☑" : "☐");

            StringBuilder u = new StringBuilder();
            if (g.firma != null && !g.firma.isEmpty()) u.append("🏢 ").append(g.firma);
            if (g.kimden != null) {
                if (u.length() > 0) u.append(" · ");
                u.append(g.kimden);
            }
            if (g.sohbet != null && !g.sohbet.equals(g.kimden)) {
                if (u.length() > 0) u.append(" · ");
                u.append(g.sohbet);
            }
            if (u.length() > 0) u.append(" · ");
            u.append(tarih.format(new Date(g.zaman)));
            ust.setText(u.toString());

            metin.setText(g.metin);
            metin.setTextColor(g.tamam ? GRI : Color.parseColor("#1F1F1F"));
            if (g.tamam) {
                metin.setPaintFlags(metin.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                metin.setPaintFlags(metin.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }

            StringBuilder e = new StringBuilder();
            if (g.son > 0 && !g.tamam) {
                long kalan = g.son - System.currentTimeMillis();
                long gun = kalan / (24L * 3600 * 1000);
                String ne;
                if (kalan < 0) ne = "⏰ GEÇTİ (" + sonBicim.format(new Date(g.son)) + ")";
                else if (gun == 0) ne = "⏰ bugün son!";
                else if (gun == 1) ne = "⏰ yarın son";
                else ne = "⏰ " + sonBicim.format(new Date(g.son)) + " (" + gun + " gün)";
                e.append(ne);
            }
            if (g.eslesen != null && !g.eslesen.isEmpty()) {
                if (e.length() > 0) e.append("  ·  ");
                e.append('#').append(g.eslesen);
            }
            if (g.foto != null) {
                if (e.length() > 0) e.append("  ·  ");
                e.append("📷 fotoğraf (basılı tut → aç)");
            }
            if (e.length() > 0) {
                etiket.setVisibility(View.VISIBLE);
                etiket.setText(e.toString());
                boolean acil = g.son > 0 && !g.tamam
                        && g.son - System.currentTimeMillis() < 2L * 24 * 3600 * 1000;
                etiket.setTextColor(acil ? Color.parseColor("#C0392B") : YESIL);
            } else {
                etiket.setVisibility(View.GONE);
            }
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
