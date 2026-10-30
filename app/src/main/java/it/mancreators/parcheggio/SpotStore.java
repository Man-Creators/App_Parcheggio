package it.mancreators.parcheggio;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Archivio dei parcheggi salvati sul telefono (il più recente è il primo). */
public final class SpotStore {
    private static final String PREFS = "parcheggi";
    private static final String KEY = "spots";
    private static final int MAX = 50;

    private SpotStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized JSONArray all(Context c) {
        try {
            return new JSONArray(prefs(c).getString(KEY, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void write(Context c, JSONArray arr) {
        prefs(c).edit().putString(KEY, arr.toString()).apply();
    }

    public static synchronized JSONObject latest(Context c) {
        JSONArray arr = all(c);
        return arr.length() > 0 ? arr.optJSONObject(0) : null;
    }

    public static synchronized JSONObject add(Context c, double lat, double lng, float acc) {
        JSONArray old = all(c);
        JSONArray arr = new JSONArray();
        JSONObject s = new JSONObject();
        try {
            long now = System.currentTimeMillis();
            s.put("id", String.valueOf(now));
            s.put("lat", lat);
            s.put("lng", lng);
            s.put("acc", Math.round(acc));
            s.put("time", now);
            s.put("address", "");
            s.put("note", "");
        } catch (JSONException ignored) {}
        arr.put(s);
        for (int i = 0; i < old.length() && arr.length() < MAX; i++) arr.put(old.opt(i));
        write(c, arr);
        return s;
    }

    /** Aggiorna un campo di un parcheggio; restituisce il parcheggio aggiornato o null. */
    public static synchronized JSONObject update(Context c, String id, String field, Object value) {
        JSONArray arr = all(c);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s != null && id.equals(s.optString("id"))) {
                try { s.put(field, value); } catch (JSONException ignored) {}
                write(c, arr);
                return s;
            }
        }
        return null;
    }

    public static synchronized void delete(Context c, String id) {
        JSONArray arr = all(c);
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s != null && !id.equals(s.optString("id"))) out.put(s);
        }
        write(c, out);
    }
}
