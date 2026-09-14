package com.nightflow.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;

public final class HudView extends View {
    private final MainActivity owner;
    private final WorldRenderer renderer;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF brake=new RectF(),gas=new RectF(),left=new RectF(),right=new RectF(),pause=new RectF();
    private final SparseIntArray touches=new SparseIntArray();
    public int safeLeft,safeRight;
    private final float d;
    public HudView(MainActivity owner,WorldRenderer renderer) {
        super(owner);this.owner=owner;this.renderer=renderer;d=getResources().getDisplayMetrics().density;
        safeLeft=owner.dp(18);safeRight=owner.dp(18);
        setFocusable(true);setContentDescription("Instrumenti vožnje, gas, kočnica, strelice i pauza.");
    }
    private float dp(float v){return v*d;}
    private void bounds() {
        float w=getWidth(),h=getHeight(),bottom=h-dp(17);
        brake.set(w-safeRight-dp(198),bottom-dp(56),w-safeRight-dp(105),bottom);
        gas.set(w-safeRight-dp(96),bottom-dp(56),w-safeRight,bottom);
        left.set(safeLeft,bottom-dp(56),safeLeft+dp(57),bottom);
        right.set(safeLeft+dp(67),bottom-dp(56),safeLeft+dp(124),bottom);
        pause.set(w-safeRight-dp(44),dp(16),w-safeRight,dp(57));
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        GameState g=renderer.state;Settings s=renderer.settings;
        if(g.phase==GameState.MENU) return;
        bounds();float w=getWidth(),h=getHeight();
        if(g.phase==GameState.CRASHED||g.phase==GameState.PAUSED) return;
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        text(c,"BODOVI",safeLeft,dp(28),10,0xffacc6d5,false);
        text(c,String.format(Locale.ROOT,"%06d",g.score),safeLeft,dp(54),23,0xfff1f7fa,false);
        text(c,String.format(Locale.ROOT,"%.2f km",g.distance/1000),w*.50f,dp(34),15,0xffe5eff4,true);
        text(c,Settings.MODES[g.mode],w*.50f,dp(51),9,0xff77d9d2,true);
        button(c,pause,"Ⅱ",false,18);
        if(s.showFps) text(c,Math.round(renderer.actualFps)+" FPS",w-safeRight-dp(59),dp(40),10,0xff8df7cc,true);
        text(c,""+Math.round(g.speed*3.6f),w*.50f,h-dp(40),39,0xfff5faff,true);
        text(c,"KM/H",w*.50f,h-dp(20),10,0xff9bb9ce,true);
        p.setColor(0x553e6376);p.setStrokeWidth(dp(3));p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(w*.5f-dp(52),h-dp(81),w*.5f+dp(52),h-dp(81),p);
        p.setColor(0xff67eddb);c.drawLine(w*.5f-dp(52),h-dp(81),w*.5f-dp(52)+dp(104)*Math.min(1,g.speed/65),h-dp(81),p);
        button(c,brake,"KOČNICA",renderer.brake,11);button(c,gas,"GAS",renderer.gas,13);
        if(s.control==1) {
            button(c,left,"‹",renderer.touch<0,31);button(c,right,"›",renderer.touch>0,31);
        } else {
            float cx=safeLeft+dp(57),y=h-dp(38);
            text(c,"NAGINJANJE",cx,h-dp(62),9,0xff9ebdcf,true);
            p.setColor(0x88436270);p.setStrokeWidth(dp(2));c.drawLine(cx-dp(44),y,cx+dp(44),y,p);
            p.setColor(0xff67eddb);c.drawCircle(cx+renderer.tilt*dp(43),y,dp(5),p);
        }
        if(g.countdown>0) {
            text(c,""+(int)Math.ceil(g.countdown),w*.5f,h*.47f,58,0xff91f8e5,true);
            text(c,s.control==0?"DRŽI TELEFON VODORAVNO · NAGINJI LIJEVO I DESNO":"KORISTI STRELICE ZA UPRAVLJANJE",
                w*.5f,h*.59f,11,0xffedf8fa,true);
        } else if(g.invincible>0) {
            text(c,"SAMO LAGANO. NASTAVI VOZITI.",w*.5f,h*.25f,13,0xffb8fbe7,true);
        } else if(g.near>0) {
            text(c,"TIJESNO!  ×"+g.combo,w*.5f,h*.25f,20,0xff8bfbe7,true);
        }
    }
    private void text(Canvas c,String text,float x,float y,float size,int color,boolean center) {
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(dp(size));p.setTextAlign(center?Paint.Align.CENTER:Paint.Align.LEFT);
        p.setShadowLayer(dp(3),0,dp(1),0xbb03101b);c.drawText(text,x,y,p);p.clearShadowLayer();
    }
    private void button(Canvas c,RectF r,String label,boolean active,float size) {
        p.setStyle(Paint.Style.FILL);p.setColor(active?0xd34bcdbb:0x99101e30);c.drawRoundRect(r,dp(10),dp(10),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(active?0xff8cfff1:0x8869899e);
        c.drawRoundRect(r,dp(10),dp(10),p);p.setStyle(Paint.Style.FILL);
        text(c,label,r.centerX(),r.centerY()+dp(size*.34f),size,active?0xff0d2029:0xffdfedf3,true);
    }
    private int hit(float x,float y) {
        if(brake.contains(x,y))return 1;if(gas.contains(x,y))return 2;
        if(renderer.settings.control==1){if(left.contains(x,y))return 3;if(right.contains(x,y))return 4;}
        if(pause.contains(x,y))return 5;return 0;
    }
    public void releaseTouches(){touches.clear();}
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(renderer.state.phase!=GameState.RUNNING) return false;
        bounds();int action=e.getActionMasked(),index=e.getActionIndex();
        if(action==MotionEvent.ACTION_CANCEL) {
            touches.clear();renderer.brake=false;renderer.gas=false;renderer.touch=0;return true;
        }
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
            if(hit(e.getX(index),e.getY(index))==5){owner.pauseGame();return true;}
        }
        touches.clear();
        for(int i=0;i<e.getPointerCount();i++) {
            if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&i==index)continue;
            touches.put(e.getPointerId(i),hit(e.getX(i),e.getY(i)));
        }
        boolean braking=false,accelerating=false,l=false,r=false;
        for(int i=0;i<touches.size();i++) {
            int v=touches.valueAt(i);braking|=v==1;accelerating|=v==2;l|=v==3;r|=v==4;
        }
        renderer.brake=braking;renderer.gas=accelerating;renderer.touch=(r?1:0)-(l?1:0);
        if(action==MotionEvent.ACTION_UP) performClick();
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
