package com.waldo.modow;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class AppDb extends SQLiteOpenHelper {
    public record Habit(long id, String name, int sortOrder, boolean active) {}
    public record Stats(int done, int possible, int perfectDays, int trackedDays, int streak) {}

    public AppDb(Context context) { super(context, "modo_w.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE habits(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, sort_order INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE completions(habit_id INTEGER NOT NULL, day TEXT NOT NULL, completed_at INTEGER NOT NULL, PRIMARY KEY(habit_id, day))");
        insertHabit(db, "Tomar Supradyn Forte", 0);
        insertHabit(db, "Tomar creatina", 1);
        insertHabit(db, "Ir al gym", 2);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    private static void insertHabit(SQLiteDatabase db, String name, int order) {
        ContentValues v = new ContentValues();
        v.put("name", name); v.put("sort_order", order); v.put("active", 1);
        db.insert("habits", null, v);
    }

    public List<Habit> habits(boolean activeOnly) {
        List<Habit> out = new ArrayList<>();
        String where = activeOnly ? "WHERE active=1" : "";
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,sort_order,active FROM habits " + where + " ORDER BY sort_order,id", null)) {
            while (c.moveToNext()) out.add(new Habit(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3) == 1));
        }
        return out;
    }

    public boolean isDone(long habitId, LocalDate day) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM completions WHERE habit_id=? AND day=? LIMIT 1", new String[]{String.valueOf(habitId), day.toString()})) {
            return c.moveToFirst();
        }
    }

    public void complete(long habitId, LocalDate day) {
        ContentValues v = new ContentValues();
        v.put("habit_id", habitId); v.put("day", day.toString()); v.put("completed_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("completions", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public long addHabit(String name) {
        int next = 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(MAX(sort_order),-1)+1 FROM habits", null)) { if (c.moveToFirst()) next = c.getInt(0); }
        ContentValues v = new ContentValues(); v.put("name", name.trim()); v.put("sort_order", next); v.put("active", 1);
        return getWritableDatabase().insert("habits", null, v);
    }

    public void rename(long id, String name) {
        ContentValues v = new ContentValues(); v.put("name", name.trim());
        getWritableDatabase().update("habits", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void setActive(long id, boolean active) {
        ContentValues v = new ContentValues(); v.put("active", active ? 1 : 0);
        getWritableDatabase().update("habits", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void move(long id, int delta) {
        List<Habit> list = habits(true);
        int index = -1;
        for (int i=0;i<list.size();i++) if (list.get(i).id()==id) index=i;
        int target = index + delta;
        if (index < 0 || target < 0 || target >= list.size()) return;
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ContentValues a = new ContentValues(); a.put("sort_order", list.get(target).sortOrder());
            db.update("habits", a, "id=?", new String[]{String.valueOf(id)});
            ContentValues b = new ContentValues(); b.put("sort_order", list.get(index).sortOrder());
            db.update("habits", b, "id=?", new String[]{String.valueOf(list.get(target).id())});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public Stats stats(LocalDate from, LocalDate to) {
        int active = habits(true).size();
        int trackedDays = (int)(to.toEpochDay() - from.toEpochDay() + 1);
        int possible = Math.max(0, active * trackedDays);
        int done = 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM completions c JOIN habits h ON h.id=c.habit_id WHERE h.active=1 AND c.day BETWEEN ? AND ?", new String[]{from.toString(), to.toString()})) {
            if (c.moveToFirst()) done = c.getInt(0);
        }
        int perfect = 0;
        if (active > 0) {
            try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM (SELECT day,COUNT(*) n FROM completions c JOIN habits h ON h.id=c.habit_id WHERE h.active=1 AND day BETWEEN ? AND ? GROUP BY day HAVING n=?)", new String[]{from.toString(), to.toString(), String.valueOf(active)})) {
                if (c.moveToFirst()) perfect = c.getInt(0);
            }
        }
        int streak = 0;
        LocalDate d = MainActivity.operationalDay();
        while (active > 0) {
            int n = 0;
            try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM completions c JOIN habits h ON h.id=c.habit_id WHERE h.active=1 AND day=?", new String[]{d.toString()})) { if (c.moveToFirst()) n=c.getInt(0); }
            if (n != active) break;
            streak++; d=d.minusDays(1);
        }
        return new Stats(done, possible, perfect, trackedDays, streak);
    }
}
