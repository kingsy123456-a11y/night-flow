package com.nightflow.game;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.io.File;
import java.io.FileOutputStream;
import java.util.function.BooleanSupplier;

public final class SmokeRunner extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void await(BooleanSupplier condition,long timeout,String message) {
        long end=SystemClock.uptimeMillis()+timeout;
        while(SystemClock.uptimeMillis()<end) {
            if(condition.getAsBoolean())return;
            SystemClock.sleep(100);
        }
        check(false,message);
    }
    private Button button(View root,String title) {
        if(root instanceof Button&&((Button)root).getText().toString().equals(title))return (Button)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++) {
            Button found=button(((ViewGroup)root).getChildAt(i),title);if(found!=null)return found;
        }
        return null;
    }
    private void screenshot(MainActivity a,String name) throws Exception {
        Bitmap bitmap=getUiAutomation().takeScreenshot();
        check(bitmap!=null,"Screenshot unavailable");
        File dir=new File(a.getExternalFilesDir(null),"smoke");check(dir.isDirectory()||dir.mkdirs(),"Create screenshot directory");
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
        Bitmap preview=Bitmap.createScaledBitmap(bitmap,880,Math.max(1,bitmap.getHeight()*880/bitmap.getWidth()),true);
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".jpg"))){preview.compress(Bitmap.CompressFormat.JPEG,65,out);}
        if(preview!=bitmap)preview.recycle();
        bitmap.recycle();
    }
    private void touch(MainActivity a,int action,float x,float y) {
        runOnMainSync(()->{
            long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,x,y,0);
            a.hud.dispatchTouchEvent(e);e.recycle();
        });
    }
    @Override public void onStart() {
        Bundle result=new Bundle();MainActivity a=null;
        try {
            Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a=(MainActivity)startActivitySync(intent);MainActivity app=a;
            await(()->app.renderer.framesRendered>=8||app.renderer.failure!=null,60000,"Renderer did not draw");
            check(a.renderer.failure==null,"GL initialization: "+a.renderer.failure);
            screenshot(a,"01-menu");
            runOnMainSync(()->{
                Button start=button(app.getWindow().getDecorView(),"VOZI   ›");
                check(start!=null,"Start button missing");start.performClick();
            });
            await(()->app.renderer.state.phase==GameState.RUNNING&&app.renderer.state.distance>4,25000,"Run did not start");
            Settings original=new Settings(a.settings);
            runOnMainSync(()->{Settings cfg=new Settings(app.settings);cfg.control=1;app.applySettings(cfg);});
            float oldX=a.renderer.state.x;
            float tx=a.hud.safeLeft+a.dp(95),ty=a.hud.getHeight()-a.dp(40);
            touch(a,MotionEvent.ACTION_DOWN,tx,ty);SystemClock.sleep(650);touch(a,MotionEvent.ACTION_UP,tx,ty);
            check(a.renderer.state.x>oldX+.7f,"Right touch control did not steer");
            SystemClock.sleep(500);check(a.renderer.touch==0,"Touch did not release");
            screenshot(a,"02-driving");
            runOnMainSync(app::pauseGame);
            await(()->app.renderer.state.phase==GameState.PAUSED,5000,"Pause failed");
            float distance=a.renderer.state.distance;SystemClock.sleep(500);
            check(Math.abs(a.renderer.state.distance-distance)<.001f,"Game progressed while paused");
            runOnMainSync(app::showSettings);SystemClock.sleep(600);screenshot(a,"03-settings");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);
            runOnMainSync(app::resumeGame);
            await(()->app.renderer.state.phase==GameState.RUNNING&&app.renderer.state.distance>distance+.5f,12000,"Resume failed");
            runOnMainSync(app::pauseGame);
            await(()->app.renderer.state.phase==GameState.PAUSED,5000,"Second pause failed");
            runOnMainSync(()->{
                Settings low=new Settings(app.settings);low.quality=0;low.night=true;low.control=1;app.applySettings(low);
            });
            int before=a.renderer.framesRendered;
            await(()->app.renderer.framesRendered>before+5,10000,"Resolution change stopped drawing");
            check(a.preferences.getInt("quality",-1)==0,"Settings not saved");
            check(a.renderer.failure==null,"Renderer failed on quality change");
            runOnMainSync(()->{app.applySettings(original);app.backToMenu();});SystemClock.sleep(500);
            check(a.renderer.lastGlError==0,"OpenGL error: "+a.renderer.lastGlError);
            check(a.renderer.framesRendered>20,"Insufficient rendered frames");
            result.putString("smoke","PASS");
            result.putString("checks","menu; GLES3 shaders; driving; touch; pause/resume; settings; render resolution; persistence");
            result.putInt("frames",a.renderer.framesRendered);
            result.putString("audio_initialized",Boolean.toString(a.sound.ready));
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e) {
            android.util.Log.e("NightFlowSmoke","Smoke failed",e);
            result.putString("smoke","FAIL");result.putString("failure",android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
