package com.oguz.gorevler;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/**
 * Fotoğraftaki yazıyı okur. Google ML Kit'in uygulamaya gömülü modeli kullanılır:
 * fotoğraf telefondan dışarı çıkmaz, internet gerekmez.
 */
public final class Ocr {

    public interface Sonuc {
        /** Okunan yazı; okunamazsa null. Ana iş parçacığında çağrılır. */
        void al(String yazi);
    }

    private static TextRecognizer tanima;

    private Ocr() {}

    private static synchronized TextRecognizer tanima() {
        if (tanima == null) tanima = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        return tanima;
    }

    static void oku(Context c, Uri uri, Sonuc sonuc) {
        InputImage resim;
        try {
            resim = InputImage.fromFilePath(c.getApplicationContext(), uri);
        } catch (Exception e) {
            sonuc.al(null);
            return;
        }
        tanima().process(resim)
                .addOnSuccessListener(t -> sonuc.al(t.getText()))
                .addOnFailureListener(e -> sonuc.al(null));
    }

    /** Bir bitmap'teki (ör. PDF sayfası) yazıyı okur. */
    static void oku(Bitmap bmp, Sonuc sonuc) {
        if (bmp == null) { sonuc.al(null); return; }
        InputImage resim = InputImage.fromBitmap(bmp, 0);
        tanima().process(resim)
                .addOnSuccessListener(t -> sonuc.al(t.getText()))
                .addOnFailureListener(e -> sonuc.al(null));
    }

    /** Galerideki (WhatsApp Images klasörü dahil) fotoğrafları okuma izni var mı? */
    static boolean izinVar(Context c) {
        String izin = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return c.checkSelfPermission(izin) == PackageManager.PERMISSION_GRANTED;
    }
}
