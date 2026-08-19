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
    private static final int DB_VERSION = 3;
    private static final int ALL_WEEKDAYS_MASK = 0x7F;

    public record Habit(
            long id,
            String name,
            int sortOrder,
            boolean active,
            boolean weekly,
            int weekday,
            int weekdaysMask,
            boolean notifyEnabled,
            int notifyHour,
            int notifyMinute,
            String soundUri,
            String createdDay
    ) {
        public int effectiveWeekdaysMask() {
            if (!weekly) return 0;
            int mask = weekdaysMask & ALL_WEEKDAYS_MASK;
            if (mask != 0) return mask;
            int legacyDay = Math.max(1, Math.min(7, weekday));
            return 1 << (legacyDay - 1);
        }

        public boolean dueOn(LocalDate day) {
            LocalDate created;
            try { created = LocalDate.parse(createdDay); }
            catch (Exception ignored) { created = LocalDate.of(1970, 1, 1); }
            if (day.isBefore(created)) return false;
            if (!weekly) return true;
            int bit = 1 << (day.getDayOfWeek().getValue() - 1);
            return (effectiveWeekdaysMask() & bit) != 0;
        }
    }

    public record Stats(int done, int possible, int perfectDays, int trackedDays, int streak) {}

    public AppDb(Context context) { super(context, "modo_w.db", null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE habits(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "sort_order INTEGER NOT NULL," +
                "active INTEGER NOT NULL DEFAULT 1," +
                "weekly INTEGER NOT NULL DEFAULT 0," +
                "weekday INTEGER NOT NULL DEFAULT 1," +
                "weekdays_mask INTEGER NOT NULL DEFAULT 0," +
                "notify_enabled INTEGER NOT NULL DEFAULT 0," +
                "notify_hour INTEGER NOT NULL DEFAULT 9," +
                "notify_minute INTEGER NOT NULL DEFAULT 0," +
                "sound_uri TEXT," +
                "created_day TEXT NOT NULL DEFAULT '1970-01-01')");
        db.execSQL("CREATE TABLE completions(habit_id INTEGER NOT NULL, day TEXT NOT NULL, completed_at INTEGER NOT NULL, PRIMARY KEY(habit_id, day))");
        insertHabit(db, "Tomar Supradyn Forte", 0);
        insertHabit(db, "Tomar creatina", 1);
        insertHabit(db, "Ir al gym", 2);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE habits ADD COLUMN weekly INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE habits ADD COLUMN weekday INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE habits ADD COLUMN notify_enabled INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE habits ADD COLUMN notify_hour INTEGER NOT NULL DEFAULT 9");
            db.execSQL("ALTER TABLE habits ADD COLUMN notify_minute INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE habits ADD COLUMN sound_uri TEXT");
            db.execSQL("ALTER TABLE habits ADD COLUMN created_day TEXT NOT NULL DEFAULT '1970-01-01'");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE habits ADD COLUMN weekdays_mask INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE habits SET weekdays_mask = CASE WHEN weekly=1 THEN (1 << (weekday-1)) ELSE 0 END");
        }
    }

    private static void insertHabit(SQLiteDatabase db, String name, int order) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("sort_order", order);
        v.put("active", 1);
        v.put("created_day", LocalDate.now().toString());
        db.insert("habits", null, v);
    }

    private static Habit fromCursor(Cursor c) {
        return new Habit(
                c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3) == 1,
                c.getInt(4) == 1, c.getInt(5), c.getInt(6), c.getInt(7) == 1,
                c.getInt(8), c.getInt(9), c.isNull(10) ? null : c.getString(10), c.getString(11)
        );
    }

    private static final String HABIT_SELECT =
            "SELECT id,name,sort_order,active,weekly,weekday,weekdays_mask,notify_enabled,notify_hour,notify_minute,sound_uri,created_day FROM habits ";

    public List<Habit> habits(boolean activeOnly) {
        List<Habit> out = new ArrayList<>();
        String where = activeOnly ? "WHERE active=1 " : "";
        try (Cursor c = getReadableDatabase().rawQuery(
                HABIT_SELECT + where + "ORDER BY sort_order,id", null)) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    public List<Habit> habitsDueOn(LocalDate day, boolean activeOnly) {
        List<Habit> out = new ArrayList<>();
        for (Habit h : habits(activeOnly)) if (h.dueOn(day)) out.add(h);
        return out;
    }

    public Habit habit(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                HABIT_SELECT + "WHERE id=? LIMIT 1", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public boolean isDone(long habitId, LocalDate day) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM completions WHERE habit_id=? AND day=? LIMIT 1",
                new String[]{String.valueOf(habitId), day.toString()})) {
            return c.moveToFirst();
        }
    }

    public void complete(long habitId, LocalDate day) {
        ContentValues v = new ContentValues();
        v.put("habit_id", habitId);
        v.put("day", day.toString());
        v.put("completed_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("completions", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public long addHabit(String name, boolean weekly, int weekdaysMask, boolean notifyEnabled, int hour, int minute, String soundUri) {
        int next = 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(MAX(sort_order),-1)+1 FROM habits", null)) {
            if (c.moveToFirst()) next = c.getInt(0);
        }
        ContentValues v = habitValues(name, weekly, weekdaysMask, notifyEnabled, hour, minute, soundUri);
        v.put("sort_order", next);
        v.put("active", 1);
        v.put("created_day", LocalDate.now().toString());
        return getWritableDatabase().insert("habits", null, v);
    }

    public void updateHabit(long id, String name, boolean weekly, int weekdaysMask, boolean notifyEnabled, int hour, int minute, String soundUri) {
        getWritableDatabase().update("habits", habitValues(name, weekly, weekdaysMask, notifyEnabled, hour, minute, soundUri),
                "id=?", new String[]{String.valueOf(id)});
    }

    private ContentValues habitValues(String name, boolean weekly, int weekdaysMask, boolean notifyEnabled, int hour, int minute, String soundUri) {
        ContentValues v = new ContentValues();
        int cleanMask = weekly ? (weekdaysMask & ALL_WEEKDAYS_MASK) : 0;
        v.put("name", name.trim());
        v.put("weekly", weekly ? 1 : 0);
        v.put("weekdays_mask", cleanMask);
        v.put("weekday", firstWeekday(cleanMask));
        v.put("notify_enabled", notifyEnabled ? 1 : 0);
        v.put("notify_hour", Math.max(0, Math.min(23, hour)));
        v.put("notify_minute", Math.max(0, Math.min(59, minute)));
        if (soundUri == null || soundUri.isBlank()) v.putNull("sound_uri"); else v.put("sound_uri", soundUri);
        return v;
    }

    private static int firstWeekday(int mask) {
        for (int day = 1; day <= 7; day++) if ((mask & (1 << (day - 1))) != 0) return day;
        return 1;
    }

    public void setActive(long id, boolean active) {
        ContentValues v = new ContentValues();
        v.put("active", active ? 1 : 0);
        getWritableDatabase().update("habits", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteHabit(long id) {
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            String[] args={String.valueOf(id)};
            db.delete("completions","habit_id=?",args);
            db.delete("habits","id=?",args);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void move(long id, int delta) {
        List<Habit> list = habits(true);
        int index = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id() == id) index = i;
        int target = index + delta;
        if (index < 0 || target < 0 || target >= list.size()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues a = new ContentValues();
            a.put("sort_order", list.get(target).sortOrder());
            db.update("habits", a, "id=?", new String[]{String.valueOf(id)});
            ContentValues b = new ContentValues();
            b.put("sort_order", list.get(index).sortOrder());
            db.update("habits", b, "id=?", new String[]{String.valueOf(list.get(target).id())});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private int doneForDay(LocalDate day, List<Habit> due) {
        int done = 0;
        for (Habit h : due) if (isDone(h.id(), day)) done++;
        return done;
    }

    public boolean isDayComplete(LocalDate day) {
        List<Habit> due = habitsDueOn(day, true);
        return !due.isEmpty() && doneForDay(day, due) == due.size();
    }

    public Stats stats(LocalDate from, LocalDate to) {
        List<Habit> active = habits(true);
        int possible = 0, done = 0, perfect = 0, trackedDays = 0;

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<Habit> due = new ArrayList<>();
            for (Habit h : active) if (h.dueOn(d)) due.add(h);
            if (due.isEmpty()) continue;
            trackedDays++;
            possible += due.size();
            int dayDone = doneForDay(d, due);
            done += dayDone;
            if (dayDone == due.size()) perfect++;
        }

        int streak = 0;
        LocalDate d = MainActivity.operationalDay();
        int safety = 0;
        while (safety++ < 3660 && !active.isEmpty()) {
            List<Habit> due = new ArrayList<>();
            for (Habit h : active) if (h.dueOn(d)) due.add(h);
            if (!due.isEmpty()) {
                if (doneForDay(d, due) != due.size()) break;
                streak++;
            }
            d = d.minusDays(1);
        }
        return new Stats(done, possible, perfect, trackedDays, streak);
    }
}
