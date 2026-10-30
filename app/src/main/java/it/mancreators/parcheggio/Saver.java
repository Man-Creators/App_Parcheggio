package it.mancreators.parcheggio;

import android.content.Context;
import android.location.Location;

import org.json.JSONObject;

/** Salvataggio comune a widget e app: GPS → salva → aggiorna widget → cerca indirizzo. */
public final class Saver {

    public interface Listener {
        void onSaved(JSONObject spot);
        void onAddress(JSONObject spot);
        void onError(String message);
    }

    private Saver() {}

    public static void saveNow(Context ctx, Listener l) {
        final Context app = ctx.getApplicationContext();
        LocationHelper.getCurrent(app, new LocationHelper.Callback() {
            @Override public void onLocation(Location loc) {
                JSONObject spot = SpotStore.add(app, loc.getLatitude(), loc.getLongitude(), loc.getAccuracy());
                ParkingWidget.updateAll(app);
                l.onSaved(spot);
                final String id = spot.optString("id");
                LocationHelper.reverse(app, loc.getLatitude(), loc.getLongitude(), address -> {
                    if (address == null || address.isEmpty()) return;
                    JSONObject updated = SpotStore.update(app, id, "address", address);
                    ParkingWidget.updateAll(app);
                    if (updated != null) l.onAddress(updated);
                });
            }
            @Override public void onError(String message) {
                l.onError(message);
            }
        });
    }
}
