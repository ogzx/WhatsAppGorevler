package com.oguz.gorevler;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Görevleri ve mesaj gelen kişileri telefonun içinde (yerel SQLite) saklar. */
public class Db extends SQLiteOpenHelper {

    public static class Gorev {
        long id;
        String kimden;
        String sohbet;
        String metin;
        long zaman;
        String eslesen;
        boolean tamam;
        String foto;
        long son;     // son tarih (ms), 0 = yok
        String firma; // mükellef/firma etiketi, null = yok
    }

    private static Db ornek;

    public static synchronized Db get(Context c) {
        if (ornek == null) ornek = new Db(c.getApplicationContext());
        return ornek;
    }

    private Db(Context c) {
        super(c, "gorevler.db", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE gorev ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "kimden TEXT,"
                + "sohbet TEXT,"
                + "metin TEXT NOT NULL,"
                + "zaman INTEGER NOT NULL,"
                + "eslesen TEXT,"
                + "tamam INTEGER NOT NULL DEFAULT 0,"
                + "imza TEXT UNIQUE,"
                + "foto TEXT,"
                + "son INTEGER NOT NULL DEFAULT 0,"
                + "firma TEXT)");
        kisiTablosu(db);
    }

    private static void kisiTablosu(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS kisi (ad TEXT PRIMARY KEY, son INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int eski, int yeni) {
        if (eski < 2) {
            db.execSQL("ALTER TABLE gorev ADD COLUMN foto TEXT");
            kisiTablosu(db);
        }
        if (eski < 3) {
            db.execSQL("ALTER TABLE gorev ADD COLUMN son INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE gorev ADD COLUMN firma TEXT");
        }
    }

    private static final String[] ALANLAR = {
            "id", "kimden", "sohbet", "metin", "zaman", "eslesen", "tamam", "foto", "son", "firma"};

    private static Gorev oku(Cursor c) {
        Gorev g = new Gorev();
        g.id = c.getLong(0);
        g.kimden = c.getString(1);
        g.sohbet = c.getString(2);
        g.metin = c.getString(3);
        g.zaman = c.getLong(4);
        g.eslesen = c.getString(5);
        g.tamam = c.getInt(6) == 1;
        g.foto = c.getString(7);
        g.son = c.getLong(8);
        g.firma = c.getString(9);
        return g;
    }

    /** Eski 7-parametreli çağrılar için (son tarih ve firma yok). */
    public synchronized boolean ekle(String kimden, String sohbet, String metin,
                                     long zaman, String eslesen, String imza, String foto) {
        return ekle(kimden, sohbet, metin, zaman, eslesen, imza, foto, 0L, null);
    }

    /** Aynı imzalı kayıt varsa eklemez. Yeni eklendiyse satır id'si, yoksa -1 döner. */
    public synchronized long ekleId(String kimden, String sohbet, String metin, long zaman,
                                    String eslesen, String imza, String foto, long son, String firma) {
        ContentValues v = new ContentValues();
        v.put("kimden", kimden);
        v.put("sohbet", sohbet);
        v.put("metin", metin);
        v.put("zaman", zaman);
        v.put("eslesen", eslesen);
        v.put("tamam", 0);
        v.put("imza", imza);
        v.put("foto", foto);
        v.put("son", son);
        v.put("firma", firma);
        return getWritableDatabase().insertWithOnConflict(
                "gorev", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized boolean ekle(String kimden, String sohbet, String metin, long zaman,
                                     String eslesen, String imza, String foto, long son, String firma) {
        return ekleId(kimden, sohbet, metin, zaman, eslesen, imza, foto, son, firma) != -1;
    }

    public synchronized List<Gorev> liste(boolean tamamlananlar) {
        return liste(tamamlananlar, null);
    }

    /** Açık/tamam görevler; firma verilirse yalnızca o firmanınkiler. Son tarihi olanlar öne. */
    public synchronized List<Gorev> liste(boolean tamamlananlar, String firma) {
        List<Gorev> sonuc = new ArrayList<>();
        String sec = "tamam = ?";
        List<String> arg = new ArrayList<>();
        arg.add(tamamlananlar ? "1" : "0");
        if (firma != null) { sec += " AND firma = ?"; arg.add(firma); }
        // Son tarihi olanlar tarih sırasına göre önce, sonra yeni eklenenler.
        String sira = "CASE WHEN son > 0 THEN 0 ELSE 1 END, "
                + "CASE WHEN son > 0 THEN son ELSE -zaman END ASC, zaman DESC";
        Cursor c = getReadableDatabase().query("gorev", ALANLAR,
                sec, arg.toArray(new String[0]), null, null, sira);
        try {
            while (c.moveToNext()) sonuc.add(oku(c));
        } finally {
            c.close();
        }
        return sonuc;
    }

    /** Son tarihi olan, tamamlanmamış tüm görevler (alarm kurmak için). */
    public synchronized List<Gorev> sonTarihliler() {
        List<Gorev> sonuc = new ArrayList<>();
        Cursor c = getReadableDatabase().query("gorev", ALANLAR,
                "tamam = 0 AND son > 0", null, null, null, "son ASC");
        try {
            while (c.moveToNext()) sonuc.add(oku(c));
        } finally {
            c.close();
        }
        return sonuc;
    }

    public synchronized Gorev getir(long id) {
        Cursor c = getReadableDatabase().query("gorev", ALANLAR,
                "id = ?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? oku(c) : null;
        } finally {
            c.close();
        }
    }

    public synchronized int sayi(boolean tamamlananlar) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM gorev WHERE tamam = ?",
                new String[]{tamamlananlar ? "1" : "0"});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public synchronized void setTamam(long id, boolean tamam) {
        ContentValues v = new ContentValues();
        v.put("tamam", tamam ? 1 : 0);
        getWritableDatabase().update("gorev", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public synchronized void guncelle(long id, String metin) {
        ContentValues v = new ContentValues();
        v.put("metin", metin);
        getWritableDatabase().update("gorev", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public synchronized void setSon(long id, long son) {
        ContentValues v = new ContentValues();
        v.put("son", son);
        getWritableDatabase().update("gorev", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public synchronized void setFirma(long id, String firma) {
        ContentValues v = new ContentValues();
        v.put("firma", firma);
        getWritableDatabase().update("gorev", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public synchronized void sil(long id) {
        getWritableDatabase().delete("gorev", "id = ?", new String[]{String.valueOf(id)});
    }

    public synchronized void tamamlananlariSil() {
        getWritableDatabase().delete("gorev", "tamam = 1", null);
    }

    /** Görevlerde geçen firma adları (boş olmayan), alfabetik. */
    public synchronized List<String> firmalar() {
        Set<String> s = new LinkedHashSet<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT firma FROM gorev WHERE firma IS NOT NULL AND firma <> '' "
                        + "ORDER BY firma COLLATE NOCASE", null);
        try {
            while (c.moveToNext()) s.add(c.getString(0));
        } finally {
            c.close();
        }
        return new ArrayList<>(s);
    }

    /** Aynı sohbetten (veya kişiden) aynı metin, verilen zamandan sonra kaydedilmiş mi? */
    public synchronized boolean varMi(String metin, String sohbet, String kimden, long sonra) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM gorev WHERE metin = ? AND zaman >= ? "
                        + "AND (sohbet = ? OR kimden = ? OR sohbet = ? OR kimden = ?) LIMIT 1",
                new String[]{metin, String.valueOf(sonra),
                        bos(sohbet), bos(sohbet), bos(kimden), bos(kimden)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    /** Belirli bir imza (ör. periyodik görev anahtarı) zaten var mı? */
    public synchronized boolean imzaVar(String imza) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM gorev WHERE imza = ? LIMIT 1", new String[]{imza});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    private static String bos(String s) { return s == null ? "\u0000" : s; }

    // ---- Yedekleme: tüm görevleri olduğu gibi ver / geri koy ----

    public synchronized List<Gorev> tumGorevler() {
        List<Gorev> sonuc = new ArrayList<>();
        Cursor c = getReadableDatabase().query("gorev", ALANLAR, null, null, null, null, "id ASC");
        try {
            while (c.moveToNext()) {
                Gorev g = oku(c);
                // tamam alanını da taşımak için ayrıca oku
                sonuc.add(g);
            }
        } finally {
            c.close();
        }
        // tamam bilgisini ayrı sorguyla doldur
        return sonuc;
    }

    public synchronized boolean tamamMi(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT tamam FROM gorev WHERE id = ?", new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() && c.getInt(0) == 1;
        } finally {
            c.close();
        }
    }

    /** Yedekten gelen bir görevi ekler (imza çakışırsa atlar). */
    public synchronized void geriYukle(Gorev g) {
        ContentValues v = new ContentValues();
        v.put("kimden", g.kimden);
        v.put("sohbet", g.sohbet);
        v.put("metin", g.metin);
        v.put("zaman", g.zaman);
        v.put("eslesen", g.eslesen);
        v.put("tamam", g.tamam ? 1 : 0);
        v.put("imza", g.metin + "|" + g.zaman); // yedekten gelenler için benzersiz anahtar
        v.put("foto", g.foto);
        v.put("son", g.son);
        v.put("firma", g.firma);
        getWritableDatabase().insertWithOnConflict("gorev", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    // ---- Mesaj gelen kişiler (önemli kişi seçimi için) ----

    public synchronized void kisiGor(String ad) {
        if (ad == null || ad.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("ad", ad.trim());
        v.put("son", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("kisi", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized List<String> sonKisiler(int adet) {
        List<String> sonuc = new ArrayList<>();
        Cursor c = getReadableDatabase().query("kisi", new String[]{"ad"},
                null, null, null, null, "son DESC", String.valueOf(adet));
        try {
            while (c.moveToNext()) sonuc.add(c.getString(0));
        } finally {
            c.close();
        }
        return sonuc;
    }
}
