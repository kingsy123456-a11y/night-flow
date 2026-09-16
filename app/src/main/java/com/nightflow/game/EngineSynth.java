package com.nightflow.game;

/** Combustion pulses, exhaust resonance, intake noise and a short shift transient. */
public final class EngineSynth {
    private static final float RATE=44100;
    private double phase,crank;
    private float rpm=850,targetRpm=850,load,targetLoad,level,targetLevel,pulse,low,rumble,air,shift;
    private float speed;private int cylinders=6,serial=-1,random=7812;private boolean cut;
    public void configure(float rpm,float throttle,int cylinders,boolean shifting,int shiftSerial,float speed,boolean enabled){
        targetRpm=GameState.clamp(rpm,600,8000);targetLoad=GameState.clamp(throttle,0,1);
        this.cylinders=cylinders;cut=shifting;this.speed=speed;targetLevel=enabled?1:0;
        if(serial>=0&&serial!=shiftSerial)shift=1;serial=shiftSerial;
    }
    public float next(){
        rpm+=(targetRpm-rpm)*.002f;load+=(targetLoad-load)*.001f;level+=(targetLevel-level)*.0015f;
        random=random*1664525+1013904223;float noise=(random>>8)/8388608f;
        crank+=rpm/60.0/RATE;if(crank>1)crank-=1;
        phase+=rpm*cylinders/120.0/RATE;
        if(phase>=1){phase-=1;pulse+=(.65f+.22f*noise)*(cut?.12f:1);}
        pulse*=.93f;
        float raw=pulse-.12f;
        low+=(raw-low)*(.04f+load*.12f);rumble+=(low-rumble)*.024f;
        air+=(noise-air)*.13f;
        float combustion=(low-rumble)*(.65f+load*.75f);
        float cycle=(float)Math.sin(crank*Math.PI*2)*(cylinders==8?.04f:.016f);
        float intake=(noise-air)*(.005f+load*.021f)*(cut?.15f:1);
        float wind=air*GameState.clamp(speed/80,0,1)*.026f;
        float transientSound=air*shift*.20f;shift*=.9992f;
        return (combustion+cycle+intake+wind+transientSound)*level*.7f;
    }
}
