package com.nightflow.game;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Dialog;
import android.widget.Spinner;
import java.util.ArrayList;
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
    private View tagged(View root,String tag){
        if(tag.equals(root.getTag()))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=tagged(((ViewGroup)root).getChildAt(i),tag);if(found!=null)return found;}
        return null;
    }
    private void collectSpinners(View root,ArrayList<Spinner> out){
        if(root instanceof Spinner)out.add((Spinner)root);
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)collectSpinners(((ViewGroup)root).getChildAt(i),out);
    }
    private void select(View root,int which,int value){ArrayList<Spinner> items=new ArrayList<>();collectSpinners(root,items);check(items.size()>which,"Spinner missing");items.get(which).setSelection(value);}
    private void click(View root,String tag){View v=tagged(root,tag);check(v!=null,"Missing button "+tag);check(v.performClick(),"Click failed "+tag);}
    private Dialog dialog(MainActivity a)throws Exception{java.lang.reflect.Field f=MainActivity.class.getDeclaredField("currentDialog");f.setAccessible(true);return (Dialog)f.get(a);}
    private void frames(MainActivity a,int count){int before=a.renderer.framesRendered;await(()->a.renderer.framesRendered>=before+count,60000,"Frames stopped");}
    private void seedDrive(MainActivity a){a.gl.queueEvent(()->{a.renderer.state.countdown=.1f;a.renderer.state.speed=28;});}
    @Override public void onStart(){
        Bundle result=new Bundle();MainActivity a=null;
        try{
            Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a=(MainActivity)startActivitySync(intent);MainActivity app=a;
            await(()->app.renderer.framesRendered>=3||app.renderer.failure!=null,120000,"Renderer did not draw");
            check(a.renderer.failure==null,"GL initialization: "+a.renderer.failure);
            Settings original=new Settings(a.settings);frames(a,2);screenshot(a,"01-menu-v2");
            runOnMainSync(()->click(app.getWindow().getDecorView(),"garage"));
            await(()->app.renderer.garage&&app.renderer.preview!=null,10000,"Garage missing");
            runOnMainSync(()->select(app.getWindow().getDecorView(),0,3));
            await(()->app.renderer.preview.car==3,10000,"Car preview did not change");
            runOnMainSync(()->{
                View root=app.getWindow().getDecorView();select(root,1,1);select(root,2,3);select(root,3,2);select(root,5,2);
            });
            await(()->app.renderer.preview.engines[3]==3&&app.renderer.preview.kits[3]==2,10000,"Tune preview did not change");
            float beforeOrbit=a.renderer.orbitYaw;
            runOnMainSync(()->{
                View orbit=tagged(app.getWindow().getDecorView(),"orbit");check(orbit!=null,"Orbit surface missing");
                long now=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
                    MotionEvent e=MotionEvent.obtain(now,now,action,action==MotionEvent.ACTION_DOWN?150:240,160,0);orbit.dispatchTouchEvent(e);e.recycle();
                }
            });check(a.renderer.orbitYaw>beforeOrbit+20,"Garage orbit does not work");frames(a,3);screenshot(a,"02-live-tuning");
            runOnMainSync(()->click(app.getWindow().getDecorView(),"saveTune"));
            check(a.preferences.getInt("engine-3",-1)==3,"Engine upgrade not saved");check(a.preferences.getInt("gearbox-3",-1)==2,"Gearbox not saved");
            runOnMainSync(()->{Settings cfg=new Settings(app.settings);cfg.control=1;cfg.quality=1;app.applySettings(cfg);click(app.getWindow().getDecorView(),"drive");});
            await(()->app.renderer.state.phase==GameState.RUNNING,30000,"Run did not start");seedDrive(a);
            await(()->app.renderer.state.distance>1,45000,"Driving did not advance");
            float oldX=a.renderer.state.x,tx=a.hud.safeLeft+a.dp(95),ty=a.hud.getHeight()-a.dp(40);
            touch(a,MotionEvent.ACTION_DOWN,tx,ty);check(a.renderer.touch>.5f,"Touch steering missing");
            try{await(()->app.renderer.state.x>oldX+.85f,30000,"Right touch did not steer");}finally{touch(a,MotionEvent.ACTION_UP,tx,ty);}
            check(a.renderer.touch==0,"Touch release");frames(a,2);screenshot(a,"03-new-instruments");
            runOnMainSync(app::pauseGame);await(()->app.renderer.state.phase==GameState.PAUSED,30000,"Pause failed");
            float distance=a.renderer.state.distance;frames(a,2);check(Math.abs(a.renderer.state.distance-distance)<.001f,"Pause advanced physics");
            runOnMainSync(app::showSettings);await(()->{try{return dialog(app)!=null&&dialog(app).isShowing();}catch(Exception e){return false;}},10000,"Settings not shown");
            SystemClock.sleep(1000);Dialog settingsDialog=dialog(a);frames(a,2);screenshot(a,"04-settings-languages");
            runOnMainSync(()->select(settingsDialog.getWindow().getDecorView(),0,1));SystemClock.sleep(300);
            runOnMainSync(()->click(settingsDialog.getWindow().getDecorView(),"saveSettings"));
            check(a.settings.language==1,"Language not applied");check(a.preferences.getInt("language",-1)==1,"Language not saved");
            runOnMainSync(app::resumeGame);seedDrive(a);await(()->app.renderer.state.distance>distance+.5f,30000,"Resume failed");
            runOnMainSync(app::backToMenu);frames(a,2);
            runOnMainSync(app::showModes);SystemClock.sleep(500);Dialog modes=dialog(a);
            runOnMainSync(()->click(modes.getWindow().getDecorView(),"mode3"));
            check(a.settings.mode==3,"AI mode choice not applied");runOnMainSync(app::startGame);
            await(()->app.renderer.state.mode==3&&app.renderer.state.phase==GameState.RUNNING,30000,"AI mode did not start");seedDrive(a);
            await(()->app.renderer.state.rivals[0].distance>1,45000,"AI did not drive");frames(a,2);screenshot(a,"05-ai-race");
            runOnMainSync(app::backToMenu);frames(a,1);
            runOnMainSync(()->{Settings cfg=new Settings(app.settings);cfg.mode=4;cfg.language=2;cfg.night=true;cfg.quality=0;app.applySettings(cfg);app.startGame();});
            await(()->app.renderer.state.mode==4&&app.renderer.state.phase==GameState.RUNNING,30000,"Time attack did not start");seedDrive(a);
            await(()->app.renderer.state.distance>2,45000,"Time attack did not progress");
            a.gl.queueEvent(()->{app.renderer.state.targetDistance=app.renderer.state.distance+3;app.renderer.state.gateDistance=1000;});
            await(()->app.renderer.state.phase==GameState.FINISHED,45000,"Time trial did not finish");
            await(()->new File(app.getFilesDir(),app.settings.ghostKey()+".ghost").isFile(),15000,"Ghost was not persisted");
            check(a.renderer.state.completedGhost!=null,"No recorded ghost");frames(a,2);screenshot(a,"06-time-result-de");
            runOnMainSync(app::startGame);await(()->app.renderer.state.phase==GameState.RUNNING,30000,"Ghost run did not restart");seedDrive(a);
            await(()->app.renderer.state.bestTime>0&&app.renderer.state.ghostVisible,30000,"Saved ghost not replayed");
            runOnMainSync(()->{app.applySettings(original);app.backToMenu();});frames(a,2);
            check(a.renderer.failure==null,"Graphics failure");check(a.renderer.lastGlError==0,"GL error "+a.renderer.lastGlError);
            check(a.sound.ready,"Audio engine unavailable");
            for(File file:app.getFilesDir().listFiles())if(file.getName().endsWith(".ghost"))check(file.delete(),"Replay cleanup");
            result.putString("smoke","PASS");result.putString("checks","live 3D garage; orbit; tuning persistence; drive; steering; pedals HUD; pause/resume; language; AI racing; time attack finish; saved ghost playback; quality and night; GLES3; audio");
            result.putInt("frames",a.renderer.framesRendered);result.putString("audio_initialized",Boolean.toString(a.sound.ready));finish(Activity.RESULT_OK,result);
        }catch(Throwable e){
            android.util.Log.e("NightFlowSmoke","Smoke failed",e);
            if(a!=null){try{screenshot(a,"99-failure");}catch(Exception ignored){}result.putString("state","phase="+a.renderer.state.phase+" frames="+a.renderer.framesRendered+" gl="+a.renderer.lastGlError);}
            result.putString("smoke","FAIL");result.putString("failure",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);
        }
    }
}
