package com.nightflow.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
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
    private void bounds(){
        float w=getWidth(),h=getHeight(),bottom=h-dp(16);
        brake.set(w-safeRight-dp(174),bottom-dp(65),w-safeRight-dp(84),bottom);
        gas.set(w-safeRight-dp(66),bottom-dp(115),w-safeRight-dp(10),bottom);
        left.set(safeLeft,bottom-dp(56),safeLeft+dp(57),bottom);
        right.set(safeLeft+dp(67),bottom-dp(56),safeLeft+dp(124),bottom);
        pause.set(w-safeRight-dp(236),dp(16),w-safeRight-dp(194),dp(57));
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);GameState g=renderer.state;Settings s=renderer.settings;
        if(g.phase!=GameState.RUNNING)return;
        bounds();float w=getWidth(),h=getHeight();p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        text(c,L.t(s,g.mode>=3?"time":"score"),safeLeft,dp(28),10,0xffacc6d5,false);
        text(c,g.mode>=3?L.clock(g.elapsed+(g.mode==GameState.TIME_ATTACK?g.penalty:0)):String.format(Locale.ROOT,"%06d",g.score),safeLeft,dp(52),22,0xfff1f7fa,false);
        text(c,L.t(s,"mode"+g.mode),safeLeft,dp(72),9,0xff77d9d2,false);
        text(c,String.format(Locale.ROOT,"%.2f km",g.distance/1000),w*.48f,dp(29),14,0xffe5eff4,true);
        if(g.mode==GameState.AI_RACE){
            text(c,L.t(s,"place",g.position),w*.48f,dp(51),13,0xff77ead7,true);
            raceProgress(c,w*.48f,dp(72),g);
        }else if(g.mode==GameState.TIME_ATTACK){
            String ghost=g.bestTime>0?L.t(s,"record")+" "+L.clock(g.bestTime):L.t(s,"firstGhost");
            text(c,ghost,w*.48f,dp(48),10,0xff77ead7,true);
            if(g.gateDistance<g.targetDistance){
                String arrow=g.gateX-g.x>.8f?"›":g.gateX-g.x<-.8f?"‹":"↓";
                text(c,arrow+"  "+L.t(s,"gate")+"  "+Math.round(Math.max(0,g.gateDistance-g.distance))+" m",w*.48f,dp(73),13,0xffe5f5ee,true);
            }
        }
        button(c,pause,"Ⅱ",false,18);gauge(c,w-safeRight-dp(91),dp(105),g,s);
        if(s.showFps)text(c,Math.round(renderer.actualFps)+" FPS",safeLeft,dp(90),10,0xff8df7cc,false);
        pedal(c,brake,false,renderer.brake,L.t(s,"brake"));pedal(c,gas,true,renderer.gas,L.t(s,"gas"));
        if(s.control==1){button(c,left,"‹",renderer.touch<0,31);button(c,right,"›",renderer.touch>0,31);}
        else{
            float cx=safeLeft+dp(57),y=h-dp(38);text(c,L.t(s,"tilt"),cx,h-dp(62),9,0xff9ebdcf,true);
            p.setColor(0x88436270);p.setStrokeWidth(dp(2));c.drawLine(cx-dp(44),y,cx+dp(44),y,p);
            p.setColor(0xff67eddb);c.drawCircle(cx+renderer.tilt*dp(43),y,dp(5),p);
        }
        if(g.countdown>0){
            text(c,""+(int)Math.ceil(g.countdown),w*.48f,h*.48f,58,0xff91f8e5,true);
            text(c,L.t(s,s.control==0?"readyTilt":"readyTouch"),w*.48f,h*.60f,10,0xffedf8fa,true);
        }else if(g.gateFlash>0)text(c,L.t(s,"gateMiss"),w*.48f,h*.3f,14,0xffffb59e,true);
        else if(g.invincible>0)text(c,L.t(s,"recover"),w*.48f,h*.3f,14,0xffb8fbe7,true);
        else if(g.near>0&&g.mode!=GameState.TIME_ATTACK)text(c,L.t(s,"near")+" ×"+g.combo,w*.48f,h*.3f,18,0xff8bfbe7,true);
    }
    private void gauge(Canvas c,float cx,float cy,GameState g,Settings s){
        float radius=dp(76);
        p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(cx,cy,radius,new int[]{0xf5192a3d,0xf309101b},null,Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,radius,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(0xff4b6175);c.drawCircle(cx,cy,radius,p);
        RectF arc=new RectF(cx-radius+dp(7),cy-radius+dp(7),cx+radius-dp(7),cy+radius-dp(7));
        p.setStrokeWidth(dp(3));p.setColor(0xff29424d);c.drawArc(arc,140,260,false,p);
        p.setColor(g.rpm>6500?0xffff7858:0xff66efdc);c.drawArc(arc,140,260*Math.min(1,g.rpm/7600),false,p);
        for(int i=0;i<=32;i++){
            float a=(float)Math.toRadians(140+i*260f/32),co=(float)Math.cos(a),si=(float)Math.sin(a);
            p.setColor(i>=27?0xfff58c75:0xffb7c8d5);p.setStrokeWidth(dp(i%4==0?1.5f:.7f));
            c.drawLine(cx+co*dp(62),cy+si*dp(62),cx+co*dp(i%4==0?54:58),cy+si*dp(i%4==0?54:58),p);
            if(i%4==0)text(c,""+(i*10),cx+co*dp(46),cy+si*dp(46)+dp(3),8,0xff9daebc,true);
            p.setStyle(Paint.Style.STROKE);
        }
        float angle=(float)Math.toRadians(140+260*Math.min(1,g.speed*3.6f/320));
        p.setColor(0xfff8a574);p.setStrokeWidth(dp(2));c.drawLine(cx+(float)Math.cos(angle)*dp(38),cy+(float)Math.sin(angle)*dp(38),cx+(float)Math.cos(angle)*dp(59),cy+(float)Math.sin(angle)*dp(59),p);
        text(c,""+Math.round(g.speed*3.6f),cx,cy+dp(9),31,0xfff4f8fb,true);
        text(c,"km/h",cx,cy+dp(24),9,0xff9fb6ca,true);
        text(c,"AT  "+g.gear,cx,cy+dp(48),14,g.shiftTime>0?0xffffb889:0xff67eddb,true);
        text(c,Math.round(g.rpm)+" rpm",cx,cy+dp(62),8,0xff91a6b8,true);
    }
    private void pedal(Canvas c,RectF r,boolean accelerator,boolean active,String title){
        c.save();c.rotate(accelerator?6:-4,r.centerX(),r.centerY());
        if(active)c.translate(0,dp(3));
        p.setStyle(Paint.Style.FILL);p.setColor(0xb900050a);c.drawRoundRect(r.left-dp(4),r.top-dp(4),r.right+dp(4),r.bottom+dp(5),dp(12),dp(12),p);
        p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,new int[]{0xffd1dbe2,0xff6d7f8d,0xffb9c6cf,0xff52636f},null,Shader.TileMode.CLAMP));
        c.drawRoundRect(r,dp(9),dp(9),p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(active?(accelerator?0xff67eed5:0xffff9f7d):0xffdce4e8);c.drawRoundRect(r,dp(9),dp(9),p);
        p.setStyle(Paint.Style.FILL);
        for(float y=r.top+dp(13);y<r.bottom-dp(21);y+=dp(10)){
            p.setColor(0xff18232c);c.drawRoundRect(r.left+dp(9),y,r.right-dp(9),y+dp(4),dp(2),dp(2),p);
        }
        for(int side:new int[]{-1,1}){
            p.setColor(0xff26333f);c.drawCircle(r.centerX()+side*(r.width()/2-dp(6)),r.top+dp(6),dp(1.5f),p);
        }
        text(c,title,r.centerX(),r.bottom-dp(8),8,0xff11202a,true);c.restore();
    }
    private void raceProgress(Canvas c,float cx,float y,GameState g){
        p.setStrokeWidth(dp(2));p.setColor(0xff3f566a);c.drawLine(cx-dp(90),y,cx+dp(90),y,p);
        p.setStyle(Paint.Style.FILL);
        for(GameState.Rival r:g.rivals){p.setColor(Settings.PAINTS[r.paint]);c.drawCircle(cx-dp(90)+dp(180)*Math.min(1,r.distance/g.targetDistance),y,dp(3),p);}
        p.setColor(0xffa4ffed);c.drawCircle(cx-dp(90)+dp(180)*Math.min(1,g.distance/g.targetDistance),y,dp(5),p);
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
