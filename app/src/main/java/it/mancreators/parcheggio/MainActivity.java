package it.mancreators.parcheggio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.Locale;

/** Schermata principale: interfaccia web interna (mappa + storico) collegata al telefono. */
public class MainActivity extends Activity {

    public static final String ACTION_SHOW_LATEST = "it.mancreators.parcheggio.SHOW_LATEST";
    private static final int REQ_SAVE = 11, REQ_LOCATE = 12;

    private WebView web;
    private boolean pageReady = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#242424"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (u != null && ("http".equals(u.getScheme()) || "https".equals(u.getScheme()))) {
                    startActivity(new Intent(Intent.ACTION_VIEW, u)); // link (es. crediti mappa) nel browser
                    return true;
                }
                return false;
            }
            @Override public void onPageFinished(WebView v, String url) {
                pageReady = true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Native");
        web.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ACTION_SHOW_LATEST.equals(intent.getAction())) js("App.showLatest()");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pageReady) js("App.refresh()");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.App ? App.back() : false", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    private void js(String code) {
        runOnUiThread(() -> web.evaluateJavascript("window.App && (" + code + ")", null));
    }

    private static String q(String s) {
        return JSONObject.quote(s == null ? "" : s);
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void askLocation(int code) {
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, code);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (hasLocation()) {
            if (code == REQ_SAVE) doSave();
            else if (code == REQ_LOCATE) doLocate();
        } else if (code == REQ_SAVE) {
            js("App.onError(" + q("Per salvare il parcheggio serve il permesso alla posizione.") + ")");
        }
    }

    private void doSave() {
        js("App.onSaving()");
        Saver.saveNow(this, new Saver.Listener() {
            @Override public void onSaved(JSONObject spot) { js("App.onSaved(" + spot + ")"); }
            @Override public void onAddress(JSONObject spot) { js("App.onUpdated(" + spot + ")"); }
            @Override public void onError(String message) { js("App.onError(" + q(message) + ")"); }
        });
    }

    private void doLocate() {
        LocationHelper.getCurrent(this, new LocationHelper.Callback() {
            @Override public void onLocation(Location l) {
                js(String.format(Locale.US, "App.onMyLocation(%f,%f,%f)",
                        l.getLatitude(), l.getLongitude(), l.getAccuracy()));
            }
            @Override public void onError(String message) { js("App.onMyLocationError(" + q(message) + ")"); }
        });
    }

    /** Funzioni chiamate dall'interfaccia (window.Native.xxx). */
    private class Bridge {
        @JavascriptInterface public String getSpots() {
            return SpotStore.all(MainActivity.this).toString();
        }

        @JavascriptInterface public void saveNow() {
            runOnUiThread(() -> { if (hasLocation()) doSave(); else askLocation(REQ_SAVE); });
        }

        @JavascriptInterface public void locateMe() {
            runOnUiThread(() -> { if (hasLocation()) doLocate(); else askLocation(REQ_LOCATE); });
        }

        @JavascriptInterface public void deleteSpot(String id) {
            SpotStore.delete(MainActivity.this, id);
            ParkingWidget.updateAll(MainActivity.this);
        }

        @JavascriptInterface public void setNote(String id, String note) {
            SpotStore.update(MainActivity.this, id, "note", note == null ? "" : note);
        }

        @JavascriptInterface public void movePosition(String id, double lat, double lng) {
            SpotStore.update(MainActivity.this, id, "lat", lat);
            SpotStore.update(MainActivity.this, id, "lng", lng);
            SpotStore.update(MainActivity.this, id, "address", "");
            ParkingWidget.updateAll(MainActivity.this);
            LocationHelper.reverse(MainActivity.this, lat, lng, address -> {
                JSONObject s = SpotStore.update(MainActivity.this, id, "address", address);
                ParkingWidget.updateAll(MainActivity.this);
                if (s != null) js("App.onUpdated(" + s + ")");
            });
        }

        @JavascriptInterface public void navigate(double lat, double lng) {
            runOnUiThread(() -> {
                Uri nav = Uri.parse(String.format(Locale.US, "google.navigation:q=%f,%f&mode=w", lat, lng));
                Intent i = new Intent(Intent.ACTION_VIEW, nav);
                i.setPackage("com.google.android.apps.maps");
                try {
                    startActivity(i);
                } catch (ActivityNotFoundException e) {
                    Uri geo = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f(La mia auto)", lat, lng, lat, lng));
                    try { startActivity(new Intent(Intent.ACTION_VIEW, geo)); }
                    catch (ActivityNotFoundException ignored) {
                        js("App.toast(" + q("Nessuna app di navigazione trovata") + ")");
                    }
                }
            });
        }

        @JavascriptInterface public void share(double lat, double lng, String place) {
            runOnUiThread(() -> {
                String text = "La mia auto è qui"
                        + (place != null && !place.isEmpty() ? " (" + place + ")" : "") + ": "
                        + String.format(Locale.US, "https://maps.google.com/?q=%f,%f", lat, lng);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "Condividi la posizione"));
            });
        }

        @JavascriptInterface public String launchAction() {
            Intent i = getIntent();
            return i != null && ACTION_SHOW_LATEST.equals(i.getAction()) ? "latest" : "";
        }
    }
}
