package com.oguz.gorevler;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/** Ana ekran widget'ı: açık görevleri gösterir, satıra dokununca tamamlar, + ile ekler. */
public class GorevWidget extends AppWidgetProvider {

    static final String TIKLA = "com.oguz.gorevler.WIDGET_TIKLA";
    static final String EKLE = "com.oguz.gorevler.WIDGET_EKLE";
    static final String EXTRA_ID = "id";

    static void hepsiniYenile(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        ComponentName cn = new ComponentName(c, GorevWidget.class);
        int[] ids = m.getAppWidgetIds(cn);
        m.notifyAppWidgetViewDataChanged(ids, R.id.widget_liste);
        for (int id : ids) ciz(c, m, id);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) ciz(c, m, id);
    }

    private static void ciz(Context c, AppWidgetManager m, int widgetId) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_gorev);

        Intent svc = new Intent(c, GorevWidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        svc.setData(Uri.parse("widget://" + widgetId));
        rv.setRemoteAdapter(R.id.widget_liste, svc);
        rv.setEmptyView(R.id.widget_liste, R.id.widget_bos);

        // Satıra dokunma şablonu (tamamla)
        Intent tikla = new Intent(c, GorevWidget.class).setAction(TIKLA)
                .setData(Uri.parse("widget://tikla/" + widgetId));
        rv.setPendingIntentTemplate(R.id.widget_liste, PendingIntent.getBroadcast(
                c, widgetId, tikla, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));

        // Başlığa dokun → uygulamayı aç
        rv.setOnClickPendingIntent(R.id.widget_baslik, PendingIntent.getActivity(
                c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // + → hızlı ekleme ekranı
        rv.setOnClickPendingIntent(R.id.widget_ekle, PendingIntent.getActivity(
                c, 1, new Intent(c, HizliEkleActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        m.updateAppWidget(widgetId, rv);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (TIKLA.equals(intent.getAction())) {
            long id = intent.getLongExtra(EXTRA_ID, -1);
            if (id >= 0) {
                Db.Gorev g = Db.get(c).getir(id);
                if (g != null) Db.get(c).setTamam(id, !g.tamam);
                hepsiniYenile(c);
                Gorevlendir.ekraniYenile();
            }
        }
    }
}
