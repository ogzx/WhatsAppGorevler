package com.oguz.gorevler;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/** Widget listesini açık görevlerle doldurur. */
public class GorevWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Fabrika(getApplicationContext());
    }

    static class Fabrika implements RemoteViewsFactory {
        private final Context c;
        private final List<Db.Gorev> veri = new ArrayList<>();

        Fabrika(Context c) { this.c = c; }

        @Override public void onCreate() { }

        @Override
        public void onDataSetChanged() {
            veri.clear();
            veri.addAll(Db.get(c).liste(false));
        }

        @Override public void onDestroy() { veri.clear(); }
        @Override public int getCount() { return veri.size(); }

        @Override
        public RemoteViews getViewAt(int konum) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_satir);
            if (konum < 0 || konum >= veri.size()) return rv;
            Db.Gorev g = veri.get(konum);
            rv.setTextViewText(R.id.satir_metin, Bildirim.tekSatir(g.metin, 120));
            String son = Tarih.bicim(g.son);
            rv.setTextViewText(R.id.satir_son, son != null ? son : "");

            Intent dolgu = new Intent();
            dolgu.putExtra(GorevWidget.EXTRA_ID, g.id);
            rv.setOnClickFillInIntent(R.id.satir_metin, dolgu);
            rv.setOnClickFillInIntent(R.id.satir_kutu, dolgu);
            return rv;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int konum) {
            return konum < veri.size() ? veri.get(konum).id : konum;
        }
        @Override public boolean hasStableIds() { return true; }
    }
}
