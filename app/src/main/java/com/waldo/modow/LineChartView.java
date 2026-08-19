package com.waldo.modow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.Arrays;

public final class LineChartView extends View {
    private final Paint gridPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint=new Paint(Paint.ANTI_ALIAS_FLAG);

    private float[] values=new float[0];
    private String[] labels=new String[0];
    private float progress=0f;
    private ValueAnimator animator;

    public LineChartView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);

        gridPaint.setColor(Color.rgb(54,48,66));
        gridPaint.setStrokeWidth(dp(1));

        linePaint.setColor(Color.rgb(168,255,96));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(3));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setShadowLayer(dp(8),0,0,Color.rgb(145,92,210));

        pointPaint.setColor(Color.rgb(240,244,248));
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setShadowLayer(dp(5),0,0,Color.rgb(168,255,96));

        labelPaint.setColor(Color.rgb(142,151,163));
        labelPaint.setTextSize(dp(10));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint.setColor(Color.rgb(142,151,163));
        valuePaint.setTextSize(dp(9));
        valuePaint.setTextAlign(Paint.Align.RIGHT);
    }

    public void setData(float[] newValues,String[] newLabels) {
        values=newValues==null?new float[0]:Arrays.copyOf(newValues,newValues.length);
        labels=newLabels==null?new String[0]:Arrays.copyOf(newLabels,newLabels.length);
        progress=0f;
        if(animator!=null) animator.cancel();
        animator=ValueAnimator.ofFloat(0f,1f);
        animator.setDuration(850);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});
        animator.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left=dp(36), right=getWidth()-dp(10), top=dp(12), bottom=getHeight()-dp(28);
        float w=Math.max(1,right-left), h=Math.max(1,bottom-top);

        for(int pct=0;pct<=100;pct+=25) {
            float y=yFor(pct,top,h);
            canvas.drawLine(left,y,right,y,gridPaint);
            canvas.drawText(pct+"%",left-dp(6),y+dp(3),valuePaint);
        }

        int n=values.length;
        if(n==0) {
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.rgb(142,151,163)); p.setTextSize(dp(12)); p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Todavía no hay datos",getWidth()/2f,getHeight()/2f,p);
            return;
        }

        int labelStep=n<=8?1:Math.max(1,(int)Math.ceil((n-1)/5f));
        for(int i=0;i<n;i++) {
            if(i%labelStep!=0 && i!=n-1) continue;
            String label=i<labels.length?labels[i]:String.valueOf(i+1);
            canvas.drawText(label,xFor(i,n,left,w),getHeight()-dp(8),labelPaint);
        }

        if(n==1) {
            if(values[0]>=0) {
                float x=left+w/2f, y=yFor(values[0],top,h);
                canvas.drawCircle(x,y,dp(4),pointPaint);
            }
            return;
        }

        float visible=(n-1)*progress;
        int whole=(int)Math.floor(visible);
        float fraction=visible-whole;

        for(int i=0;i<whole;i++) drawSegment(canvas,i,i+1,1f,left,w,top,h);
        if(whole<n-1 && fraction>0f) drawSegment(canvas,whole,whole+1,fraction,left,w,top,h);

        int pointLimit=Math.min(n-1,whole);
        for(int i=0;i<=pointLimit;i++) {
            if(values[i]<0) continue;
            canvas.drawCircle(xFor(i,n,left,w),yFor(values[i],top,h),dp(3.5f),pointPaint);
        }
        if(whole<n-1 && fraction>.94f && values[whole+1]>=0) {
            canvas.drawCircle(xFor(whole+1,n,left,w),yFor(values[whole+1],top,h),dp(3.5f),pointPaint);
        }
    }

    private void drawSegment(Canvas canvas,int a,int b,float fraction,float left,float w,float top,float h) {
        if(a<0 || b>=values.length || values[a]<0 || values[b]<0) return;
        float x1=xFor(a,values.length,left,w), y1=yFor(values[a],top,h);
        float x2=xFor(b,values.length,left,w), y2=yFor(values[b],top,h);
        float ex=x1+(x2-x1)*fraction, ey=y1+(y2-y1)*fraction;
        canvas.drawLine(x1,y1,ex,ey,linePaint);
    }

    private float xFor(int index,int count,float left,float w) {
        if(count<=1) return left+w/2f;
        return left+(index/(float)(count-1))*w;
    }

    private float yFor(float value,float top,float h) {
        float v=Math.max(0,Math.min(100,value));
        return top+((100f-v)/100f)*h;
    }

    private float dp(float n) { return n*getResources().getDisplayMetrics().density; }
}
