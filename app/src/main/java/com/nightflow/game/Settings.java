package com.nightflow.game;

import android.content.SharedPreferences;

public final class Settings {
    public int quality = 2, fps = 60, control = 0, camera = 0, mode = 0, car = 0, paint = 0;
    public float sensitivity = 1.0f, fov = 62f, music = 0.55f, effects = 0.45f;
    public boolean bloom = true, shadows = true, night = false, vibration = true, showFps = false, invert = false;
    public static final String[] QUALITY = {"Štednja", "Uravnoteženo", "Visoko", "Ultra"};
    public static final String[] MODES = {"ZEN", "KLASIK", "IZAZOV"};
    public static final String[] CARS = {"AERO GT", "GRAND TOURER", "STREET S"};
    public static final int[] PAINTS = {0xff63dece, 0xffef765f, 0xffd9e3ed, 0xff8681d6, 0xffe5be6d, 0xff344860};

    public Settings() {}
    public Settings(Settings a) {
        quality=a.quality; fps=a.fps; control=a.control; camera=a.camera; mode=a.mode;
        car=a.car; paint=a.paint; sensitivity=a.sensitivity; fov=a.fov;
        music=a.music; effects=a.effects; bloom=a.bloom; shadows=a.shadows;
        night=a.night; vibration=a.vibration; showFps=a.showFps; invert=a.invert;
    }
    public float scale() { return new float[]{0.55f,0.72f,0.87f,1f}[quality]; }
    public void save(SharedPreferences p) {
        p.edit().putInt("quality",quality).putInt("fps",fps).putInt("control",control)
            .putInt("camera",camera).putInt("mode",mode).putInt("car",car).putInt("paint",paint)
            .putFloat("sensitivity",sensitivity).putFloat("fov",fov)
            .putFloat("music",music).putFloat("effects",effects)
            .putBoolean("bloom",bloom).putBoolean("shadows",shadows)
            .putBoolean("night",night).putBoolean("vibration",vibration)
            .putBoolean("showFps",showFps).putBoolean("invert",invert).apply();
    }
    public static Settings read(SharedPreferences p) {
        Settings s=new Settings();
        s.quality=bound(p.getInt("quality",2),0,3);
        int fps=p.getInt("fps",60); s.fps=(fps==30||fps==120)?fps:60;
        s.control=bound(p.getInt("control",0),0,1); s.camera=bound(p.getInt("camera",0),0,2);
        s.mode=bound(p.getInt("mode",0),0,2); s.car=bound(p.getInt("car",0),0,2);
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
    private static int bound(int n,int lo,int hi) { return Math.max(lo,Math.min(hi,n)); }
}
