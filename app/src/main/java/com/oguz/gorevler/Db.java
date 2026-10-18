package com.oguz.gorevler;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

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
    }

    private static Db ornek;

    public static synchronized Db get(Context c) {
        if (ornek == null) ornek = new Db(c.getApplicationContext());
        return ornek;
    }

    private Db(Context c) {
        super(c, "gorevler.db", null, 2);
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
                + "foto TEXT)");
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
    }

    /** Aynı imzalı kayıt varsa eklemez. Yeni eklendiyse true döner. */
    public synchronized boolean ekle(String kimden, String sohbet, String metin,
                                     long zaman, String eslesen, String imza, String foto) {
        ContentValues v = new ContentValues();
        v.put("kimden", kimden);
        v.put("sohbet", sohbet);
        v.put("metin", metin);
        v.put("zaman", zaman);
        v.put("eslesen", eslesen);
        v.put("tamam", 0);
        v.put("imza", imza);
        v.put("foto", foto);
        long r = getWritableDatabase().insertWithOnConflict(
                "gorev", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        return r != -1;
    }

    public synchronized List<Gorev> liste(boolean tamamlananlar) {
        List<Gorev> sonuc = new ArrayList<>();
        Cursor c = getReadableDatabase().query("gorev",
                new String[]{"id", "kimden", "sohbet", "metin", "zaman", "eslesen", "tamam", "foto"},
                "tamam = ?", new String[]{tamamlananlar ? "1" : "0"},
                null, null, "zaman DESC");
        try {
            while (c.moveToNext()) {
                Gorev g = new Gorev();
                g.id = c.getLong(0);
                g.kimden = c.getString(1);
                g.sohbet = c.getString(2);
                g.metin = c.getString(3);
                g.zaman = c.getLong(4);
                g.eslesen = c.getString(5);
                g.tamam = c.getInt(6) == 1;
                g.foto = c.getString(7);
                sonuc.add(g);
            }
        } finally {
            c.close();
        }
        return sonuc;
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

    public synchronized void sil(long id) {
        getWritableDatabase().delete("gorev", "id = ?", new String[]{String.valueOf(id)});
    }

    public synchronized void tamamlananlariSil() {
        getWritableDatabase().delete("gorev", "tamam = 1", null);
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
