package com.waldo.modow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

public final class BackupActivity extends Activity {
    public static final String EXTRA_MODE = "mode";
    private static final int REQ_EXPORT = 501;
    private static final int REQ_IMPORT = 502;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) return;

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if ("import".equals(mode)) launchImport();
        else launchExport();
    }

    private void launchExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "W-mode-backup-" + LocalDate.now() + ".json");
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void launchImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            finish();
            return;
        }

        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            try {
                exportBackup(uri);
                toast("Backup exportado correctamente");
            } catch (Exception e) {
                toast("No pude exportar el backup");
            }
            finish();
            return;
        }

        if (requestCode == REQ_IMPORT) {
            try {
                String raw = readText(uri);
                JSONObject backup = new JSONObject(raw);
                validateBackup(backup);
                confirmImport(backup);
            } catch (Exception e) {
                toast("Ese archivo no es un backup válido de W-mode");
                finish();
            }
        }
    }

    private void confirmImport(JSONObject backup) {
        int habits = backup.optJSONArray("habits") == null ? 0 : backup.optJSONArray("habits").length();
        int completions = backup.optJSONArray("completions") == null ? 0 : backup.optJSONArray("completions").length();

        AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Importar backup")
                .setMessage("Esto va a reemplazar los datos actuales de W-mode por " + habits + " tareas y " + completions + " registros de cumplimiento.\n\n¿Continuar?")
                .setNegativeButton("Cancelar", (d, w) -> finish())
                .setPositiveButton("Importar", (d, w) -> {
                    try {
                        restoreBackup(backup);
                        AlarmScheduler.scheduleAll(this);
                        toast("Backup restaurado correctamente");
                    } catch (Exception e) {
                        toast("No pude restaurar el backup");
                    }
                    finish();
                })
                .create();
        dialog.setOnCancelListener(d -> finish());
        dialog.show();
    }

    private void exportBackup(Uri uri) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "W-mode-backup");
        root.put("backup_version", 1);
        root.put("app_version", BuildConfig.VERSION_NAME);
        root.put("created_at", Instant.now().toString());

        JSONArray habits = new JSONArray();
        JSONArray completions = new JSONArray();

        AppDb helper = new AppDb(this);
        SQLiteDatabase db = helper.getReadableDatabase();

        try (Cursor c = db.rawQuery(
                "SELECT id,name,sort_order,active,weekly,weekday,weekdays_mask,notify_enabled,notify_hour,notify_minute,sound_uri,created_day FROM habits ORDER BY sort_order,id",
                null)) {
            while (c.moveToNext()) {
                JSONObject item = new JSONObject();
                item.put("id", c.getLong(0));
                item.put("name", c.getString(1));
                item.put("sort_order", c.getInt(2));
                item.put("active", c.getInt(3));
                item.put("weekly", c.getInt(4));
                item.put("weekday", c.getInt(5));
                item.put("weekdays_mask", c.getInt(6));
                item.put("notify_enabled", c.getInt(7));
                item.put("notify_hour", c.getInt(8));
                item.put("notify_minute", c.getInt(9));
                item.put("sound_uri", c.isNull(10) ? JSONObject.NULL : c.getString(10));
                item.put("created_day", c.getString(11));
                habits.put(item);
            }
        }

        try (Cursor c = db.rawQuery(
                "SELECT habit_id,day,completed_at FROM completions ORDER BY day,habit_id",
                null)) {
            while (c.moveToNext()) {
                JSONObject item = new JSONObject();
                item.put("habit_id", c.getLong(0));
                item.put("day", c.getString(1));
                item.put("completed_at", c.getLong(2));
                completions.put(item);
            }
        }

        helper.close();
        root.put("habits", habits);
        root.put("completions", completions);

        try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt");
             OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
            writer.write(root.toString(2));
            writer.flush();
        }
    }

    private String readText(Uri uri) throws Exception {
        StringBuilder out = new StringBuilder();
        try (InputStream stream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        return out.toString();
    }

    private void validateBackup(JSONObject root) throws Exception {
        if (!"W-mode-backup".equals(root.optString("format"))) throw new IllegalArgumentException("format");
        if (root.optInt("backup_version", -1) != 1) throw new IllegalArgumentException("version");
        JSONArray habits = root.optJSONArray("habits");
        JSONArray completions = root.optJSONArray("completions");
        if (habits == null || completions == null) throw new IllegalArgumentException("arrays");

        for (int i = 0; i < habits.length(); i++) {
            JSONObject h = habits.getJSONObject(i);
            if (h.getLong("id") <= 0) throw new IllegalArgumentException("habit id");
            if (h.getString("name").trim().isEmpty()) throw new IllegalArgumentException("habit name");
            LocalDate.parse(h.getString("created_day"));
        }
        for (int i = 0; i < completions.length(); i++) {
            JSONObject c = completions.getJSONObject(i);
            if (c.getLong("habit_id") <= 0) throw new IllegalArgumentException("completion id");
            LocalDate.parse(c.getString("day"));
        }
    }

    private void restoreBackup(JSONObject root) throws Exception {
        JSONArray habits = root.getJSONArray("habits");
        JSONArray completions = root.getJSONArray("completions");

        AppDb helper = new AppDb(this);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("completions", null, null);
            db.delete("habits", null, null);

            for (int i = 0; i < habits.length(); i++) {
                JSONObject h = habits.getJSONObject(i);
                ContentValues values = new ContentValues();
                values.put("id", h.getLong("id"));
                values.put("name", h.getString("name"));
                values.put("sort_order", h.optInt("sort_order", i));
                values.put("active", h.optInt("active", 1));
                values.put("weekly", h.optInt("weekly", 0));
                values.put("weekday", h.optInt("weekday", 1));
                values.put("weekdays_mask", h.optInt("weekdays_mask", 0));
                values.put("notify_enabled", h.optInt("notify_enabled", 0));
                values.put("notify_hour", h.optInt("notify_hour", 9));
                values.put("notify_minute", h.optInt("notify_minute", 0));
                if (h.isNull("sound_uri")) values.putNull("sound_uri");
                else values.put("sound_uri", h.optString("sound_uri", null));
                values.put("created_day", h.getString("created_day"));
                if (db.insertOrThrow("habits", null, values) == -1) throw new IllegalStateException("habit insert");
            }

            for (int i = 0; i < completions.length(); i++) {
                JSONObject c = completions.getJSONObject(i);
                ContentValues values = new ContentValues();
                values.put("habit_id", c.getLong("habit_id"));
                values.put("day", c.getString("day"));
                values.put("completed_at", c.optLong("completed_at", System.currentTimeMillis()));
                if (db.insertOrThrow("completions", null, values) == -1) throw new IllegalStateException("completion insert");
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            helper.close();
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
