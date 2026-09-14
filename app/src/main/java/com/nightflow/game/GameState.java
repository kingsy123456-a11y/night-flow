package com.nightflow.game;

import java.util.Random;

/** Simulation is owned by the GL thread; volatile instruments may be read by the UI/audio threads. */
public final class GameState {
    public static final int MENU=0, RUNNING=1, PAUSED=2, CRASHED=3;
    public static final float ROAD_LIMIT=6.1f;
    public static final float[] LANES={-5.1f,-1.7f,1.7f,5.1f};
    public static final class Car {
        public float x,z,speed,length;
        public int shape,paint;
        public boolean active,counted;
    }
    public final Car[] traffic=new Car[28];
    private final Random random;
    public volatile int phase=MENU, mode=0, score=0, passes=0, combo=0, crashSerial=0;
    public volatile float speed=0, distance=0, x=-1.7f, steer=0, countdown=0, flash=0, near=0, invincible=0;
    public float travel=0, animation=0;
    private float spawnClock=0, comboClock=0, scoreFraction=0;
    public GameState() { this(System.nanoTime()); }
    public GameState(long seed) {
        random=new Random(seed);
        for(int i=0;i<traffic.length;i++) traffic[i]=new Car();
    }
    public void start(int selectedMode) {
        mode=selectedMode; score=passes=combo=0; scoreFraction=0;
        speed=0; distance=0; x=-1.7f; steer=0; countdown=2.5f;
        flash=near=invincible=0; spawnClock=0; comboClock=0;
        for(Car c:traffic) c.active=false;
        spawnRow(-60); spawnRow(-111); spawnRow(-164);
        phase=RUNNING;
    }
    public void pause() { if(phase==RUNNING) phase=PAUSED; }
    public void resume() { if(phase==PAUSED) { phase=RUNNING; countdown=Math.max(1f,countdown); } }
    public void menu() { phase=MENU; speed=0; for(Car c:traffic) c.active=false; }
    public void tick(float rawDt,float input,boolean brake,boolean gas) {
        if(!Float.isFinite(rawDt)||rawDt<=0) return;
        float dt=Math.min(rawDt,0.05f);
        if(phase==PAUSED||phase==CRASHED) return;
        animation+=dt;
        if(phase==MENU) { travel=(travel+12*dt)%640f; return; }
        flash=Math.max(0,flash-dt*2f); near=Math.max(0,near-dt);
        invincible=Math.max(0,invincible-dt);
        if(countdown>0) { countdown=Math.max(0,countdown-dt); return; }
        float command=Float.isFinite(input)?clamp(input,-1,1):0;
        steer+=(command-steer)*(1f-(float)Math.exp(-dt*9));
        float target=brake?12f:(gas?(mode==2?65:55):(mode==0?31:mode==1?38:46));
        speed=approach(speed,target,(brake?23:6.0f)*dt);
        float oldX=x;
        x=clamp(x+steer*(4.8f+speed*0.045f)*dt,-ROAD_LIMIT,ROAD_LIMIT);
        distance+=speed*dt;
        travel=(travel+speed*dt)%640f;
        scoreFraction+=speed*dt*0.35f;
        if(scoreFraction>=1) { int add=(int)scoreFraction; score+=add; scoreFraction-=add; }
        comboClock-=dt;
        if(comboClock<=0) combo=0;
        for(Car c:traffic) {
            if(!c.active) continue;
            float oldZ=c.z;
            c.z+=(speed-c.speed)*dt;
            if(invincible<=0 && sweptCollision(oldX,x,c.x,oldZ,c.z,c.length)) {
                crashSerial++; flash=1;
                if(mode==0) {
                    speed=12; invincible=2.8f; combo=0; c.active=false;
                } else {
                    phase=CRASHED; speed=0; return;
                }
            }
            if(!c.counted && c.z>4.5f && oldZ<=4.5f) {
                c.counted=true; passes++; score+=35;
                float gap=Math.abs(x-c.x);
                if(gap>1.65f&&gap<2.8f&&invincible<=0) {
                    combo=Math.min(8,combo+1); comboClock=6; score+=100*combo; near=1.6f;
                }
            }
            if(c.z>20||c.z< -240) c.active=false;
        }
        spawnClock+=dt;
        float interval=mode==0?3.8f:mode==1?2.9f:2.2f;
        if(spawnClock>interval) {
            boolean clear=true;
            for(Car c:traffic) if(c.active&&c.z< -125f) { clear=false; break; }
            if(clear) { spawnRow(-174); spawnClock=0; }
        }
    }
    private void spawnRow(float z) {
        int count=mode==0?1:mode==1?2:2+random.nextInt(2);
        int empty=random.nextInt(4);
        int first=random.nextInt(4);
        for(int n=0;n<4&&count>0;n++) {
            int lane=(first+n)%4;
            if(lane==empty) continue;
            Car free=null;
            for(Car c:traffic) if(!c.active) { free=c; break; }
            if(free==null) return;
            free.active=true; free.counted=false; free.x=LANES[lane];
            free.z=z-random.nextFloat()*5;
            free.speed=17+random.nextFloat()*5;
            free.shape=random.nextInt(3); free.paint=random.nextInt(6);
            free.length=free.shape==1?4.65f:4.25f;
            count--;
        }
    }
    /** Relative swept AABB: catches a collision even if a car crosses between frames. */
    public static boolean sweptCollision(float oldX,float newX,float carX,float oldZ,float newZ,float length) {
        float halfZ=(4.3f+length)*0.5f-0.32f;
        float[] range={0,1};
        return slab(oldX-carX,newX-oldX,1.60f,range)&&slab(oldZ,newZ-oldZ,halfZ,range);
    }
    private static boolean slab(float pos,float delta,float half,float[] range) {
        if(Math.abs(delta)<0.000001f) return Math.abs(pos)<half;
        float a=(-half-pos)/delta,b=(half-pos)/delta;
        if(a>b) {float t=a;a=b;b=t;}
        range[0]=Math.max(range[0],a); range[1]=Math.min(range[1],b);
        return range[0]<=range[1];
    }
    public static float steerFromSensor(float gx,float gy,int rotation,float zero,float sensitivity,boolean invert) {
        // Sensor axes do not rotate with the display. Surface rotation is 0,1,2,3.
        float horizontal=rotation==1?-gy:rotation==3?gy:rotation==2?-gx:gx;
        return steerFromHorizontal(horizontal,zero,sensitivity,invert);
    }
    public static float horizontalGravity(float gx,float gy,int rotation) {
        return rotation==1?-gy:rotation==3?gy:rotation==2?-gx:gx;
    }
    public static float steerFromHorizontal(float horizontal,float zero,float sensitivity,boolean invert) {
        float value=-(horizontal-zero)/3.8f;
        if(Math.abs(value)<0.025f) value=0;
        return clamp(value*sensitivity*(invert?-1:1),-1,1);
    }
    public static float clamp(float v,float a,float b) { return Float.isFinite(v)?Math.max(a,Math.min(b,v)):a; }
    private static float approach(float v,float target,float d) { return v<target?Math.min(v+d,target):Math.max(v-d,target); }
}
