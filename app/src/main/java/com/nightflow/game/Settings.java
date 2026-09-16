package com.nightflow.game;

import android.content.SharedPreferences;

public final class Settings {
    public int quality = 2, fps = 60, control = 0, camera = 0, mode = 0, car = 0, paint = 0;
    public int language=0, density=1, raceLength=0, rims=0;
    public boolean autoGas=true, wet=true, neon=false;
    public final int[] engines=new int[4], gearboxes=new int[4], tyres=new int[4], kits=new int[4];
    public float sensitivity = 1.0f, fov = 62f, music = 0.55f, effects = 0.45f;
    public boolean bloom = true, shadows = true, night = false, vibration = true, showFps = false, invert = false;
    public static final String[] QUALITY = {"Štednja", "Uravnoteženo", "Visoko", "Ultra"};
    public static final String[] MODES = {"ZEN", "CLASSIC", "HARDCORE", "AI RACE", "TIME ATTACK"};
    public static final String[] CARS = {"AERO GT", "GRAND TOURER", "STREET S", "VORTEX R"};
    public static final int[] PAINTS = {0xff63dece, 0xffef765f, 0xffd9e3ed, 0xff8681d6, 0xffe5be6d, 0xff344860};

    public Settings() {}
    public Settings(Settings a) {
        language=a.language; density=a.density; raceLength=a.raceLength; rims=a.rims;
        autoGas=a.autoGas; wet=a.wet; neon=a.neon;
        System.arraycopy(a.engines,0,engines,0,4); System.arraycopy(a.gearboxes,0,gearboxes,0,4);
        System.arraycopy(a.tyres,0,tyres,0,4); System.arraycopy(a.kits,0,kits,0,4);
        quality=a.quality; fps=a.fps; control=a.control; camera=a.camera; mode=a.mode;
        car=a.car; paint=a.paint; sensitivity=a.sensitivity; fov=a.fov;
        music=a.music; effects=a.effects; bloom=a.bloom; shadows=a.shadows;
        night=a.night; vibration=a.vibration; showFps=a.showFps; invert=a.invert;
    }
    public float scale() { return new float[]{0.55f,0.72f,0.87f,1f}[quality]; }
    public void save(SharedPreferences p) {
        SharedPreferences.Editor e=p.edit();
        for(int i=0;i<4;i++) e.putInt("engine-"+i,engines[i]).putInt("gearbox-"+i,gearboxes[i]).putInt("tyres-"+i,tyres[i]).putInt("kit-"+i,kits[i]);
        e.putInt("language",language).putInt("density",density).putInt("raceLength",raceLength).putInt("rims",rims)
            .putBoolean("autoGas",autoGas).putBoolean("wet",wet).putBoolean("neon",neon).putInt("quality",quality).putInt("fps",fps).putInt("control",control)
            .putInt("camera",camera).putInt("mode",mode).putInt("car",car).putInt("paint",paint)
            .putFloat("sensitivity",sensitivity).putFloat("fov",fov)
            .putFloat("music",music).putFloat("effects",effects)
            .putBoolean("bloom",bloom).putBoolean("shadows",shadows)
            .putBoolean("night",night).putBoolean("vibration",vibration)
            .putBoolean("showFps",showFps).putBoolean("invert",invert).apply();
    }
    public static Settings read(SharedPreferences p) {
        Settings s=new Settings();
        s.language=bound(p.getInt("language",0),0,3); s.density=bound(p.getInt("density",1),0,2);
        s.raceLength=bound(p.getInt("raceLength",0),0,1); s.rims=bound(p.getInt("rims",0),0,2);
        s.autoGas=p.getBoolean("autoGas",true); s.wet=p.getBoolean("wet",true); s.neon=p.getBoolean("neon",false);
        for(int i=0;i<4;i++){s.engines[i]=bound(p.getInt("engine-"+i,0),0,3);s.gearboxes[i]=bound(p.getInt("gearbox-"+i,0),0,2);s.tyres[i]=bound(p.getInt("tyres-"+i,0),0,2);s.kits[i]=bound(p.getInt("kit-"+i,0),0,2);}
        s.quality=bound(p.getInt("quality",2),0,3);
        int fps=p.getInt("fps",60); s.fps=(fps==30||fps==120)?fps:60;
        s.control=bound(p.getInt("control",0),0,1); s.camera=bound(p.getInt("camera",0),0,2);
        s.mode=bound(p.getInt("mode",0),0,4); s.car=bound(p.getInt("car",0),0,3);
        s.paint=bound(p.getInt("paint",0),0,PAINTS.length-1);
        s.sensitivity=GameState.clamp(p.getFloat("sensitivity",1),0.4f,2.5f);
        s.fov=GameState.clamp(p.getFloat("fov",62),48,85);
        s.music=GameState.clamp(p.getFloat("music",0.55f),0,1);
        s.effects=GameState.clamp(p.getFloat("effects",0.45f),0,1);
        s.bloom=p.getBoolean("bloom",true); s.shadows=p.getBoolean("shadows",true);
        s.night=p.getBoolean("night",false); s.vibration=p.getBoolean("vibration",true);
        s.showFps=p.getBoolean("showFps",false); s.invert=p.getBoolean("invert",false);
        return s;
    }
    public String ghostKey(){return "v2-"+car+"-"+engines[car]+"-"+gearboxes[car]+"-"+tyres[car]+"-"+raceLength;}
    private static int bound(int n,int lo,int hi) { return Math.max(lo,Math.min(hi,n)); }
}
