package it.mancreators.parcheggio;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

/** Aperta dal widget: mostra una piccola scheda, salva la posizione e si chiude da sola. */
public class SaveActivity extends Activity {

    private static final int REQ = 7;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView title, detail;
    private ProgressBar progress;
    private View done;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_save);
        title = findViewById(R.id.s_title);
        detail = findViewById(R.id.s_detail);
        progress = findViewById(R.id.s_progress);
        done = findViewById(R.id.s_done);
        findViewById(R.id.s_root).setOnClickListener(x -> finish());
        findViewById(R.id.s_card).setOnClickListener(x -> {
            Intent i = new Intent(this, MainActivity.class);
            i.setAction(MainActivity.ACTION_SHOW_LATEST);
            startActivity(i);
            finish();
        });

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            start();
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean ok = false;
        for (int r : results) ok |= r == PackageManager.PERMISSION_GRANTED;
        if (ok) start();
        else showError("Per salvare il parcheggio serve il permesso alla posizione.");
    }

    private void start() {
        title.setText("Cerco la posizione…");
        detail.setText("Un attimo, sto agganciando il GPS");
        Saver.saveNow(this, new Saver.Listener() {
            @Override public void onSaved(JSONObject spot) {
                progress.setVisibility(View.GONE);
                done.setVisibility(View.VISIBLE);
                title.setText("Parcheggio salvato");
                detail.setText(Format.when(spot.optLong("time")) + " · precisione ±" + spot.optInt("acc") + " m");
                buzz();
                handler.postDelayed(SaveActivity.this::finish, 2200);
            }
            @Override public void onAddress(JSONObject spot) {
                if (!isFinishing()) detail.setText(spot.optString("address"));
            }
            @Override public void onError(String message) {
                showError(message);
            }
        });
    }

    private void showError(String msg) {
        progress.setVisibility(View.GONE);
        title.setText("Non salvato");
        detail.setText(msg);
        handler.postDelayed(this::finish, 3500);
    }

    private void buzz() {
        try {
            Vibrator v = getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
