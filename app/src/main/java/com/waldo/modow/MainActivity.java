package com.waldo.modow;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
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
    static final int DIALOG_BG = Color.rgb(35,18,52), DIALOG_STROKE = Color.rgb(122,95,160), DIALOG_SHADOW = Color.rgb(203,178,235);
    private static final int REQ_RINGTONE = 301;
    private static final int REQ_NOTIFICATIONS = 302;

    private AppDb db;
    private LinearLayout root, content, nav;
    private ScrollView screenScroll;
    private final Handler handler = new Handler();
    private String tab = "today";
    private String statsPeriod = "week";
    private String pendingSoundUri;
    private Button pendingToneButton;
    private boolean askExactAfterNotificationPermission;
    private boolean tabTransitionRunning;
    private float touchDownX, touchDownY;

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
        TextView t = text("W-MODE", 34, TEXT, true); t.setLetterSpacing(.24f); t.setAlpha(0); root.addView(t);
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
        LinearLayout titles = column(BG); titles.setPadding(dp(10),0,0,0); titles.addView(text("W-MODE",22,TEXT,true)); titles.addView(text("Tu sistema. Tus reglas.",12,MUTED,false)); head.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); root.addView(head);

        screenScroll = new ScrollView(this);
        screenScroll.setClipToPadding(false);
        screenScroll.setOnTouchListener(this::handleScreenTouch);
        content = column(BG);
        content.setPadding(dp(18),dp(8),dp(18),dp(100));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        screenScroll.addView(content);
        root.addView(screenScroll,new LinearLayout.LayoutParams(-1,0,1));

        nav = row(Color.rgb(16,19,25)); nav.setPadding(dp(8),dp(8),dp(8),dp(10)); root.addView(nav,new LinearLayout.LayoutParams(-1,dp(78)));
        renderToday(true,0);
        scheduleBoundaryRefresh();
    }

    private void scheduleBoundaryRefresh() {
        handler.postDelayed(new Runnable(){ public void run(){ if (tab.equals("today")) renderToday(false,0); handler.postDelayed(this,60_000); }},60_000);
    }

    private boolean handleScreenTouch(View v, MotionEvent event) {
        if(event.getAction()==MotionEvent.ACTION_DOWN) {
            touchDownX=event.getX(); touchDownY=event.getY();
        } else if(event.getAction()==MotionEvent.ACTION_UP) {
            float dx=event.getX()-touchDownX, dy=event.getY()-touchDownY;
            if(Math.abs(dx)>dp(70) && Math.abs(dx)>Math.abs(dy)*1.35f) {
                navigateBySwipe(dx<0 ? 1 : -1);
            }
        }
        return false;
    }

    private void navigateBySwipe(int delta) {
        int next=tabIndex(tab)+delta;
        if(next<0 || next>2) return;
        navigateTo(next==0?"today":next==1?"stats":"config");
    }

    private int tabIndex(String id) {
        if("stats".equals(id)) return 1;
        if("config".equals(id)) return 2;
        return 0;
    }

    private void navigateTo(String target) {
        if(target.equals(tab) || tabTransitionRunning) return;
        int direction=tabIndex(target)>tabIndex(tab)?1:-1;
        tabTransitionRunning=true;
        content.animate().cancel();
        content.animate()
                .translationX(-direction*dp(72))
                .alpha(.12f)
                .scaleX(.985f).scaleY(.985f)
                .setDuration(125)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(()->{
                    if("stats".equals(target)) renderStats(statsPeriod,true,direction);
                    else if("config".equals(target)) renderConfig(-1,0,true,direction);
                    else renderToday(true,direction);
                    tabTransitionRunning=false;
                }).start();
    }

    private void animateScreenIn(int direction) {
        content.animate().cancel();
        content.setAlpha(.08f);
        content.setScaleX(.985f); content.setScaleY(.985f);
        if(direction==0) {
            content.setTranslationX(0);
            content.setTranslationY(dp(20));
        } else {
            content.setTranslationY(0);
            content.setTranslationX(direction*dp(72));
        }
        content.animate().translationX(0).translationY(0).alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(235).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    private void resetContentTransform() {
        content.animate().cancel();
        content.setTranslationX(0); content.setTranslationY(0);
        content.setScaleX(1); content.setScaleY(1); content.setAlpha(1);
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
        b.setOnClickListener(v->navigateTo(id));
        return b;
    }

    private void renderToday(boolean animate,int direction) {
        tab="today"; setNav(tab); resetContentTransform(); content.removeAllViews(); LocalDate day=operationalDay();
        content.addView(text(day.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM",new Locale("es","AR"))).toUpperCase(),13,ACCENT,true));
        TextView h=text("Modo configuración",30,TEXT,true); h.setPadding(0,dp(4),0,dp(4)); content.addView(h);
        List<AppDb.Habit> due = db.habitsDueOn(day, true);
        int total=due.size(), done=0; for(AppDb.Habit habit:due) if(db.isDone(habit.id(),day)) done++;
        content.addView(progressCard(done,total)); space(content,14);
        for(AppDb.Habit habit:due) content.addView(habitCard(habit,day));
        if(total==0) content.addView(empty("No hay tareas programadas para hoy."));
        if(animate) animateScreenIn(direction);
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
        if(!done) c.setOnClickListener(v->showCompleteDialog(h,day));
        else { c.setAlpha(.58f); c.setOnClickListener(v->toast("Esta tarea ya quedó cumplida")); }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(10); c.setLayoutParams(lp); return c;
    }

    private void showCompleteDialog(AppDb.Habit h, LocalDate day) {
        AlertDialog dialog=styledBuilder()
                .setTitle("¿Marcar como cumplido?")
                .setMessage("Después no se puede destildar hasta el próximo día en que corresponda esta tarea.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Sí, cumplido",(d,w)->{
                    db.complete(h.id(),day);
                    boolean dayComplete=db.isDayComplete(day);
                    renderToday(false,0);
                    if(dayComplete) handler.postDelayed(this::celebrateDay,140);
                })
                .create();
        dialog.setOnShowListener(x->styleDialog(dialog));
        dialog.show();
    }

    private void celebrateDay() {
        View decor=getWindow().getDecorView();
        if(!(decor instanceof ViewGroup)) return;
        ViewGroup group=(ViewGroup)decor;
        ConfettiView confetti=new ConfettiView(this);
        group.addView(confetti,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        confetti.bringToFront();
        confetti.start(()->{
            if(confetti.getParent() instanceof ViewGroup) ((ViewGroup)confetti.getParent()).removeView(confetti);
        });
    }

    private String todayDetail(AppDb.Habit h) {
        String out = h.weekly() ? "Semanal · " + daysSummary(h.effectiveWeekdaysMask()) : "";
        if (h.notifyEnabled()) out += (out.isEmpty()?"":" · ") + "🔔 " + timeText(h.notifyHour(), h.notifyMinute());
        return out;
    }

    private void renderStats(String period,boolean animate,int direction){
        statsPeriod=period; tab="stats"; setNav(tab); resetContentTransform(); content.removeAllViews();
        TextView kicker=centeredText("CUMPLIMIENTO",13,ACCENT,true); content.addView(kicker,new LinearLayout.LayoutParams(-1,-2));
        TextView title=centeredText("Tu registro",30,TEXT,true); title.setPadding(0,dp(2),0,dp(4)); content.addView(title,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout tabs=row(BG); tabs.addView(periodButton("SEMANA","week",period),new LinearLayout.LayoutParams(0,dp(48),1)); tabs.addView(periodButton("MES","month",period),new LinearLayout.LayoutParams(0,dp(48),1)); tabs.addView(periodButton("AÑO","year",period),new LinearLayout.LayoutParams(0,dp(48),1)); content.addView(tabs);
        LocalDate to=operationalDay(), from;
        if(period.equals("week")) from=to.with(DayOfWeek.MONDAY); else if(period.equals("month")) from=YearMonth.from(to).atDay(1); else from=LocalDate.of(to.getYear(),1,1);
        AppDb.Stats s=db.stats(from,to); int pct=s.possible()==0?0:Math.round(s.done()*100f/s.possible());
        space(content,18);
        content.addView(metric("CUMPLIMIENTO",pct+"%",s.done()+" de "+s.possible()+" acciones programadas"));
        space(content,16);
        LinearLayout pair=row(BG); pair.setClipChildren(false); pair.setClipToPadding(false);
        pair.addView(metric("DÍAS PERFECTOS",String.valueOf(s.perfectDays()),"de "+s.trackedDays()+" días con tareas"),new LinearLayout.LayoutParams(0,-2,1));
        space(pair,12);
        pair.addView(metric("RACHA ACTUAL",s.streak()+" días","jornadas programadas completas"),new LinearLayout.LayoutParams(0,-2,1));
        content.addView(pair);
        space(content,18); content.addView(text("Período: "+from.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))+" — "+to.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),13,MUTED,false));
        if(animate) animateScreenIn(direction);
    }

    private Button periodButton(String label,String id,String selected){
        Button b=button(label,id.equals(selected)?BG:CARD,id.equals(selected)?ACCENT:Color.TRANSPARENT);
        b.setOnClickListener(v->renderStats(id,false,0));
        return b;
    }

    private View metric(String label,String value,String sub){
        LinearLayout c=card(); c.setPadding(dp(16),dp(16),dp(16),dp(16));
        c.addView(text(label,11,ACCENT,true)); c.addView(text(value,30,TEXT,true)); c.addView(text(sub,12,MUTED,false));
        c.setElevation(dp(10));
        if(Build.VERSION.SDK_INT>=28) {
            c.setOutlineAmbientShadowColor(Color.rgb(224,205,245));
            c.setOutlineSpotShadowColor(Color.rgb(150,105,205));
        }
        return c;
    }

    private void renderConfig(long animatedHabitId,int moveDelta,boolean animate,int direction){
        tab="config"; setNav(tab); resetContentTransform(); content.removeAllViews(); content.addView(text("CONFIGURACIÓN",13,ACCENT,true)); content.addView(text("Tus hábitos",30,TEXT,true));
        Button add=button("＋ AGREGAR HÁBITO",BG,ACCENT); add.setOnClickListener(v->editDialog(null)); content.addView(add,new LinearLayout.LayoutParams(-1,dp(52))); space(content,14);
        for(AppDb.Habit h:db.habits(false)) {
            View c=configCard(h);
            content.addView(c);
            if(h.id()==animatedHabitId) animateSettledCard(c,h.active()?1f:.5f,moveDelta);
        }
        TextView note=text("Podés hacer una tarea diaria o elegir varios días de la semana y agregarle un aviso con hora y tono. Los hábitos retirados conservan su historial.",12,MUTED,false); note.setPadding(0,dp(14),0,0); content.addView(note);
        if(animate) animateScreenIn(direction);
    }

    private View configCard(AppDb.Habit h){
        LinearLayout c=card(); c.setOrientation(LinearLayout.HORIZONTAL); c.setGravity(Gravity.CENTER_VERTICAL); c.setPadding(dp(14),dp(10),dp(8),dp(10)); c.setAlpha(h.active()?1f:.5f);
        LinearLayout info=column(Color.TRANSPARENT);
        info.addView(text(h.name(),16,TEXT,true));
        info.addView(text(configDetail(h),11,MUTED,false));
        c.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        Button up=button("↑",TEXT,Color.TRANSPARENT); up.setOnClickListener(v->animateMove(h,-1,c)); c.addView(up,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Button down=button("↓",TEXT,Color.TRANSPARENT); down.setOnClickListener(v->animateMove(h,1,c)); c.addView(down,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Button edit=button("✎",ACCENT,Color.TRANSPARENT); edit.setOnClickListener(v->editDialog(h)); c.addView(edit,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Button active=button(h.active()?"×":"＋",h.active()?Color.rgb(255,120,120):ACCENT,Color.TRANSPARENT);
        active.setOnClickListener(v->{
            boolean next=!h.active(); db.setActive(h.id(),next); AppDb.Habit updated=db.habit(h.id());
            if(updated!=null && updated.active() && updated.notifyEnabled()) AlarmScheduler.scheduleHabit(this,updated); else AlarmScheduler.cancelHabit(this,h.id());
            renderConfig(-1,0,false,0);
        });
        c.addView(active,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(9); c.setLayoutParams(lp); return c;
    }

    private void animateMove(AppDb.Habit h,int delta,View card) {
        List<AppDb.Habit> active=db.habits(true);
        int index=-1;
        for(int i=0;i<active.size();i++) if(active.get(i).id()==h.id()) { index=i; break; }
        int target=index+delta;
        if(!h.active() || index<0 || target<0 || target>=active.size()) {
            float nudge=delta*dp(8);
            card.animate().translationY(nudge).setDuration(90).withEndAction(()->
                    card.animate().translationY(0).setDuration(120).start()).start();
            return;
        }
        float distance=(card.getHeight()>0?card.getHeight():dp(68))+dp(9);
        card.animate()
                .translationY(delta*distance)
                .scaleX(.975f).scaleY(.975f)
                .alpha(.62f)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(190)
                .withEndAction(()->{
                    db.move(h.id(),delta);
                    renderConfig(h.id(),delta,false,0);
                }).start();
    }

    private void animateSettledCard(View card,float finalAlpha,int moveDelta) {
        card.setTranslationY(-moveDelta*dp(26));
        card.setScaleX(.98f); card.setScaleY(.98f); card.setAlpha(.55f);
        card.animate().translationY(0).scaleX(1f).scaleY(1f).alpha(finalAlpha)
                .setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(210).start();
    }

    private String configDetail(AppDb.Habit h) {
        String schedule = h.weekly() ? daysSummary(h.effectiveWeekdaysMask()) : "Todos los días";
        if (h.notifyEnabled()) schedule += " · 🔔 " + timeText(h.notifyHour(),h.notifyMinute());
        return schedule;
    }

    private void editDialog(AppDb.Habit h){
        boolean isNew = h == null;
        int initialMask = isNew ? 0 : h.effectiveWeekdaysMask();
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

        CheckBox weekly = check("Elegir días de la semana"); weekly.setChecked(!isNew && h.weekly()); form.addView(weekly);
        CheckBox[] dayChecks=new CheckBox[7];
        LinearLayout daysPanel=buildDaysPanel(dayChecks,initialMask);
        daysPanel.setVisibility(weekly.isChecked()?View.VISIBLE:View.GONE);
        form.addView(daysPanel,new LinearLayout.LayoutParams(-1,-2));
        weekly.setOnCheckedChangeListener((b,checked)->daysPanel.setVisibility(checked?View.VISIBLE:View.GONE));
        space(form,8);

        CheckBox notify = check("Notificar"); notify.setChecked(!isNew && h.notifyEnabled()); form.addView(notify);
        final int[] pickedTime={initialHour,initialMinute};
        Button time=button("Hora · "+timeText(initialHour,initialMinute),TEXT,CARD);
        time.setOnClickListener(v->showTimePicker(time,pickedTime));
        form.addView(time,new LinearLayout.LayoutParams(-1,dp(50)));

        Button tone=button(toneLabel(pendingSoundUri),TEXT,CARD); pendingToneButton=tone;
        tone.setOnClickListener(v->openRingtonePicker());
        form.addView(tone,new LinearLayout.LayoutParams(-1,dp(50)));
        time.setVisibility(notify.isChecked()?View.VISIBLE:View.GONE); tone.setVisibility(notify.isChecked()?View.VISIBLE:View.GONE);
        notify.setOnCheckedChangeListener((b,checked)->{time.setVisibility(checked?View.VISIBLE:View.GONE);tone.setVisibility(checked?View.VISIBLE:View.GONE);});

        AlertDialog dialog=styledBuilder()
                .setTitle(isNew?"Nuevo hábito":"Editar hábito")
                .setView(scroll)
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Guardar",null)
                .create();
        dialog.setOnShowListener(x->{
            styleDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String n=e.getText().toString().trim();
                if(n.isEmpty()){e.setError("Poné un nombre");return;}
                boolean isWeekly=weekly.isChecked();
                int weekdaysMask=selectedDaysMask(dayChecks);
                if(isWeekly && weekdaysMask==0){toast("Elegí al menos un día");return;}
                boolean wantsNotify=notify.isChecked();
                long id;
                if(isNew) id=db.addHabit(n,isWeekly,weekdaysMask,wantsNotify,pickedTime[0],pickedTime[1],pendingSoundUri);
                else { id=h.id(); db.updateHabit(id,n,isWeekly,weekdaysMask,wantsNotify,pickedTime[0],pickedTime[1],pendingSoundUri); }
                AppDb.Habit saved=db.habit(id);
                if(saved!=null && saved.active() && saved.notifyEnabled()) AlarmScheduler.scheduleHabit(this,saved); else AlarmScheduler.cancelHabit(this,id);
                dialog.dismiss(); renderConfig(-1,0,false,0);
                if(wantsNotify) requestNotificationAccessIfNeeded();
            });
        });
        dialog.setOnDismissListener(x->{pendingToneButton=null;});
        dialog.show();
    }

    private LinearLayout buildDaysPanel(CheckBox[] out,int mask) {
        LinearLayout panel=column(Color.TRANSPARENT);
        TextView label=text("DÍAS",11,MUTED,true); label.setPadding(0,dp(2),0,dp(2)); panel.addView(label);
        LinearLayout row1=row(Color.TRANSPARENT), row2=row(Color.TRANSPARENT);
        String[] labels={"Lun","Mar","Mié","Jue","Vie","Sáb","Dom"};
        for(int i=0;i<7;i++) {
            CheckBox c=check(labels[i]);
            c.setChecked((mask&(1<<i))!=0);
            c.setGravity(Gravity.CENTER_VERTICAL);
            out[i]=c;
            LinearLayout target=i<4?row1:row2;
            target.addView(c,new LinearLayout.LayoutParams(0,dp(44),1));
        }
        row2.addView(new Space(this),new LinearLayout.LayoutParams(0,dp(44),1));
        panel.addView(row1,new LinearLayout.LayoutParams(-1,dp(44)));
        panel.addView(row2,new LinearLayout.LayoutParams(-1,dp(44)));
        return panel;
    }

    private int selectedDaysMask(CheckBox[] checks) {
        int mask=0;
        for(int i=0;i<checks.length;i++) if(checks[i]!=null && checks[i].isChecked()) mask|=1<<i;
        return mask;
    }

    private String daysSummary(int mask) {
        int clean=mask&0x7F;
        if(clean==0x7F) return "Todos los días";
        String[] names={"Lun","Mar","Mié","Jue","Vie","Sáb","Dom"};
        StringBuilder out=new StringBuilder();
        for(int i=0;i<7;i++) if((clean&(1<<i))!=0) {
            if(out.length()>0) out.append(" · ");
            out.append(names[i]);
        }
        return out.length()==0?"Sin días":out.toString();
    }

    private void showTimePicker(Button time,final int[] pickedTime) {
        TimePickerDialog picker=new TimePickerDialog(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK,(view,hour,minute)->{
            pickedTime[0]=hour; pickedTime[1]=minute; time.setText("Hora · "+timeText(hour,minute));
        },pickedTime[0],pickedTime[1],true);
        picker.setOnShowListener(x->styleDialog(picker));
        picker.show();
    }

    private CheckBox check(String label) {
        CheckBox c=new CheckBox(this); c.setText(label); c.setTextColor(TEXT); c.setTextSize(15); c.setPadding(0,dp(6),0,dp(6)); return c;
    }

    private void openRingtonePicker() {
        Intent i=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_NOTIFICATION);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"Tono de W-mode");
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
        AlertDialog dialog=styledBuilder()
                .setTitle("Respetar el horario")
                .setMessage("Para avisarte lo más cerca posible de la hora elegida, Android puede pedir permiso para Alarmas y recordatorios. Si no lo activás, el aviso igual queda programado pero el sistema puede demorarlo.")
                .setNegativeButton("Ahora no",null)
                .setPositiveButton("Permitir",(d,w)->{
                    try {
                        Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()));
                        startActivity(i);
                    } catch(Exception ignored) { toast("Podés habilitar Alarmas y recordatorios desde Ajustes"); }
                }).create();
        dialog.setOnShowListener(x->styleDialog(dialog));
        dialog.show();
    }

    private AlertDialog.Builder styledBuilder() {
        return new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK);
    }

    private void styleDialog(Dialog dialog) {
        Window window=dialog.getWindow();
        if(window==null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        View decor=window.getDecorView();
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(DIALOG_BG); bg.setCornerRadius(dp(22)); bg.setStroke(dp(1),DIALOG_STROKE);
        decor.setBackground(bg); decor.setClipToOutline(true); decor.setElevation(dp(22));
        if(Build.VERSION.SDK_INT>=28) {
            decor.setOutlineAmbientShadowColor(DIALOG_SHADOW);
            decor.setOutlineSpotShadowColor(DIALOG_SHADOW);
        }
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.width=(int)(getResources().getDisplayMetrics().widthPixels*.90f);
        window.setAttributes(lp);
        tintDialogText(decor);
        if(dialog instanceof AlertDialog) {
            AlertDialog a=(AlertDialog)dialog;
            if(a.getButton(AlertDialog.BUTTON_POSITIVE)!=null) a.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT);
            if(a.getButton(AlertDialog.BUTTON_NEGATIVE)!=null) a.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.rgb(224,210,240));
            if(a.getButton(AlertDialog.BUTTON_NEUTRAL)!=null) a.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.rgb(224,210,240));
        }
    }

    private void tintDialogText(View view) {
        if(view instanceof TextView && !(view instanceof Button)) ((TextView)view).setTextColor(TEXT);
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) tintDialogText(group.getChildAt(i));
        }
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
    private TextView centeredText(String s,int sp,int color,boolean bold){TextView t=text(s,sp,color,bold);t.setGravity(Gravity.CENTER);t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);return t;}
    private Button button(String s,int textColor,int bg){Button b=new Button(this);b.setText(s);b.setTextColor(textColor);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT_BOLD);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));return b;}
    private TextView empty(String s){TextView t=text(s,15,MUTED,false);t.setGravity(Gravity.CENTER);t.setPadding(dp(20),dp(50),dp(20),dp(50));return t;}
    private void space(LinearLayout l,int d){Space s=new Space(this);l.addView(s,new LinearLayout.LayoutParams(dp(d),dp(d)));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String s){android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_SHORT).show();}
}
