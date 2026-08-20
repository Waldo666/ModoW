package com.waldo.modow;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

public final class WModeApp extends Application implements Application.ActivityLifecycleCallbacks {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MainActivity currentMain;

    private final Runnable injector = new Runnable() {
        @Override public void run() {
            MainActivity activity = currentMain;
            if (activity == null || activity.isFinishing()) return;
            injectBackupControls(activity);
            handler.postDelayed(this, 350);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof MainActivity) {
            currentMain = (MainActivity) activity;
            handler.removeCallbacks(injector);
            handler.post(injector);
        }
    }

    @Override public void onActivityPaused(Activity activity) {
        if (activity == currentMain) {
            currentMain = null;
            handler.removeCallbacks(injector);
        }
    }

    private void injectBackupControls(MainActivity activity) {
        View root = activity.findViewById(android.R.id.content);
        TextView configTitle = findText(root, "Tus tareas");
        if (configTitle == null) return;
        if (!(configTitle.getParent() instanceof LinearLayout)) return;

        LinearLayout content = (LinearLayout) configTitle.getParent();
        if (content.findViewWithTag("wmode_backup_section") != null) return;

        Space gap = new Space(activity);
        content.addView(gap, new LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18)));

        LinearLayout section = new LinearLayout(activity);
        section.setTag("wmode_backup_section");
        section.setOrientation(LinearLayout.VERTICAL);
        section.setClipChildren(false);
        section.setClipToPadding(false);

        TextView title = text(activity, "DATOS", 13, MainActivity.ACCENT, true);
        title.setGravity(Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        section.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = text(activity, "Backup de W-mode", 25, MainActivity.TEXT, true);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        subtitle.setPadding(0, dp(activity, 2), 0, dp(activity, 10));
        section.addView(subtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        card.setClipToOutline(false);
        card.setElevation(dp(activity, 9));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(MainActivity.CARD);
        cardBg.setCornerRadius(dp(activity, 16));
        cardBg.setStroke(dp(activity, 1), Color.rgb(77, 58, 98));
        card.setBackground(cardBg);
        if (Build.VERSION.SDK_INT >= 28) {
            card.setOutlineAmbientShadowColor(Color.rgb(224, 205, 245));
            card.setOutlineSpotShadowColor(Color.rgb(141, 91, 203));
        }

        Button export = actionButton(activity, "EXPORTAR COPIA DE SEGURIDAD");
        export.setOnClickListener(v -> openBackup(activity, "export"));
        card.addView(export, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50)));

        Space between = new Space(activity);
        card.addView(between, new LinearLayout.LayoutParams(dp(activity, 8), dp(activity, 8)));

        Button importButton = actionButton(activity, "IMPORTAR COPIA DE SEGURIDAD");
        importButton.setOnClickListener(v -> openBackup(activity, "import"));
        card.addView(importButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50)));

        TextView note = text(activity,
                "Incluye tareas, días programados, avisos e historial. Los permisos de Android no se modifican.",
                12, MainActivity.MUTED, false);
        note.setPadding(dp(activity, 2), dp(activity, 10), dp(activity, 2), 0);
        card.addView(note, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        section.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(section, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void openBackup(Activity activity, String mode) {
        Intent intent = new Intent(activity, BackupActivity.class);
        intent.putExtra(BackupActivity.EXTRA_MODE, mode);
        activity.startActivity(intent);
    }

    private TextView findText(View view, String wanted) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && wanted.contentEquals(value)) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Button actionButton(Activity activity, String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(MainActivity.BG);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(MainActivity.ACCENT);
        bg.setCornerRadius(dp(activity, 14));
        button.setBackground(bg);
        return button;
    }

    private TextView text(Activity activity, String value, int sp, int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return text;
    }

    private int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
