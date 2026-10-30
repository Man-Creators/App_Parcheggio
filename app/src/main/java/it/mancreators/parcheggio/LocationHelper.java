package it.mancreators.parcheggio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.Locale;

/** Prende una posizione precisa una sola volta (niente servizi in background). */
public final class LocationHelper {

    public interface Callback {
        void onLocation(Location location);
        void onError(String message);
    }

    public interface AddressCallback {
        void onAddress(String address);
    }

    private static final long TIMEOUT_MS = 15000;
    private static final float GOOD_ACCURACY = 20f;

    private LocationHelper() {}

    @SuppressLint("MissingPermission")
    public static void getCurrent(Context ctx, Callback cb) {
        final LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        final Handler main = new Handler(Looper.getMainLooper());
        boolean gps = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean net = lm != null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gps && !net) {
            cb.onError("La posizione del telefono è spenta: attivala e riprova.");
            return;
        }

        final Location[] best = {null};
        final boolean[] done = {false};
        final LocationListener[] holder = new LocationListener[1];

        final Runnable finish = () -> {
            if (done[0]) return;
            done[0] = true;
            try { lm.removeUpdates(holder[0]); } catch (Exception ignored) {}
            Location result = best[0];
            if (result == null) result = recentLastKnown(lm);
            if (result != null) cb.onLocation(result);
            else cb.onError("Non riesco a trovare la posizione. Spostati all'aperto e riprova.");
        };

        holder[0] = new LocationListener() {
            @Override public void onLocationChanged(Location l) {
                if (done[0]) return;
                if (best[0] == null || l.getAccuracy() < best[0].getAccuracy()) best[0] = l;
                if (LocationManager.GPS_PROVIDER.equals(l.getProvider()) && l.getAccuracy() <= GOOD_ACCURACY) {
                    main.post(finish);
                }
            }
            @Override public void onStatusChanged(String p, int s, Bundle b) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };

        try {
            if (gps) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, holder[0], Looper.getMainLooper());
            if (net) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, holder[0], Looper.getMainLooper());
        } catch (SecurityException e) {
            cb.onError("Serve il permesso di accesso alla posizione.");
            return;
        }
        main.postDelayed(finish, TIMEOUT_MS);
    }

    /** Ultima posizione nota se è recente (meno di 2 minuti): utile nei garage. */
    @SuppressLint("MissingPermission")
    private static Location recentLastKnown(LocationManager lm) {
        Location best = null;
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && System.currentTimeMillis() - l.getTime() < 120000
                        && (best == null || l.getAccuracy() < best.getAccuracy())) best = l;
            } catch (Exception ignored) {}
        }
        return best;
    }

    /** Cerca l'indirizzo (via e comune) in un thread separato. Restituisce "" se non lo trova. */
    public static void reverse(Context ctx, double lat, double lng, AddressCallback cb) {
        final Context app = ctx.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String text = "";
            try {
                if (Geocoder.isPresent()) {
                    @SuppressWarnings("deprecation")
                    List<Address> list = new Geocoder(app, Locale.ITALY).getFromLocation(lat, lng, 1);
                    if (list != null && !list.isEmpty()) text = format(list.get(0));
                }
            } catch (Exception ignored) {}
            final String result = text;
            main.post(() -> cb.onAddress(result));
        }).start();
    }

    private static String format(Address a) {
        StringBuilder sb = new StringBuilder();
        if (a.getThoroughfare() != null) {
            sb.append(a.getThoroughfare());
            if (a.getSubThoroughfare() != null) sb.append(' ').append(a.getSubThoroughfare());
        } else if (a.getFeatureName() != null) {
            sb.append(a.getFeatureName());
        }
        String city = a.getLocality() != null ? a.getLocality() : a.getSubAdminArea();
        if (city != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        if (sb.length() == 0 && a.getMaxAddressLineIndex() >= 0) sb.append(a.getAddressLine(0));
        return sb.toString();
    }
}
