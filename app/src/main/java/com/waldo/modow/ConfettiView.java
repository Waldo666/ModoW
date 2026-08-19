package com.waldo.modow;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ConfettiView extends View {
    private static final long DURATION_MS = 2300L;
    private static final float GRAVITY = 980f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final List<Particle> particles = new ArrayList<>();
    private long startedAt;
    private Runnable onFinished;

    private static final int[] COLORS = {
            Color.rgb(168,255,96),
            Color.rgb(182,130,255),
            Color.rgb(244,238,255),
            Color.rgb(87,220,255),
            Color.rgb(255,210,75),
            Color.rgb(255,112,190)
    };

    private static final class Particle {
        float x0, y0, vx, vy, size, rotation, rotationSpeed;
        int color;
        boolean circle;
    }

    public ConfettiView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
    }

    public void start(Runnable finished) {
        onFinished = finished;
        post(() -> {
            createParticles();
            startedAt = System.currentTimeMillis();
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(DURATION_MS);
            animator.setInterpolator(new DecelerateInterpolator(.9f));
            animator.addUpdateListener(a -> invalidate());
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (onFinished != null) onFinished.run();
                }
            });
            animator.start();
        });
    }

    private void createParticles() {
        particles.clear();
        float width = Math.max(1, getWidth());
        float height = Math.max(1, getHeight());
        float originY = height * .78f;

        for (int i = 0; i < 125; i++) {
            Particle p = new Particle();
            p.x0 = width * .5f + randomRange(-width * .08f, width * .08f);
            p.y0 = originY + randomRange(-22f, 22f);
            p.vx = randomRange(-430f, 430f);
            p.vy = randomRange(-1050f, -520f);
            p.size = randomRange(8f, 18f);
            p.rotation = randomRange(0f, 360f);
            p.rotationSpeed = randomRange(-440f, 440f);
            p.color = COLORS[random.nextInt(COLORS.length)];
            p.circle = random.nextFloat() < .18f;
            particles.add(p);
        }
    }

    private float randomRange(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (particles.isEmpty()) return;

        float seconds = (System.currentTimeMillis() - startedAt) / 1000f;
        float progress = Math.min(1f, (System.currentTimeMillis() - startedAt) / (float) DURATION_MS);
        int alpha = progress < .72f ? 255 : Math.max(0, Math.round(255f * (1f - progress) / .28f));

        for (Particle p : particles) {
            float x = p.x0 + p.vx * seconds;
            float y = p.y0 + p.vy * seconds + .5f * GRAVITY * seconds * seconds;
            if (y > getHeight() + 80 || x < -80 || x > getWidth() + 80) continue;

            paint.setColor(p.color);
            paint.setAlpha(alpha);
            canvas.save();
            canvas.rotate(p.rotation + p.rotationSpeed * seconds, x, y);
            if (p.circle) {
                canvas.drawCircle(x, y, p.size * .45f, paint);
            } else {
                canvas.drawRoundRect(x - p.size * .65f, y - p.size * .32f,
                        x + p.size * .65f, y + p.size * .32f, 3f, 3f, paint);
            }
            canvas.restore();
        }
    }
}
