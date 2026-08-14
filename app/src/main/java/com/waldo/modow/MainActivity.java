package com.waldo.modow;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import android.app.Activity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int BG = Color.rgb(11,13,18), CARD = Color.rgb(26,30,39), TEXT = Color.rgb(240,244,248), MUTED = Color.rgb(142,151,163), ACCENT = Color.rgb(168,255,96), DONE = Color.rgb(90,95,103);
    private AppDb db;
    private LinearLayout root, content, nav;
    private final Handler handler = new Handler();
    private String tab = "today";

    public static LocalDate operationalDay() {
        LocalDateTime now = LocalDateTime.now();
        return (now.getHour()==0 && now.getMinute()==0) ? now.toLocalDate().minusDays(1) : now.toLocalDate();
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        enableImmersiveMode();
        db = new AppDb(this);
        splash();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveMode();
    }

    private void enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void splash() {
        root = column(BG); root.setGravity(Gravity.CENTER); setContentView(root);
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
        root = column(BG); setContentView(root);
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
        Button b = button(label, id.equals(selected)?ACCENT:MUTED, Color.TRANSPARENT); b.setTextSize(11); b.setOnClickListener(v->{ if(id.equals("today"))showToday(); else if(id.equals("stats"))showStats("week"); else showConfig(); }); return b;
    }

    private void showToday() {
        tab="today"; setNav(tab); content.removeAllViews(); LocalDate day=operationalDay();
        content.addView(text(day.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM",new Locale("es","AR"))).toUpperCase(),13,ACCENT,true));
        TextView h=text("Modo configuración",30,TEXT,true); h.setPadding(0,dp(4),0,dp(4)); content.addView(h);
        int total=db.habits(true).size(), done=0; for(AppDb.Habit habit:db.habits(true)) if(db.isDone(habit.id(),day)) done++;
        content.addView(progressCard(done,total)); space(content,14);
        for(AppDb.Habit habit:db.habits(true)) content.addView(habitCard(habit,day));
        if(total==0) content.addView(empty("No hay hábitos activos. Agregalos desde Configuración."));
    }

    private View progressCard(int done,int total){
        LinearLayout c=card(); c.setPadding(dp(18),dp(16),dp(18),dp(16));
        TextView big=text(total==0?"0%":Math.round(done*100f/total)+"%",36,TEXT,true); c.addView(big);
        c.addView(text(done+" de "+total+" completados",14,MUTED,false));
        LinearLayout bar=row(Color.rgb(48,54,64)); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(9)); bp.topMargin=dp(12); c.addView(bar,bp);
        View fill=new View(this); fill.setBackgroundColor(ACCENT); bar.addView(fill,new LinearLayout.LayoutParams(total==0?0:0,dp(9),done)); if(total-done>0) bar.addView(new View(this),new LinearLayout.LayoutParams(0,dp(9),total-done));
        return c;
    }

    private View habitCard(AppDb.Habit h,LocalDate day){
        boolean done=db.isDone(h.id(),day); LinearLayout c=card(); c.setOrientation(LinearLayout.HORIZONTAL); c.setGravity(Gravity.CENTER_VERTICAL); c.setPadding(dp(18),dp(14),dp(14),dp(14));
        TextView name=text(h.name(),18,done?DONE:TEXT,true); if(done) name.setPaintFlags(name.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG); c.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView mark=text(done?"✓":"○",32,done?DONE:ACCENT,true); c.addView(mark,new LinearLayout.LayoutParams(dp(46),dp(46)));
        if(!done) c.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("¿Marcar como cumplido?").setMessage("Después no se puede destildar hasta el próximo día.").setNegativeButton("Cancelar",null).setPositiveButton("Sí, cumplido",(d,w)->{db.complete(h.id(),day); showToday();}).show());
        else { c.setAlpha(.58f); c.setOnClickListener(v->toast("Quedó bloqueado hasta mañana a las 00:01")); }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(10); c.setLayoutParams(lp); return c;
    }

    private void showStats(String period){
        tab="stats"; setNav(tab); content.removeAllViews(); content.addView(text("CUMPLIMIENTO",13,ACCENT,true)); content.addView(text("Tu registro",30,TEXT,true));
        LinearLayout tabs=row(BG); tabs.addView(periodButton("SEMANA","week",period),new LinearLayout.LayoutParams(0,dp(48),1)); tabs.addView(periodButton("MES","month",period),new LinearLayout.LayoutParams(0,dp(48),1)); tabs.addView(periodButton("AÑO","year",period),new LinearLayout.LayoutParams(0,dp(48),1)); content.addView(tabs);
        LocalDate to=operationalDay(), from;
        if(period.equals("week")) from=to.with(DayOfWeek.MONDAY); else if(period.equals("month")) from=YearMonth.from(to).atDay(1); else from=LocalDate.of(to.getYear(),1,1);
        AppDb.Stats s=db.stats(from,to); int pct=s.possible()==0?0:Math.round(s.done()*100f/s.possible());
        space(content,16); content.addView(metric("CUMPLIMIENTO",pct+"%",s.done()+" de "+s.possible()+" acciones"));
        LinearLayout pair=row(BG); pair.addView(metric("DÍAS PERFECTOS",String.valueOf(s.perfectDays()),"de "+s.trackedDays()+" días"),new LinearLayout.LayoutParams(0,-2,1)); space(pair,10); pair.addView(metric("RACHA ACTUAL",s.streak()+" días","días completos seguidos"),new LinearLayout.LayoutParams(0,-2,1)); content.addView(pair);
        space(content,14); content.addView(text("Período: "+from.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))+" — "+to.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),13,MUTED,false));
    }

    private Button periodButton(String label,String id,String selected){ Button b=button(label,id.equals(selected)?BG:CARD,id.equals(selected)?ACCENT:Color.TRANSPARENT); b.setOnClickListener(v->showStats(id)); return b; }
    private View metric(String label,String value,String sub){ LinearLayout c=card(); c.setPadding(dp(16),dp(16),dp(16),dp(16)); c.addView(text(label,11,ACCENT,true)); c.addView(text(value,30,TEXT,true)); c.addView(text(sub,12,MUTED,false)); return c; }

    private void showConfig(){
        tab="config"; setNav(tab); content.removeAllViews(); content.addView(text("CONFIGURACIÓN",13,ACCENT,true)); content.addView(text("Tus hábitos",30,TEXT,true));
        Button add=button("＋ AGREGAR HÁBITO",BG,ACCENT); add.setOnClickListener(v->editDialog(null)); content.addView(add,new LinearLayout.LayoutParams(-1,dp(52))); space(content,14);
        for(AppDb.Habit h:db.habits(false)) content.addView(configCard(h));
        TextView note=text("Los hábitos retirados conservan su historial. Una tarea tildada nunca se puede desmarcar ese día.",12,MUTED,false); note.setPadding(0,dp(14),0,0); content.addView(note);
    }

    private View configCard(AppDb.Habit h){
        LinearLayout c=card(); c.setOrientation(LinearLayout.HORIZONTAL); c.setGravity(Gravity.CENTER_VERTICAL); c.setPadding(dp(14),dp(10),dp(8),dp(10)); c.setAlpha(h.active()?1f:.5f);
        TextView name=text(h.name(),16,TEXT,true); c.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Button up=button("↑",TEXT,Color.TRANSPARENT); up.setOnClickListener(v->{db.move(h.id(),-1);showConfig();}); c.addView(up,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Button down=button("↓",TEXT,Color.TRANSPARENT); down.setOnClickListener(v->{db.move(h.id(),1);showConfig();}); c.addView(down,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Button edit=button("✎",ACCENT,Color.TRANSPARENT); edit.setOnClickListener(v->editDialog(h)); c.addView(edit,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Button active=button(h.active()?"×":"＋",h.active()?Color.rgb(255,120,120):ACCENT,Color.TRANSPARENT); active.setOnClickListener(v->{db.setActive(h.id(),!h.active());showConfig();}); c.addView(active,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(9); c.setLayoutParams(lp); return c;
    }

    private void editDialog(AppDb.Habit h){ EditText e=new EditText(this); e.setText(h==null?"":h.name()); e.setSingleLine(true); e.setSelectAllOnFocus(true); int pad=dp(20); e.setPadding(pad,pad,pad,pad);
        new AlertDialog.Builder(this).setTitle(h==null?"Nuevo hábito":"Renombrar hábito").setView(e).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{String n=e.getText().toString().trim();if(n.isEmpty())return;if(h==null)db.addHabit(n);else db.rename(h.id(),n);showConfig();}).show(); }

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
