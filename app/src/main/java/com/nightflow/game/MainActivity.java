package com.nightflow.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Choreographer;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.MotionEvent;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class MainActivity extends Activity implements SensorEventListener,Choreographer.FrameCallback {
    GLSurfaceView gl;
    WorldRenderer renderer;
    HudView hud;
    SoundEngine sound;
    Settings settings;
    SharedPreferences preferences;
    private FrameLayout root,overlay;
    private SensorManager sensors;
    private Sensor steeringSensor;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private float filteredHorizontal=0,neutral=0;
    private boolean sensorReady=false,foreground=false,focusGranted=false;
    private long lastRenderRequest=0,lastHud=0;
    private int observedPhase=GameState.MENU,lastRotation=-1;
    private Dialog currentDialog;
    private String runGhostKey;
    private boolean newGhostRecord=false;
    private int safeLeft=18,safeRight=18;
    private static final int MINT=0xff67eddb,WHITE=0xfff1f5f7,MUTED=0xffa5b8c9,INK=0xff0b1523;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferences=getSharedPreferences("night-flow",MODE_PRIVATE);settings=Settings.read(preferences);
        sensors=(SensorManager)getSystemService(SENSOR_SERVICE);
        steeringSensor=sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if(steeringSensor==null) steeringSensor=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if(steeringSensor==null) settings.control=1;
        root=new FrameLayout(this);root.setBackgroundColor(INK);
        gl=new GLSurfaceView(this);gl.setEGLContextClientVersion(3);
        gl.setEGLConfigChooser(8,8,8,8,16,0);gl.setPreserveEGLContextOnPause(true);
        renderer=new WorldRenderer(this,settings);gl.setRenderer(renderer);
        gl.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        root.addView(gl,new FrameLayout.LayoutParams(-1,-1));
        hud=new HudView(this,renderer);root.addView(hud,new FrameLayout.LayoutParams(-1,-1));
        overlay=new FrameLayout(this);root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        if(Build.VERSION.SDK_INT>=30) root.setOnApplyWindowInsetsListener((v,insets)->{
            Insets i=insets.getInsets(WindowInsets.Type.displayCutout());
            safeLeft=Math.max(dp(18),i.left+dp(10));safeRight=Math.max(dp(18),i.right+dp(10));
            hud.safeLeft=safeLeft;hud.safeRight=safeRight;return insets;
        });
        sound=new SoundEngine(renderer.state,settings);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(change->{
                if(change==AudioManager.AUDIOFOCUS_GAIN) {focusGranted=true;sound.duck=1;sound.setActive(foreground);}
                else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) sound.duck=.2f;
                else {focusGranted=false;sound.setActive(false);pauseGame();}
            }).build();
        showMenu();immersive();applyFrameRate();
    }
    @Override protected void onResume() {
        super.onResume();foreground=true;gl.onResume();lastRenderRequest=0;lastHud=0;
        if(steeringSensor!=null) sensors.registerListener(this,steeringSensor,SensorManager.SENSOR_DELAY_GAME);
        focusGranted=audio.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        sound.setActive(focusGranted);immersive();
        Choreographer.getInstance().postFrameCallback(this);
    }
    @Override protected void onPause() {
        foreground=false;Choreographer.getInstance().removeFrameCallback(this);
        if(renderer!=null) {clearInputs();pauseGame();}
        sensors.unregisterListener(this);sound.setActive(false);
        audio.abandonAudioFocusRequest(focusRequest);focusGranted=false;
        gl.onPause();saveRecord();super.onPause();
    }
    @Override protected void onDestroy() {
        sound.close();
        if(currentDialog!=null) currentDialog.dismiss();
        super.onDestroy();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus) immersive();else if(renderer!=null&&renderer.state.phase==GameState.RUNNING) pauseGame();
    }
    @Override public void doFrame(long time) {
        if(!foreground) return;
        long interval=1_000_000_000L/settings.fps;
        if(time-lastRenderRequest>=interval-800_000L) {
            gl.requestRender();lastRenderRequest=time;
        }
        if(time-lastHud>=33_000_000L) {
            hud.invalidate();lastHud=time;
            int phase=renderer.state.phase;
            if(phase!=observedPhase) {
                observedPhase=phase;
                if(phase==GameState.CRASHED||phase==GameState.FINISHED) {saveRecord();saveGhost();showEnd();}
                else if(phase==GameState.PAUSED) showPause();
            }
        }
        Choreographer.getInstance().postFrameCallback(this);
    }
    @Override public void onSensorChanged(SensorEvent e) {
        int rotation=getWindowManager().getDefaultDisplay().getRotation();
        float horizontal=GameState.horizontalGravity(e.values[0],e.values[1],rotation);
        if(!sensorReady||rotation!=lastRotation) {
            filteredHorizontal=horizontal;neutral=horizontal;sensorReady=true;lastRotation=rotation;
        } else filteredHorizontal+=(horizontal-filteredHorizontal)*.18f;
        renderer.tilt=GameState.steerFromHorizontal(filteredHorizontal,neutral,settings.sensitivity,settings.invert);
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) {}
    public void calibrate() {neutral=filteredHorizontal;renderer.tilt=0;}
    public void startGame() {
        calibrate();clearInputs();overlay.removeAllViews();overlay.setBackgroundColor(Color.TRANSPARENT);
        observedPhase=GameState.RUNNING;
        renderer.garage=false;renderer.preview=null;newGhostRecord=false;
        Settings runSettings=new Settings(settings);runGhostKey=runSettings.ghostKey();
        GhostRun replay=runSettings.mode==GameState.TIME_ATTACK?loadGhost(runGhostKey):null;
        gl.queueEvent(()->{renderer.state.configure(runSettings);renderer.state.start(runSettings.mode,replay);});
    }
    public void pauseGame() {
        if(renderer.state.phase!=GameState.RUNNING) return;
        clearInputs();gl.queueEvent(()->renderer.state.pause());
        runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()) showPause();});
    }
    public void resumeGame() {
        clearInputs();overlay.removeAllViews();overlay.setBackgroundColor(Color.TRANSPARENT);
        observedPhase=GameState.RUNNING;gl.queueEvent(()->renderer.state.resume());
    }
    public void backToMenu() {
        saveRecord();clearInputs();gl.queueEvent(()->renderer.state.menu());
        observedPhase=GameState.MENU;showMenu();
    }
    public void clearInputs() {renderer.brake=false;renderer.gas=false;renderer.touch=0;hud.releaseTouches();}
    @Override public void onBackPressed() {
        int p=renderer.state.phase;
        if(p==GameState.RUNNING) pauseGame();
        else if(p==GameState.PAUSED) resumeGame();
        else if(p==GameState.CRASHED||p==GameState.FINISHED) backToMenu();
        else if(renderer.garage) showMenu();
        else super.onBackPressed();
    }
    public void onCollision() {
        runOnUiThread(()->{
            if(settings.vibration) {
                Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);
                if(v!=null&&v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(65,100));
            }
        });
    }
    private void saveRecord() {
        int mode=renderer.state.mode,score=renderer.state.score;
        if(score>preferences.getInt("best-"+mode,0)) preferences.edit().putInt("best-"+mode,score).apply();
    }
    private String t(String key,Object... args){return L.t(settings,key,args);}
    private GhostRun loadGhost(String key){
        AtomicFile file=new AtomicFile(new File(getFilesDir(),key+".ghost"));
        try(FileInputStream in=file.openRead()){return GhostRun.read(in);}
        catch(java.io.IOException e){return null;}
    }
    private void saveGhost(){
        GhostRun record=renderer.state.completedGhost;
        if(record==null||runGhostKey==null||renderer.state.phase!=GameState.FINISHED)return;
        GhostRun old=loadGhost(runGhostKey);if(old!=null&&old.duration<=record.duration)return;
        AtomicFile file=new AtomicFile(new File(getFilesDir(),runGhostKey+".ghost"));
        FileOutputStream out=null;
        try{out=file.startWrite();record.write(out);file.finishWrite(out);newGhostRecord=true;}
        catch(java.io.IOException e){if(out!=null)file.failWrite(out);android.util.Log.w("NightFlow","Replay save",e);}
    }
    private void showMenu(){
        renderer.garage=false;renderer.preview=null;
        overlay.removeAllViews();overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xec091523,0x82091523,0x00091523}));
        LinearLayout column=column();
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(320),-2,Gravity.LEFT|Gravity.CENTER_VERTICAL);
        p.leftMargin=Math.max(dp(26),safeLeft);overlay.addView(column,p);
        TextView badge=label(t("tag"),10,MINT);badge.setLetterSpacing(.16f);column.addView(badge);
        TextView title=label("NIGHT FLOW",37,WHITE);title.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        title.setLetterSpacing(.07f);column.addView(title);space(column,5);
        column.addView(label(t("tagline"),13,MUTED));space(column,14);
        Button drive=button(t("drive"),true,this::startGame);drive.setTag("drive");
        column.addView(drive,new LinearLayout.LayoutParams(-1,dp(47)));space(column,8);
        LinearLayout row=new LinearLayout(this);
        Button settingButton=button(t("settings"),false,this::showSettings);settingButton.setTag("settings");
        row.addView(settingButton,new LinearLayout.LayoutParams(0,dp(42),1));
        View gap=new View(this);row.addView(gap,new LinearLayout.LayoutParams(dp(8),1));
        Button garageButton=button(t("garage"),false,this::showGarage);garageButton.setTag("garage");garageButton.setTextSize(10);
        row.addView(garageButton,new LinearLayout.LayoutParams(0,dp(42),1));column.addView(row);space(column,9);
        Button modes=button(t("mode"+settings.mode)+"  ›",false,this::showModes);modes.setTag("modes");
        column.addView(modes,new LinearLayout.LayoutParams(-1,dp(38)));space(column,8);
        column.addView(label(t("record")+"  "+preferences.getInt("best-"+settings.mode,0)+"   ·   "+Settings.CARS[settings.car],10,MUTED));
        TextView bottom=label("2.0 / "+t("soundtrack"),9,0xffc7d3e0);bottom.setLetterSpacing(.10f);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);
        bp.rightMargin=Math.max(dp(24),safeRight);bp.bottomMargin=dp(16);overlay.addView(bottom,bp);
    }
    public void showModes(){
        Settings draft=new Settings(settings);LinearLayout box=column();box.setPadding(dp(22),dp(16),dp(22),dp(18));
        box.addView(label(t("modes"),23,WHITE));Dialog dialog=makeDialog(box);
        for(int i=0;i<5;i++){
            final int mode=i;space(box,12);
            Button b=button(t("mode"+i)+(settings.mode==i?"  ✓":""),settings.mode==i,()->{
                draft.mode=mode;applySettings(draft);dialog.dismiss();showMenu();
            });b.setTag("mode"+i);box.addView(b,new LinearLayout.LayoutParams(-1,dp(43)));
            space(box,5);box.addView(label(t("desc"+i),12,MUTED));
        }
        space(box,16);spinner(box,t("traffic"),L.options(settings,"density",3),draft.density,n->draft.density=n);
        spinner(box,t("length"),new String[]{"2 km","5 km"},draft.raceLength,n->draft.raceLength=n);
        box.addView(label(t("ghostHint"),11,MUTED));dialog.show();sizeDialog(dialog);
    }
    private LinearLayout centerPanel(String title,String subtitle){
        overlay.removeAllViews();overlay.setBackgroundColor(0xa6091523);
        LinearLayout panel=column();panel.setPadding(dp(22),dp(16),dp(22),dp(16));
        panel.setBackground(shape(0xf20b1929,0xff294357,18));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(345),-2,Gravity.CENTER);overlay.addView(panel,p);
        panel.addView(label(title,24,WHITE));panel.addView(label(subtitle,12,MUTED));space(panel,12);return panel;
    }
    private void showPause(){
        if(currentDialog!=null&&currentDialog.isShowing())return;
        LinearLayout panel=centerPanel(t("pause"),t("waiting"));
        panel.addView(button(t("resume"),true,this::resumeGame),new LinearLayout.LayoutParams(-1,dp(42)));space(panel,7);
        panel.addView(button(t("calibrate"),false,()->{calibrate();android.widget.Toast.makeText(this,t("calibrated"),android.widget.Toast.LENGTH_SHORT).show();}),new LinearLayout.LayoutParams(-1,dp(37)));
        space(panel,7);panel.addView(button(t("settings"),false,this::showSettings),new LinearLayout.LayoutParams(-1,dp(37)));
        space(panel,7);panel.addView(button(t("menu"),false,this::backToMenu),new LinearLayout.LayoutParams(-1,dp(37)));
    }
    private void showEnd(){
        GameState g=renderer.state;boolean race=g.mode>=GameState.AI_RACE;
        LinearLayout panel=centerPanel(t(g.phase==GameState.FINISHED?"finished":"ended"),t("result",g.distance/1000,g.passes));
        TextView points=label(race?L.clock(g.finishTime):""+g.score,34,MINT);points.setTypeface(Typeface.DEFAULT_BOLD);panel.addView(points);
        if(g.mode==GameState.AI_RACE)panel.addView(label(t("place",g.position),16,WHITE));
        if(g.penalty>0)panel.addView(label(t("penalty",g.penalty),12,0xffffb09a));
        if(g.mode==GameState.TIME_ATTACK){
            panel.addView(label(newGhostRecord?t("newbest"):t("record")+"  "+(g.bestTime>0?L.clock(g.bestTime):"—"),11,MINT));
        }else if(!race)panel.addView(label(t("record")+"  "+preferences.getInt("best-"+g.mode,0),11,MUTED));
        space(panel,12);panel.addView(button(t("again"),true,this::startGame),new LinearLayout.LayoutParams(-1,dp(42)));space(panel,8);
        panel.addView(button(t("menu"),false,this::backToMenu),new LinearLayout.LayoutParams(-1,dp(38)));
    }
    public void showSettings(){
        if(renderer.state.phase==GameState.RUNNING)pauseGame();
        Settings draft=new Settings(settings);LinearLayout box=column();box.setPadding(dp(22),dp(16),dp(22),dp(18));
        box.addView(label(t("settings"),23,WHITE));space(box,10);
        spinner(box,t("language"),L.LANGUAGES,draft.language,n->draft.language=n);
        heading(box,t("image"));spinner(box,t("graphics"),L.options(settings,"quality",4),draft.quality,n->draft.quality=n);
        spinner(box,t("fps"),new String[]{"30 FPS","60 FPS","120 FPS"},draft.fps==30?0:draft.fps==60?1:2,n->draft.fps=new int[]{30,60,120}[n]);
        box.addView(label(t("fpsHint"),11,MUTED));
        toggle(box,t("bloom"),draft.bloom,b->draft.bloom=b);toggle(box,t("shadows"),draft.shadows,b->draft.shadows=b);
        toggle(box,t("night"),draft.night,b->draft.night=b);toggle(box,t("wet"),draft.wet,b->draft.wet=b);
        toggle(box,t("showFps"),draft.showFps,b->draft.showFps=b);
        spinner(box,t("camera"),L.options(settings,"camera",3),draft.camera,n->draft.camera=n);
        slider(box,t("fov"),48,85,(int)draft.fov,"°",n->draft.fov=n);
        heading(box,t("controlHeading"));spinner(box,t("controls"),L.options(settings,"control",2),draft.control,n->draft.control=n);
        if(steeringSensor==null)box.addView(label(t("sensorMissing"),12,MINT));
        slider(box,t("sensitivity"),40,250,Math.round(draft.sensitivity*100),"%",n->draft.sensitivity=n/100f);
        toggle(box,t("invert"),draft.invert,b->draft.invert=b);toggle(box,t("autoGas"),draft.autoGas,b->draft.autoGas=b);
        box.addView(label(t("controlsHint"),12,MUTED));
        heading(box,t("audio"));slider(box,t("music"),0,100,Math.round(draft.music*100),"%",n->draft.music=n/100f);
        slider(box,t("effects"),0,100,Math.round(draft.effects*100),"%",n->draft.effects=n/100f);
        toggle(box,t("vibration"),draft.vibration,b->draft.vibration=b);space(box,12);
        Dialog dialog=makeDialog(box);Button save=button(t("save"),true,()->{
            if(steeringSensor==null)draft.control=1;applySettings(draft);dialog.dismiss();refreshOverlay();
        });save.setTag("saveSettings");box.addView(save,new LinearLayout.LayoutParams(-1,dp(44)));
        space(box,8);box.addView(button(t("cancel"),false,dialog::dismiss),new LinearLayout.LayoutParams(-1,dp(40)));
        dialog.show();sizeDialog(dialog);
    }
    public void showGarage(){
        renderer.garage=true;renderer.orbitYaw=34;clearInputs();showGaragePanel(new Settings(settings));
    }
    private void showGaragePanel(Settings draft){
        overlay.removeAllViews();overlay.setBackgroundColor(Color.TRANSPARENT);
        int screen=getResources().getDisplayMetrics().widthPixels;
        int panelWidth=Math.min(dp(338),Math.round(screen*.43f));
        renderer.garageFraction=(screen-panelWidth)/(float)screen;
        View orbit=new View(this){
            private float last;
            @Override public boolean onTouchEvent(MotionEvent e){
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN)last=e.getX();
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE){renderer.orbitYaw+=(e.getX()-last)*.4f;last=e.getX();}
                if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;
            }
            @Override public boolean performClick(){super.performClick();return true;}
        };
        orbit.setTag("orbit");overlay.addView(orbit,new FrameLayout.LayoutParams(screen-panelWidth,-1,Gravity.LEFT));
        LinearLayout title=column();title.addView(label(Settings.CARS[draft.car],25,WHITE));title.addView(label(t("rotate"),12,MUTED));
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(screen-panelWidth-dp(48),-2,Gravity.TOP|Gravity.LEFT);
        tp.leftMargin=Math.max(dp(24),safeLeft);tp.topMargin=dp(18);overlay.addView(title,tp);
        TextView stats=label("",15,WHITE);stats.setPadding(dp(14),dp(12),dp(14),dp(12));
        stats.setBackground(shape(0xd30c1928,0xff284555,14));
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(screen-panelWidth-dp(48),-2,Gravity.BOTTOM|Gravity.LEFT);
        sp.leftMargin=Math.max(dp(24),safeLeft);sp.bottomMargin=dp(18);overlay.addView(stats,sp);
        Runnable preview=()->{
            renderer.preview=new Settings(draft);VehicleSpec spec=new VehicleSpec(draft);
            stats.setText(spec.hp+" "+t("power")+"   /   "+spec.cylinders+" cyl   /   "+spec.ratios.length+" AT\n"+
                Math.round(spec.shiftDuration*1000)+" ms "+t("shift")+"   ·   +"+Math.round((spec.grip-1)*100)+"% "+t("grip"));
        };preview.run();
        LinearLayout panel=column();panel.setPadding(dp(16),dp(14),Math.max(dp(16),safeRight),dp(14));
        panel.setBackground(shape(0xf70a1623,0xff294253,0));
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(panelWidth,-1,Gravity.RIGHT);overlay.addView(panel,pp);
        panel.addView(label(t("workshop"),18,MINT));space(panel,8);
        ScrollView scroll=new ScrollView(this);LinearLayout box=column();scroll.addView(box);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        garageSpinner(box,t("car"),Settings.CARS,draft.car,n->{draft.car=n;showGaragePanel(draft);});
        garageSpinner(box,t("paint"),L.options(settings,"paint",6),draft.paint,n->{draft.paint=n;preview.run();});
        garageSpinner(box,t("engine"),L.options(settings,"stage",4),draft.engines[draft.car],n->{draft.engines[draft.car]=n;preview.run();});
        garageSpinner(box,t("gearbox"),L.options(settings,"gearbox",3),draft.gearboxes[draft.car],n->{draft.gearboxes[draft.car]=n;preview.run();});
        garageSpinner(box,t("tyres"),L.options(settings,"tyres",3),draft.tyres[draft.car],n->{draft.tyres[draft.car]=n;preview.run();});
        garageSpinner(box,t("kit"),L.options(settings,"kit",3),draft.kits[draft.car],n->{draft.kits[draft.car]=n;preview.run();});
        garageSpinner(box,t("rims"),L.options(settings,"rims",3),draft.rims,n->{draft.rims=n;preview.run();});
        toggle(box,t("neon"),draft.neon,b->{draft.neon=b;preview.run();});box.addView(label(t("tuneHint"),11,MUTED));space(box,12);
        space(panel,8);Button save=button(t("applyTune"),true,()->{applySettings(draft);showMenu();});save.setTag("saveTune");
        panel.addView(save,new LinearLayout.LayoutParams(-1,dp(42)));space(panel,6);
        panel.addView(button(t("cancel"),false,this::showMenu),new LinearLayout.LayoutParams(-1,dp(34)));
    }
    private void garageSpinner(LinearLayout box,String title,String[] values,int selected,IntConsumer change){
        space(box,8);box.addView(label(title,12,MUTED));Spinner spinner=new Spinner(this);
        setupSpinner(spinner,values,selected,change);box.addView(spinner,new LinearLayout.LayoutParams(-1,dp(41)));
    }
    void applySettings(Settings changed) {
        settings=new Settings(changed);settings.save(preferences);renderer.settings=settings;sound.settings=settings;
        applyFrameRate();clearInputs();lastRenderRequest=0;
    }
    private void refreshOverlay() {
        if(renderer.state.phase==GameState.MENU) showMenu();
        else if(renderer.state.phase==GameState.PAUSED) showPause();
    }
    private Dialog makeDialog(LinearLayout box) {
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(box);
        scroll.setBackground(shape(0xff0c1a2b,0xff294357,18));dialog.setContentView(scroll);
        dialog.setOnDismissListener(d->{currentDialog=null;immersive();});
        currentDialog=dialog;return dialog;
    }
    private void sizeDialog(Dialog dialog) {
        Window window=dialog.getWindow();if(window==null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setLayout(Math.min(dp(590),(int)(getResources().getDisplayMetrics().widthPixels*.88f)),
            (int)(getResources().getDisplayMetrics().heightPixels*.85f));
    }
    private void applyFrameRate() {
        Display display=getWindowManager().getDefaultDisplay();
        WindowManager.LayoutParams params=getWindow().getAttributes();
        Display.Mode best=null;
        for(Display.Mode m:display.getSupportedModes()) {
            if(m.getPhysicalWidth()!=display.getMode().getPhysicalWidth()||m.getPhysicalHeight()!=display.getMode().getPhysicalHeight()) continue;
            if(m.getRefreshRate()+.5f>=settings.fps&&(best==null||m.getRefreshRate()<best.getRefreshRate())) best=m;
        }
        if(best!=null) params.preferredDisplayModeId=best.getModeId();
        params.preferredRefreshRate=settings.fps;getWindow().setAttributes(params);
    }
    private void immersive() {
        if(Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c=getWindow().getInsetsController();
            if(c!=null){c.hide(WindowInsets.Type.systemBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
        } else getWindow().getDecorView().setSystemUiVisibility(5894);
    }
    public void showGraphicsError(String message) {
        if(isFinishing()||isDestroyed()) return;
        new AlertDialog.Builder(this).setTitle(t("graphicsError"))
            .setMessage(t("graphicsNeed")+message)
            .setPositiveButton(t("close"),(d,w)->finish()).show();
    }
    int dp(float value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void space(LinearLayout l,int height) {l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private TextView label(String text,int size,int color) {
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");
        return t;
    }
    private GradientDrawable shape(int color,int stroke,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;
    }
    private Button button(String title,boolean primary,Runnable action) {
        Button b=new Button(this);b.setText(title);b.setTextSize(12);b.setTextColor(primary?INK:WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(dp(10),0,dp(10),0);b.setLetterSpacing(.05f);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x447df5e4),
            shape(primary?MINT:0x99152335,primary?MINT:0xff355066,10),null));
        b.setOnClickListener(v->action.run());return b;
    }
    private void heading(LinearLayout l,String title) {space(l,14);TextView t=label(title,11,MINT);t.setLetterSpacing(.14f);l.addView(t);space(l,5);}
    private void spinner(LinearLayout l,String title,String[] values,int selected,IntConsumer change) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(title,13,WHITE),new LinearLayout.LayoutParams(0,dp(44),1));
        Spinner s=new Spinner(this);
        setupSpinner(s,values,selected,change);
        row.addView(s,new LinearLayout.LayoutParams(dp(225),dp(44)));l.addView(row);
    }
    private void setupSpinner(Spinner s,String[] values,int selected,IntConsumer change){
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setSelection(selected);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            int previous=selected;
            public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(position!=previous){previous=position;change.accept(position);}}
            public void onNothingSelected(AdapterView<?> p){}
        });
    }
    private void toggle(LinearLayout l,String title,boolean value,Consumer<Boolean> change) {
        Switch s=new Switch(this);s.setText(title);s.setTextSize(13);s.setTextColor(WHITE);
        s.setPadding(0,dp(7),0,dp(7));s.setChecked(value);s.setOnCheckedChangeListener((b,c)->change.accept(c));
        l.addView(s,new LinearLayout.LayoutParams(-1,dp(43)));
    }
    private void slider(LinearLayout l,String title,int min,int max,int value,String suffix,IntConsumer change) {
        TextView text=label(title+"   "+value+suffix,13,WHITE);space(l,8);l.addView(text);
        SeekBar seek=new SeekBar(this);seek.setMax(max-min);seek.setProgress(value-min);l.addView(seek,new LinearLayout.LayoutParams(-1,dp(35)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){int v=p+min;text.setText(title+"   "+v+suffix);change.accept(v);}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });
    }
}
