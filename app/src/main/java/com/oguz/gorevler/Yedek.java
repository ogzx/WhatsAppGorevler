package com.oguz.gorevler;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Görevleri ve ayarları bir dosyaya (JSON) yedekler, geri yükler. İnternet yok; dosya sizde kalır. */
public final class Yedek {

    private Yedek() {}

    /** Tüm görevleri ve ayarları JSON olarak verilen dosyaya yazar. */
    static void disaAktar(Context c, Uri hedef) throws Exception {
        JSONObject kok = new JSONObject();
        kok.put("surum", 1);
        kok.put("tarih", System.currentTimeMillis());

        JSONArray gorevler = new JSONArray();
        Db db = Db.get(c);
        for (Db.Gorev g : db.tumGorevler()) {
            JSONObject o = new JSONObject();
            o.put("kimden", g.kimden);
            o.put("sohbet", g.sohbet);
            o.put("metin", g.metin);
            o.put("zaman", g.zaman);
            o.put("eslesen", g.eslesen);
            o.put("tamam", db.tamamMi(g.id));
            o.put("son", g.son);
            o.put("firma", g.firma);
            gorevler.put(o);
        }
        kok.put("gorevler", gorevler);

        // Anahtar kelimeler, önemli kişiler, periyodik ayarlar gibi metin ayarları
        JSONObject ayar = new JSONObject();
        ayar.put("kelimeler", Ayarlar.kelimelerMetin(c));
        ayar.put("onemli", Ayarlar.onemliMetin(c));
        ayar.put("periyodikSecim", Ayarlar.periyodikSecim(c));
        kok.put("ayarlar", ayar);

        try (OutputStream os = c.getContentResolver().openOutputStream(hedef, "w")) {
            if (os == null) throw new Exception("Dosya açılamadı");
            os.write(kok.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Yedek dosyasını okur, görevleri ekler (çakışanları atlar). Eklenen görev sayısını döner. */
    static int iceAktar(Context c, Uri kaynak) throws Exception {
        String metin = oku(c, kaynak);
        JSONObject kok = new JSONObject(metin);
        Db db = Db.get(c);

        int n = 0;
        JSONArray gorevler = kok.optJSONArray("gorevler");
        if (gorevler != null) {
            for (int i = 0; i < gorevler.length(); i++) {
                JSONObject o = gorevler.getJSONObject(i);
                Db.Gorev g = new Db.Gorev();
                g.kimden = o.optString("kimden", null);
                g.sohbet = o.optString("sohbet", null);
                g.metin = o.optString("metin", "").trim();
                if (g.metin.isEmpty()) continue;
                g.zaman = o.optLong("zaman", System.currentTimeMillis());
                g.eslesen = o.optString("eslesen", "");
                g.tamam = o.optBoolean("tamam", false);
                g.son = o.optLong("son", 0);
                g.firma = o.isNull("firma") ? null : o.optString("firma", null);
                db.geriYukle(g);
                n++;
            }
        }

        JSONObject ayar = kok.optJSONObject("ayarlar");
        if (ayar != null) {
            Ayarlar.duzenle(c)
                    .putString("kelimeler", ayar.optString("kelimeler", Ayarlar.kelimelerMetin(c)))
                    .putString("onemli", ayar.optString("onemli", Ayarlar.onemliMetin(c)))
                    .putString("periyodikSecim", ayar.optString("periyodikSecim", Ayarlar.periyodikSecim(c)))
                    .apply();
        }
        Hatirlatici.sonTarihleriPlanla(c);
        return n;
    }

    private static String oku(Context c, Uri u) throws Exception {
        try (InputStream is = c.getContentResolver().openInputStream(u)) {
            if (is == null) throw new Exception("Dosya açılamadı");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            String s;
            while ((s = br.readLine()) != null) b.append(s).append('\n');
            return b.toString();
        }
    }
}
