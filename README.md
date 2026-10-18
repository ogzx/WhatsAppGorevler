# WA Görevler 2.0

WhatsApp'a gelen mesajlardan yapılacaklar listesi çıkaran, **tamamen telefonun içinde çalışan** Android uygulaması.

## Özellikler
- **Anahtar kelime:** fatura, beyanname, SGK, ödeme, KDV, gönder, acil... geçen mesajlar görev olur (liste düzenlenebilir).
- **Önemli kişiler / gruplar:** Seçtiğin kişilerin her mesajı görev sayılır. İstersen *sadece* onlar takip edilir. Kişiler, mesaj gelenler listesinden işaretlenerek seçilir.
- **Fotoğraf okuma:** WhatsApp'a gelen fotoğraflardaki yazılar (fatura, dekont, tahakkuk fişi...) telefonda okunur, anahtar kelime ya da önemli kişi kuralına uyanlar görev olur. Ayrıca herhangi bir fotoğraf *Paylaş > WA Görevler* ile ya da menüdeki *Fotoğraftan görev* ile eklenebilir.
- **Saat başı hatırlatma:** Belirlediğin saat aralığında (varsayılan 09:00–19:00), açık görev varsa her saat başı listeyi bildirim olarak gösterir. Bildirimdeki **WhatsApp'a gönder** düğmesi listeyi kendi WhatsApp sohbetine hazır getirir.
- Dokun → tamamla, basılı tut → kopyala / düzenle / sil / fotoğrafı aç. Elle görev ekleme, paylaşma.

## Gizlilik
- İnternet izni yok (fotoğraf kütüphanesinin eklediği ağ izinleri de zorla kaldırıldı).
- Yazı tanıma, uygulamanın içine gömülü modelle telefonda yapılır.
- Veriler Google yedeğine girmez. Liste yalnızca sen "WhatsApp'a gönder" / "Paylaş" dersen uygulamadan çıkar.

## APK'yı oluşturma

### Yol 1: GitHub (bilgisayar gerekmez)
1. GitHub'da yeni bir depo aç, bu klasördeki tüm dosyaları yükle (`.github` klasörü dahil).
2. "Actions" sekmesinde derleme kendiliğinden başlar (~5–8 dk).
3. Depo sayfasındaki **Releases** bölümünden `WA-Gorevler.apk` dosyasını telefona indir.

### Yol 2: Android Studio
1. **File > Open** ile bu klasörü aç.
2. **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
3. `app/build/outputs/apk/debug/app-debug.apk` dosyasını telefona at.

## Telefona kurulum (Samsung S25 Ultra)
1. APK'yı aç → bilinmeyen kaynaklara izin ver → Yükle.
2. Uygulamayı aç; **Bildirim** ve **Fotoğraf** izinlerini ver (fotoğrafta **Tümüne izin ver** seç).
3. **Bildirim erişimini aç** → WA Görevler'i etkinleştir.
   - Kilitliyse: Ayarlar > Uygulamalar > WA Görevler > sağ üst ⋮ > **Kısıtlı ayarlara izin ver**, sonra tekrar dene.
4. Ayarlar > Pil > Arka plan kullanım sınırları > **Hiç uyumayan uygulamalar** → WA Görevler.
5. WhatsApp > Ayarlar > Sohbetler > **Medya görünürlüğü** açık olsun (fotoğraf okuma için).
6. Uygulama Ayarları'nda kendi numaranı yaz (905xxxxxxxxx) → saatlik listeyi tek dokunuşla kendine gönderirsin.

## Sınırlar
- Yalnızca bildirimi gelen mesajlar yakalanır (sessize alınmış sohbetler ya da WhatsApp'ta açıkken okunan sohbet bildirim üretmez).
- Sesli mesajlar okunamaz. El yazısı ve bulanık fotoğraflarda okuma zayıf olabilir.
- WhatsApp fotoğrafı otomatik indirmiyorsa, fotoğraf sen açınca okunur.
- WhatsApp'a mesajı tamamen otomatik gönderme yoktur: Android buna izin vermez. Liste hazır gelir, gönder tuşuna sen basarsın.
