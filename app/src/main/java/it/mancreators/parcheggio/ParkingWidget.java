package it.mancreators.parcheggio;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

/** Widget per la home: pulsante SALVA + ultimo parcheggio (tocca per aprire la mappa). */
public class ParkingWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(ctx));
    }

    public static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, ParkingWidget.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews views = build(ctx);
        for (int id : ids) mgr.updateAppWidget(id, views);
    }

    private static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        JSONObject spot = SpotStore.latest(ctx);
        if (spot == null) {
            v.setTextViewText(R.id.w_label, "DOV'È L'AUTO");
            v.setTextViewText(R.id.w_address, "Nessun parcheggio salvato");
            v.setViewVisibility(R.id.w_time, View.GONE);
        } else {
            v.setTextViewText(R.id.w_label, "AUTO PARCHEGGIATA");
            v.setTextViewText(R.id.w_address, Format.place(spot));
            v.setTextViewText(R.id.w_time, Format.when(spot.optLong("time")));
            v.setViewVisibility(R.id.w_time, View.VISIBLE);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        Intent save = new Intent(ctx, SaveActivity.class);
        save.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.w_save, PendingIntent.getActivity(ctx, 1, save, flags));

        Intent open = new Intent(ctx, MainActivity.class);
        open.setAction(MainActivity.ACTION_SHOW_LATEST);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.w_info, PendingIntent.getActivity(ctx, 2, open, flags));
        return v;
    }
}
