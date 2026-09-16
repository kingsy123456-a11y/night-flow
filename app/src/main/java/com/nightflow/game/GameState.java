package com.nightflow.game;

import java.util.Random;

/** Fixed-step simulation is owned by GL; volatile instruments are safe for the HUD/audio. */
public final class GameState {
    public static final int MENU=0,RUNNING=1,PAUSED=2,CRASHED=3,FINISHED=4;
    public static final int ZEN=0,CLASSIC=1,HARDCORE=2,AI_RACE=3,TIME_ATTACK=4;
    public static final float ROAD_LIMIT=6.1f;
    public static final float[] LANES={-5.1f,-1.7f,1.7f,5.1f};
    public static final class Car {
        public float x,z,speed,length;public int shape,paint;public boolean active,counted;
    }
    public static final class Rival {
        public float x,distance,speed,decision,finishTime=-1;
        public int lane,shape,paint;
    }
    public final Car[] traffic=new Car[44];
    public final Rival[] rivals=new Rival[3];
    private final Random random;
    private Settings config=new Settings();
    public VehicleSpec vehicle=new VehicleSpec(config);
    public volatile int phase=MENU,mode=0,score=0,passes=0,combo=0,crashSerial=0;
    public volatile int gear=1,shiftSerial=0,position=1,missedGates=0;
    public volatile float speed=0,distance=0,x=-1.7f,steer=0,countdown=0,flash=0,near=0,invincible=0;
    public volatile float rpm=850,throttle=0,shiftTime=0,elapsed=0,penalty=0,finishTime=0;
    public volatile float lateralVelocity=0,yaw=0,roll=0,pitch=0,acceleration=0;
    public volatile float targetDistance=2000,gateDistance=250,gateX=1.7f,gateFlash=0;
    public volatile float ghostX=0,ghostZ=0,bestTime=0;
    public volatile boolean ghostVisible=false,braking=false;
    public volatile GhostRun completedGhost=null;
    public float travel=0,animation=0,wheelAngle=0;
    private float spawnClock=0,comboClock=0,scoreFraction=0;
    private int gateNumber=0;
    private float penaltyHold=0;
    private GhostRun ghost;
    private GhostRun.Recorder recorder;
    private final float[] replayPoint=new float[2];
    public GameState(){this(System.nanoTime());}
    public GameState(long seed){
        random=new Random(seed);for(int i=0;i<traffic.length;i++)traffic[i]=new Car();
        for(int i=0;i<rivals.length;i++)rivals[i]=new Rival();
    }
    public void configure(Settings s){config=new Settings(s);vehicle=new VehicleSpec(config);gear=Math.min(gear,vehicle.ratios.length);}
    public void start(int selectedMode){start(selectedMode,null);}
    public void start(int selectedMode,GhostRun replay){
        mode=selectedMode;score=passes=combo=0;scoreFraction=0;
        speed=distance=steer=lateralVelocity=yaw=roll=pitch=throttle=elapsed=penalty=finishTime=0;
        x=-1.7f;countdown=2.5f;gear=1;rpm=850;shiftTime=0;position=1;
        flash=near=invincible=spawnClock=comboClock=gateFlash=0;missedGates=gateNumber=0;penaltyHold=0;
        targetDistance=config.raceLength==0?2000:5000;gateDistance=250;gateX=1.7f;
        completedGhost=null;ghost=replay;bestTime=replay==null?0:replay.duration;ghostVisible=false;
        recorder=mode==TIME_ATTACK?new GhostRun.Recorder():null;
        if(recorder!=null)recorder.add(0,x,0,true);
        if(mode==TIME_ATTACK)random.setSeed(82461); // Repeatable course and no random traffic.
        for(Car c:traffic)c.active=false;
        if(mode!=TIME_ATTACK){spawnRow(-60);spawnRow(-111);spawnRow(-164);}
        for(int i=0;i<rivals.length;i++){
            Rival r=rivals[i];r.lane=i==0?0:i==1?2:3;r.x=LANES[r.lane];r.distance=0;
            r.speed=0;r.decision=0;r.finishTime=-1;r.shape=i;r.paint=i+1;
        }
        phase=RUNNING;
    }
    public void pause(){if(phase==RUNNING)phase=PAUSED;}
    public void resume(){if(phase==PAUSED){phase=RUNNING;countdown=Math.max(1,countdown);}}
    public void menu(){phase=MENU;speed=0;flash=near=invincible=countdown=0;ghostVisible=false;for(Car c:traffic)c.active=false;}
    public void tick(float rawDt,float input,boolean brake,boolean gas){
        if(!Float.isFinite(rawDt)||rawDt<=0)return;
        float dt=Math.min(rawDt,.05f);
        if(phase==PAUSED||phase==CRASHED||phase==FINISHED)return;
        animation+=dt;
        if(phase==MENU){travel=(travel+8*dt)%640;return;}
        flash=Math.max(0,flash-dt*2);near=Math.max(0,near-dt);gateFlash=Math.max(0,gateFlash-dt);
        invincible=Math.max(0,invincible-dt);
        if(countdown>0){countdown=Math.max(0,countdown-dt);return;}
        elapsed+=dt;braking=brake;
        float command=Float.isFinite(input)?clamp(input,-1,1):0;
        steer+=(command-steer)*(1-(float)Math.exp(-dt*10));
        float desiredLateral=steer*(3.8f+Math.min(speed,70)*.037f);
        lateralVelocity+=(desiredLateral-lateralVelocity)*(1-(float)Math.exp(-dt*6.5f*vehicle.grip));
        float oldX=x,oldDistance=distance;
        x=clamp(x+lateralVelocity*dt,-ROAD_LIMIT,ROAD_LIMIT);
        if(Math.abs(x)>=ROAD_LIMIT&&Math.signum(x)==Math.signum(lateralVelocity))lateralVelocity=0;
        yaw=-(float)Math.toDegrees(Math.atan2(lateralVelocity,Math.max(14,speed)));
        roll+=(-steer*Math.min(speed/30,1)*1.8f-roll)*(1-(float)Math.exp(-dt*5));
        float cruise=mode==ZEN?34:mode==CLASSIC?41:mode==HARDCORE?48:vehicle.topSpeed*.85f;
        float wanted=brake?0:gas?1:config.autoGas?clamp((cruise-speed)*.23f,0,1):0;
        throttle+=(wanted-throttle)*(1-(float)Math.exp(-dt*10));
        shiftTime=Math.max(0,shiftTime-dt);
        float mechanical=vehicle.engineRpm(speed,gear);
        if(shiftTime<=0){
            if(mechanical>6750&&gear<vehicle.ratios.length){gear++;shiftTime=vehicle.shiftDuration;shiftSerial++;}
            else if(gear>1&&mechanical<2450&&vehicle.engineRpm(speed,gear-1)<6100){gear--;shiftTime=vehicle.shiftDuration;shiftSerial++;}
        }
        float targetRpm=Math.min(7600,vehicle.engineRpm(speed,gear)+throttle*Math.max(0,1-speed/15)*1000);
        rpm+=(targetRpm-rpm)*(1-(float)Math.exp(-dt*(shiftTime>0?16:22)));
        float force=vehicle.driveAcceleration(rpm,gear,throttle)*(shiftTime>0?.08f:1);
        float drag=.15f+.00043f*speed*speed+(throttle<.08f?.45f:0);
        float oldSpeed=speed;
        speed=clamp(speed+(force-drag-(brake?15.5f*vehicle.grip:0))*dt,0,vehicle.topSpeed);
        if(penaltyHold>0){speed=0;penaltyHold=Math.max(0,penaltyHold-dt);}
        acceleration=(speed-oldSpeed)/dt;
        pitch+=(-acceleration*.15f-pitch)*(1-(float)Math.exp(-dt*6));
        distance+=speed*dt;travel=(travel+speed*dt)%640;wheelAngle=(wheelAngle+speed/.345f*57.2958f*dt)%360;
        scoreFraction+=speed*dt*.35f;
        if(scoreFraction>=1){int add=(int)scoreFraction;score+=add;scoreFraction-=add;}
        comboClock-=dt;if(comboClock<=0)combo=0;
        for(Car c:traffic){
            if(!c.active)continue;
            float oldZ=c.z;c.z+=(speed-c.speed)*dt;
            if(invincible<=0&&sweptCollision(oldX,x,c.x,oldZ,c.z,c.length)){
                crashSerial++;flash=1;
                if(mode==ZEN||mode==AI_RACE){speed*=.35f;invincible=2.5f;combo=0;c.active=false;if(mode==AI_RACE){penalty+=2;penaltyHold=2;}}
                else{phase=CRASHED;speed=0;return;}
            }
            if(!c.counted&&c.z>4.5f&&oldZ<=4.5f){
                c.counted=true;passes++;score+=35;float gap=Math.abs(x-c.x);
                if(gap>1.65f&&gap<2.8f&&invincible<=0){combo=Math.min(8,combo+1);comboClock=6;score+=100*combo;near=1.6f;}
            }
            if(c.z>24||c.z< -270)c.active=false;
        }
        if(mode==AI_RACE)tickRivals(dt,oldX);
        if(mode!=TIME_ATTACK){
            spawnClock+=dt;
            float interval=(mode==ZEN?3.5f:mode==CLASSIC?2.5f:mode==HARDCORE?1.5f:3.4f)/( .8f+config.density*.2f);
            if(spawnClock>interval){
                boolean clear=true;for(Car c:traffic)if(c.active&&c.z< -143){clear=false;break;}
                if(clear){spawnRow(-190);spawnClock=0;}
            }
        }else{
            if(distance>=gateDistance&&gateDistance<targetDistance){
                float cross=oldX+(x-oldX)*clamp((gateDistance-oldDistance)/Math.max(.0001f,distance-oldDistance),0,1);
                if(Math.abs(cross-gateX)>2.0f){penalty+=3;missedGates++;gateFlash=2;}else{score+=250;near=1;}
                gateNumber++;gateDistance+=250;gateX=LANES[new int[]{2,0,3,1}[gateNumber%4]];
            }
            if(distance<targetDistance)recorder.add(elapsed+penalty,x,distance,false);
            if(ghost!=null){ghost.at(elapsed+penalty,replayPoint);ghostX=replayPoint[0];ghostZ=distance-replayPoint[1];ghostVisible=Math.abs(ghostZ)<220;}
        }
        if((mode==AI_RACE||mode==TIME_ATTACK)&&distance>=targetDistance){
            float overshoot=(distance-targetDistance)/Math.max(.01f,speed);
            finishTime=elapsed+(mode==TIME_ATTACK?penalty:0)-overshoot;elapsed-=overshoot;distance=targetDistance;
            if(mode==AI_RACE){position=1;for(Rival r:rivals)if(r.finishTime>=0&&r.finishTime<finishTime)position++;}
            if(recorder!=null){recorder.add(finishTime,x,distance,true);completedGhost=recorder.finish(finishTime);}
            phase=FINISHED;
        }
    }
    private void tickRivals(float dt,float oldX){
        position=1;
        for(int i=0;i<rivals.length;i++){
            Rival r=rivals[i];float oldZ=distance-r.distance;
            if(r.finishTime<0){
                r.decision-=dt;
                if(r.decision<=0){
                    float best=-1;int lane=r.lane;
                    for(int n=0;n<4;n++){
                        float room=laneRoom(r,n);float desirability=room-Math.abs(n-r.lane)*9;
                        if(desirability>best){best=desirability;lane=n;}
                    }
                    r.lane=lane;r.decision=.55f+i*.12f;
                }
                float room=laneRoom(r,r.lane);
                float target=(60+i*2.7f)*(.88f+config.density*.08f);
                if(room<22)target=Math.min(target,Math.max(15,room*.9f));
                r.speed=approach(r.speed,target,(r.speed>target?13:5.5f+i*.25f)*dt);
                r.x=approach(r.x,LANES[r.lane],4.8f*dt);r.distance+=r.speed*dt;
                if(r.distance>=targetDistance){r.finishTime=elapsed-(r.distance-targetDistance)/Math.max(.1f,r.speed);r.distance=targetDistance;}
            }
            float newZ=distance-r.distance;
            if(invincible<=0&&sweptCollision(oldX,x,r.x,oldZ,newZ,4.4f)){
                crashSerial++;flash=1;speed*=.55f;r.speed*=.6f;invincible=2;penalty+=2;penaltyHold=2;
            }
            if(r.distance>distance||r.finishTime>=0)position++;
        }
    }
    private float laneRoom(Rival r,int lane){
        float room=120;
        for(Car c:traffic)if(c.active&&Math.abs(c.x-LANES[lane])<2){
            float gap=distance-c.z-r.distance;if(gap> -5)room=Math.min(room,Math.max(0,gap-5));
        }
        for(Rival other:rivals)if(other!=r&&Math.abs(other.x-LANES[lane])<2){
            float gap=other.distance-r.distance;if(gap> -5)room=Math.min(room,Math.max(0,gap-5));
        }
        float playerGap=distance-r.distance;
        if(Math.abs(x-LANES[lane])<2&&playerGap> -5)room=Math.min(room,Math.max(0,playerGap-5));
        return room;
    }
    private void spawnRow(float z){
        int count=mode==ZEN?1:mode==CLASSIC||mode==AI_RACE?2:2+random.nextInt(2);
        int empty=random.nextInt(4),first=random.nextInt(4);
        for(int n=0;n<4&&count>0;n++){
            int lane=(first+n)%4;if(lane==empty)continue;Car free=null;
            for(Car c:traffic)if(!c.active){free=c;break;}if(free==null)return;
            free.active=true;free.counted=false;free.x=LANES[lane];free.z=z-random.nextFloat()*5;
            free.speed=17+random.nextFloat()*7;free.shape=random.nextInt(4);free.paint=random.nextInt(Settings.PAINTS.length);
            free.length=free.shape==1?4.8f:4.4f;count--;
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
