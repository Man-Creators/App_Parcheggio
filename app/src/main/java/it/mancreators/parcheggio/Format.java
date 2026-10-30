package it.mancreators.parcheggio;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import org.json.JSONObject;

final class Format {
    private Format() {}

    /** "oggi 18:42", "ieri 09:10" oppure "12 ott 18:42". */
    static String when(long time) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(time);
        String hour = new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date(time));
        if (sameDay(now, t)) return "oggi " + hour;
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(now, t)) return "ieri " + hour;
        return new SimpleDateFormat("d MMM", Locale.ITALY).format(new Date(time)) + " " + hour;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    static String place(JSONObject spot) {
        String a = spot.optString("address", "");
        if (!a.isEmpty()) return a;
        return String.format(Locale.ITALY, "%.5f, %.5f", spot.optDouble("lat"), spot.optDouble("lng"));
    }
}
