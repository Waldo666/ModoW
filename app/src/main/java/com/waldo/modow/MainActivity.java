package com.waldo.modow;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int BG = Color.rgb(11,13,18), CARD = Color.rgb(26,30,39), TEXT = Color.rgb(240,244,248), MUTED = Color.rgb(142,151,163), ACCENT = Color.rgb(168,255,96), DONE = Color.rgb(90,95,103);
    private static final int REQ_RINGTONE = 301;
    private static final int REQ_NOTIFICATIONS = 302;

    private AppDb db;
    private LinearLayout root, content, nav;
    private final Handler handler = new Handler();
    private String tab = "today";
    private String pendingSoundUri;
    private Button pendingToneButton;
    private boolean askExactAfterNotificationPermission;

    public static LocalDate operationalDay() {
        LocalDateTime now = LocalDateTime.now();
        return (now.getHour()==0 && now.getMinute()==0) ? now.toLocalDate().minusDays(1) : now.toLocalDate();
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = new AppDb(this);
        AlarmScheduler.scheduleAll(this);
        splash();
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemBarsCompat();
        if (db != null) AlarmScheduler.scheduleAll(this);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBarsCompat();
    }

    private void hideSystemBarsCompat() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void splash() {
        root = column(BG); root.setGravity(Gravity.CENTER); setContentView(root); root.post(this::hideSystemBarsCompat);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_leoric);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(190),dp(285));
        logo.setScaleX(.35f); logo.setScaleY(.35f); logo.setAlpha(0f); root.addView(logo,lp);
        TextView t = text("MODO W", 34, TEXT, true); t.setLetterSpacing(.24f); t.setAlpha(0); root.addView(t);
        TextView s = text("DISCIPLINA QUE SE VE", 12, ACCENT, true); s.setLetterSpacing(.18f); s.setAlpha(0); root.addView(s);
        logo.animate().alpha(1).scaleX(1).scaleY(1).rotationBy(360).setDuration(850).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        t.animate().alpha(1).translationY(-6).setStartDelay(380).setDuration(600).start();
        s.animate().alpha(1).setStartDelay(650).setDuration(500).start();
        handler.postDelayed(this::buildShell, 1650);
    }

    private void buildShell() {
        root = column(BG); setContentView(root); root.post(this::hideSystemBarsCompat);
        LinearLayout head = row(BG); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(20),dp(26),dp(20),dp(8));
        ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.app_icon_leoric); icon.setScaleType(ImageView.ScaleType.CENTER_CROP); head.addView(icon,new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout titles = column(BG); titles.setPadding(dp(10),0,0,0); titles.addView(text("MODO W",22,TEXT,true)); titles.addView(text("Tu sistema. Tus reglas.",12,MUTED,false)); head.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); root.addView(head);
        ScrollView scroll = new ScrollView(this); content = column(BG); content.setPadding(dp(18),dp(8),dp(18),dp(100)); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav = row(Color.rgb(16,19,25)); nav.setPadding(dp(8),dp(8),dp(8),dp(10)); root.addView(nav,new LinearLayout.LayoutParams(-1,dp(78)));
        showToday(); scheduleBoundaryRefresh();
    }

    private void scheduleBoundaryRefresh() {
        handler.postDelayed(new Runnable(){ public void run(){ if (tab.equals("today")) showToday(); handler.postDelayed(this,60_000); }},60_000);
    }

    private void setNav(String selected) {
        nav.removeAllViews();
        nav.addView(navButton("HOY", "today", selected), new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navButton("HISTORIAL", "stats", selected), new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navButton("CONFIG", "config", selected), new LinearLayout.LayoutParams(0,-1,1));
    }

    private Button navButton(String label, String id, String selected) {
        Button b = button(label, id.equals(selected)?ACCENT:MUTED, Color.TRANSPARENT);
        b.setTextSize(11);
        b.setOnClickListener(v->{ if(id.equals("today"))showToday(); else if(id.equals("stats"))showStats("week"); else showConfig(); });
        return b;
    }

    private void showToday() {
        tab="today"; setNav(tab); content.removeAllViews(); LocalDate day=operationalDay();
        content.addView(text(day.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM",new Locale("es","AR"))).toUpperCase(),13,ACCENT,true));
        TextView h=text("Modo configuración",30,TEXT,true); h.setPadding(0,dp(4),0,dp(4)); content.addView(h);
        List<AppDb.Habit> due = db.habitsDueOn(day, true);
        int total=due.size(), done=0; for(AppDb.Habit habit:due) if(db.isDone(habit.id(),day)) done++;
        content.addView(progressCard(done,total)); space(content,14);
        for(AppDb.Habit habit:due) content.addView(habitCard(habit,day));
        if(total==0) content.addView(empty("No hay tareas programadas para hoy."));
    }

    private View progressCard(int done,int total){
        LinearLayout c=card(); c.setPadding(dp(18),dp(16),dp(18),dp(16));
        TextView big=text(total==0?"0%":Math.round(done*100f/total)+"%",36,TEXT,true); c.addView(big);
        c.addView(text(done+" de "+total+" completados",14,MUTED,false));
        LinearLayout bar=row(Color.rgb(48,54,64)); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(9)); bp.topMargin=dp(12); c.addView(bar,bp);
        View fill=new View(this); fill.setBackgroundColor(ACCENT); bar.addView(fill,new LinearLayout.LayoutParams(0,dp(9),done));
        if(total-done>0) bar.addView(new View(this),new LinearLayout.LayoutParams(0,dp(9),total-done));
        return c;
    }

    private View habitCard(AppDb.Habit h,LocalDate day){
        boolean done=db.isDone(h.id(),day); LinearLayout c=card(); c.setOrientation(LinearLayout.HORIZONTAL); c.setGravity(Gravity.CENTER_VERTICAL); c.setPadding(dp(18),dp(14),dp(14),dp(14));
        LinearLayout info=column(Color.TRANSPARENT);
        TextView name=text(h.name(),18,done?DONE:TEXT,true); if(done) name.setPaintFlags(name.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG); info.addView(name);
        String detail = todayDetail(h);
        if (!detail.isEmpty()) info.addView(text(detail,12,done?DONE:MUTED,false));
        c.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        TextView mark=text(done?"✓":"○",32,done?DONE:ACCENT,true); c.addView(mark,new LinearLayout.LayoutParams(dp(46),dp(46)));
        if(!done) c.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("¿Marcar como cumplido?").setMessage("Después no se puede destildar hasta el próximo día en que corresponda esta tarea.").setNegativeButton("Cancelar",null).setPositiveButton("Sí, cumplido",(d,w)->{db.complete(h.id(),day); showToday();}).show());
        else { c.setAlpha(.58f); c.setOnClickListener(v->toast("Esta tarea ya quedó cumplida")); }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(10); c.setLayoutParams(lp); return c;
    }

    private String todayDetail(AppDb.Habit h) {
        String out = h.weekly() ? "Semanal · " + dayName(h.weekday()).toLowerCase(new Locale("es","AR")) : "";
        if (h.notifyEnabled()) out += (out.isEmpty()?"":" · ") + "🔔 " + timeText(h.notifyHour(), h.notifyMinute());
        return out;
    }

    private void showStats(String period){
        tab="stats"; setNav(tab); content.removeAllViews(); content.addView(text("CUMPLIMIENTO",13,ACCENT,true)); content.addView(text("Tu registro",30,TEXT,true));
        LinearLayout tabs=row(BG); tabs.addView(periodButton("SEMANA","week",period),new LinearLayout.LayoutParams(0,dp(48),1)); tabs.addView(periodButton("MES","month",period),new LinearLayout.LayoutParams(0,dp(48),1)); tabs.addView(periodButton("AÑO","year",period),new LinearLayout.LayoutParams(0,dp(48),1)); content.addView(tabs);
        LocalDate to=operationalDay(), from;
        if(period.equals("week")) from=to.with(DayOfWeek.MONDAY); else if(period.equals("month")) from=YearMonth.from(to).atDay(1); else from=LocalDate.of(to.getYear(),1,1);
        AppDb.Stats s=db.stats(from,to); int pct=s.possible()==0?0:Math.round(s.done()*100f/s.possible());
        space(content,16); content.addView(metric("CUMPLIMIENTO",pct+"%",s.done()+" de "+s.possible()+" acciones programadas"));
        LinearLayout pair=row(BG); pair.addView(metric("DÍAS PERFECTOS",String.valueOf(s.perfectDays()),"de "+s.trackedDays()+" días con tareas"),new LinearLayout.LayoutParams(0,-2,1)); space(pair,10); pair.addView(metric("RACHA ACTUAL",s.streak()+" días","jornadas programadas completas"),new LinearLayout.LayoutParams(0,-2,1)); content.addView(pair);
        space(content,14); content.addView(text("Período: "+from.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))+" — "+to.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),13,MUTED,false));
    }

    private Button periodButton(String label,String id,String selected){ Button b=button(label,id.equals(selected)?BG:CARD,id.equals(selected)?ACCENT:Color.TRANSPARENT); b.setOnClickListener(v->showStats(id)); return b; }
    private View metric(String label,String value,String sub){ LinearLayout c=card(); c.setPadding(dp(16),dp(16),dp(16),dp(16)); c.addView(text(label,11,ACCENT,true)); c.addView(text(value,30,TEXT,true)); c.addView(text(sub,12,MUTED,false)); return c; }

    private void showConfig(){
        tab="config"; setNav(tab); content.removeAllViews(); content.addView(text("CONFIGURACIÓN",13,ACCENT,true)); content.addView(text("Tus hábitos",30,TEXT,true));
        Button add=button("＋ AGREGAR HÁBITO",BG,ACCENT); add.setOnClickListener(v->editDialog(null)); content.addView(add,new LinearLayout.LayoutParams(-1,dp(52))); space(content,14);
        for(AppDb.Habit h:db.habits(false)) content.addView(configCard(h));
        TextView note=text("Podés hacer una tarea diaria o semanal y agregarle un aviso con hora y tono. Los hábitos retirados conservan su historial.",12,MUTED,false); note.setPadding(0,dp(14),0,0); content.addView(note);
    }

    private View configCard(AppDb.Habit h){
        LinearLayout c=card(); c.setOrientation(LinearLayout.HORIZONTAL); c.setGravity(Gravity.CENTER_VERTICAL); c.setPadding(dp(14),dp(10),dp(8),dp(10)); c.setAlpha(h.active()?1f:.5f);
        LinearLayout info=column(Color.TRANSPARENT);
        info.addView(text(h.name(),16,TEXT,true));
        info.addView(text(configDetail(h),11,MUTED,false));
        c.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        Button up=button("↑",TEXT,Color.TRANSPARENT); up.setOnClickListener(v->{db.move(h.id(),-1);showConfig();}); c.addView(up,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Button down=button("↓",TEXT,Color.TRANSPARENT); down.setOnClickListener(v->{db.move(h.id(),1);showConfig();}); c.addView(down,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Button edit=button("✎",ACCENT,Color.TRANSPARENT); edit.setOnClickListener(v->editDialog(h)); c.addView(edit,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Button active=button(h.active()?"×":"＋",h.active()?Color.rgb(255,120,120):ACCENT,Color.TRANSPARENT);
        active.setOnClickListener(v->{
            boolean next=!h.active(); db.setActive(h.id(),next); AppDb.Habit updated=db.habit(h.id());
            if(updated!=null && updated.active() && updated.notifyEnabled()) AlarmScheduler.scheduleHabit(this,updated); else AlarmScheduler.cancelHabit(this,h.id());
            showConfig();
        });
        c.addView(active,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(9); c.setLayoutParams(lp); return c;
    }

    private String configDetail(AppDb.Habit h) {
        String schedule = h.weekly() ? "Todos los " + dayName(h.weekday()).toLowerCase(new Locale("es","AR")) : "Todos los días";
        if (h.notifyEnabled()) schedule += " · 🔔 " + timeText(h.notifyHour(),h.notifyMinute());
        return schedule;
    }

    private void editDialog(AppDb.Habit h){
        boolean isNew = h == null;
        int initialWeekday = isNew ? DayOfWeek.FRIDAY.getValue() : h.weekday();
        int initialHour = isNew ? 9 : h.notifyHour();
        int initialMinute = isNew ? 0 : h.notifyMinute();
        pendingSoundUri = (!isNew && h.soundUri()!=null && !h.soundUri().isBlank())
                ? h.soundUri() : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString();

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = column(Color.TRANSPARENT);
        form.setPadding(dp(22),dp(8),dp(22),dp(4));
        scroll.addView(form);

        form.addView(text("NOMBRE",11,MUTED,true));
        EditText e=new EditText(this); e.setText(isNew?"":h.name()); e.setSingleLine(true); e.setSelectAllOnFocus(true); e.setTextColor(TEXT); e.setHintTextColor(MUTED); e.setHint("Ej: Tomar creatina");
        form.addView(e,new LinearLayout.LayoutParams(-1,dp(54)));
        space(form,10);

        CheckBox weekly = check("Tarea semanal"); weekly.setChecked(!isNew && h.weekly()); form.addView(weekly);
        Spinner day = new Spinner(this);
        String[] days={"Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,days); day.setAdapter(adapter); day.setSelection(initialWeekday-1);
        day.setVisibility(weekly.isChecked()?View.VISIBLE:View.GONE); form.addView(day,new LinearLayout.LayoutParams(-1,dp(52)));
        weekly.setOnCheckedChangeListener((b,checked)->day.setVisibility(checked?View.VISIBLE:View.GONE));
        space(form,8);

        CheckBox notify = check("Notificar"); notify.setChecked(!isNew && h.notifyEnabled()); form.addView(notify);
        final int[] pickedTime={initialHour,initialMinute};
        Button time=button("Hora · "+timeText(initialHour,initialMinute),TEXT,CARD);
        time.setOnClickListener(v->new TimePickerDialog(this,(view,hour,minute)->{pickedTime[0]=hour;pickedTime[1]=minute;time.setText("Hora · "+timeText(hour,minute));},pickedTime[0],pickedTime[1],true).show());
        form.addView(time,new LinearLayout.LayoutParams(-1,dp(50)));

        Button tone=button(toneLabel(pendingSoundUri),TEXT,CARD); pendingToneButton=tone;
        tone.setOnClickListener(v->openRingtonePicker());
        form.addView(tone,new LinearLayout.LayoutParams(-1,dp(50)));
        time.setVisibility(notify.isChecked()?View.VISIBLE:View.GONE); tone.setVisibility(notify.isChecked()?View.VISIBLE:View.GONE);
        notify.setOnCheckedChangeListener((b,checked)->{time.setVisibility(checked?View.VISIBLE:View.GONE);tone.setVisibility(checked?View.VISIBLE:View.GONE);});

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(isNew?"Nuevo hábito":"Editar hábito")
                .setView(scroll)
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Guardar",null)
                .create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=e.getText().toString().trim();
            if(n.isEmpty()){e.setError("Poné un nombre");return;}
            boolean isWeekly=weekly.isChecked(); int weekday=day.getSelectedItemPosition()+1; boolean wantsNotify=notify.isChecked();
            long id;
            if(isNew) id=db.addHabit(n,isWeekly,weekday,wantsNotify,pickedTime[0],pickedTime[1],pendingSoundUri);
            else { id=h.id(); db.updateHabit(id,n,isWeekly,weekday,wantsNotify,pickedTime[0],pickedTime[1],pendingSoundUri); }
            AppDb.Habit saved=db.habit(id);
            if(saved!=null && saved.active() && saved.notifyEnabled()) AlarmScheduler.scheduleHabit(this,saved); else AlarmScheduler.cancelHabit(this,id);
            dialog.dismiss(); showConfig();
            if(wantsNotify) requestNotificationAccessIfNeeded();
        }));
        dialog.setOnDismissListener(x->{pendingToneButton=null;});
        dialog.show();
    }

    private CheckBox check(String label) {
        CheckBox c=new CheckBox(this); c.setText(label); c.setTextColor(TEXT); c.setTextSize(15); c.setPadding(0,dp(6),0,dp(6)); return c;
    }

    private void openRingtonePicker() {
        Intent i=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_NOTIFICATION);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"Tono de Modo W");
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        if(pendingSoundUri!=null) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,Uri.parse(pendingSoundUri));
        startActivityForResult(i,REQ_RINGTONE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_RINGTONE && resultCode==RESULT_OK && data!=null) {
            Uri picked=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if(picked!=null) { pendingSoundUri=picked.toString(); if(pendingToneButton!=null) pendingToneButton.setText(toneLabel(pendingSoundUri)); }
        }
    }

    private String toneLabel(String uriString) {
        try {
            Uri uri=Uri.parse(uriString); Ringtone ringtone=RingtoneManager.getRingtone(this,uri);
            String title=ringtone==null?"Predeterminado":ringtone.getTitle(this);
            return "Tono · "+title;
        } catch(Exception ignored) { return "Tono · Predeterminado"; }
    }

    private void requestNotificationAccessIfNeeded() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            askExactAfterNotificationPermission=true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);
        } else requestExactAlarmAccessIfNeeded();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_NOTIFICATIONS && askExactAfterNotificationPermission) {
            askExactAfterNotificationPermission=false;
            if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) requestExactAlarmAccessIfNeeded();
            else toast("Sin permiso de notificaciones no puedo avisarte");
        }
    }

    private void requestExactAlarmAccessIfNeeded() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
        if(am.canScheduleExactAlarms()) return;
        new AlertDialog.Builder(this)
                .setTitle("Respetar el horario")
                .setMessage("Para avisarte lo más cerca posible de la hora elegida, Android puede pedir permiso para Alarmas y recordatorios. Si no lo activás, el aviso igual queda programado pero el sistema puede demorarlo.")
                .setNegativeButton("Ahora no",null)
                .setPositiveButton("Permitir",(d,w)->{
                    try {
                        Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()));
                        startActivity(i);
                    } catch(Exception ignored) { toast("Podés habilitar Alarmas y recordatorios desde Ajustes"); }
                }).show();
    }

    private String dayName(int isoDay) {
        String[] days={"Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo"};
        return days[Math.max(1,Math.min(7,isoDay))-1];
    }

    private String timeText(int hour,int minute) { return String.format(Locale.US,"%02d:%02d",hour,minute); }

    private LinearLayout column(int color){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackgroundColor(color);return l;}
    private LinearLayout row(int color){LinearLayout l=column(color);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout card(){
        LinearLayout l=column(CARD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.rgb(38,44,56));
        l.setBackground(bg);
        return l;
    }
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;}
    private Button button(String s,int textColor,int bg){Button b=new Button(this);b.setText(s);b.setTextColor(textColor);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT_BOLD);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));return b;}
    private TextView empty(String s){TextView t=text(s,15,MUTED,false);t.setGravity(Gravity.CENTER);t.setPadding(dp(20),dp(50),dp(20),dp(50));return t;}
    private void space(LinearLayout l,int d){Space s=new Space(this);l.addView(s,new LinearLayout.LayoutParams(dp(d),dp(d)));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String s){android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_SHORT).show();}
}
