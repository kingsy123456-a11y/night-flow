package com.nightflow.game;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;

public class UpgradeTest {
    private GameState run(Settings s){GameState g=new GameState(42);g.configure(s);g.start(s.mode);g.countdown=0;return g;}
    private void drive(GameState g,int ticks,boolean gas){for(int i=0;i<ticks;i++){for(GameState.Car c:g.traffic)c.active=false;g.tick(1f/120,0,false,gas);}}
    @Test public void engineUpgradeImprovesAccelerationAndGearboxChangesShiftDuration(){
        Settings stock=new Settings(),tuned=new Settings(stock);tuned.engines[0]=3;tuned.gearboxes[0]=2;
        GameState a=run(stock),b=run(tuned);drive(a,1200,true);drive(b,1200,true);
        assertTrue("Upgraded power must change speed",b.speed>a.speed+2);
        assertTrue(b.gear>1);assertTrue(b.shiftSerial>0);assertTrue(b.rpm>850&&b.rpm<=7601);
        assertTrue(b.vehicle.shiftDuration<a.vehicle.shiftDuration);
    }
    @Test public void brakingStopsAndReleaseCoastsWhenAutoThrottleIsOff(){
        Settings s=new Settings();s.autoGas=false;GameState g=run(s);g.speed=30;
        drive(g,120,false);assertTrue(g.speed<30);float old=g.speed;
        for(int i=0;i<120*5;i++)g.tick(1f/120,0,true,true);
        assertEquals(0,g.speed,.001);assertTrue(old>0);
    }
    @Test public void steeringHasInertiaButSettlesWithoutDrift(){
        GameState g=run(new Settings());g.speed=30;
        g.tick(1f/120,1,false,true);assertTrue(g.lateralVelocity>0&&g.lateralVelocity<1);
        for(int i=0;i<40;i++)g.tick(1f/120,1,false,false);
        float old=g.x;for(int i=0;i<240;i++)g.tick(1f/120,0,false,false);
        assertTrue(g.x>old);assertEquals(0,g.lateralVelocity,.001);
    }
    @Test public void aiMovesWithinRoadAndRaceFinishes(){
        Settings s=new Settings();s.mode=GameState.AI_RACE;GameState g=run(s);g.targetDistance=120;
        drive(g,120*30,true);assertEquals(GameState.FINISHED,g.phase);assertTrue(g.position>=1&&g.position<=4);
        for(GameState.Rival r:g.rivals){assertTrue(r.distance>0);assertTrue(Math.abs(r.x)<=GameState.ROAD_LIMIT);}
        float done=g.distance;g.tick(.05f,0,false,true);assertEquals(done,g.distance,0);
    }
    @Test public void missedTimeTrialGateAddsPenaltyAndReplayRoundTrips()throws Exception{
        Settings s=new Settings();s.mode=GameState.TIME_ATTACK;GameState g=run(s);
        g.targetDistance=80;g.gateDistance=20;g.gateX=5.1f;g.speed=30;
        drive(g,120*10,true);assertEquals(GameState.FINISHED,g.phase);assertEquals(3,g.penalty,0);
        assertNotNull(g.completedGhost);assertEquals(g.finishTime,g.completedGhost.duration,.001);
        ByteArrayOutputStream out=new ByteArrayOutputStream();g.completedGhost.write(out);
        GhostRun replay=GhostRun.read(new ByteArrayInputStream(out.toByteArray()));float[] position=new float[2];
        replay.at(replay.duration,position);assertEquals(80,position[1],.01);assertEquals(g.x,position[0],.01);
        replay.at(0,position);assertEquals(0,position[1],0);
    }
    @Test public void ghostIsNonCollidingAndInterpolatesCorrectly(){
        GhostRun ghost=new GhostRun(2,new float[]{0,-1,0,1,1,30,2,3,60});float[] p=new float[2];
        ghost.at(.5f,p);assertEquals(0,p[0],.0001);assertEquals(15,p[1],.0001);
        Settings s=new Settings();s.mode=4;GameState g=run(s);g.start(4,ghost);g.countdown=0;drive(g,120,true);
        assertEquals(GameState.RUNNING,g.phase);assertTrue(g.ghostVisible);assertEquals(0,g.crashSerial);
    }
    @Test public void corruptedReplayIsRejected()throws Exception{
        try{GhostRun.read(new ByteArrayInputStream(new byte[20]));fail();}catch(IOException expected){}
        try{new GhostRun(2,new float[]{0,0,0,1,Float.NaN,30});fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void tuningCopyIsIsolatedAndGhostRecordsUseMatchingPerformance(){
        Settings a=new Settings(),b=new Settings(a);b.engines[0]=3;b.tyres[0]=2;
        assertEquals(0,a.engines[0]);assertNotEquals(a.ghostKey(),b.ghostKey());
        b=new Settings(a);b.paint=5;assertEquals(a.ghostKey(),b.ghostKey());
    }
    @Test public void translationsCoverAllFourLanguages(){
        assertTrue(L.TEXT.size()>100);
        for(String[] values:L.TEXT.values()){assertEquals(4,values.length);for(String value:values)assertFalse(value.trim().isEmpty());}
    }
    @Test public void engineAudioIsFiniteActiveAndFadesToSilence(){
        EngineSynth engine=new EngineSynth();engine.configure(5500,1,8,false,0,40,true);float energy=0;
        for(int i=0;i<44100;i++){float f=engine.next();assertTrue(Float.isFinite(f));assertTrue(Math.abs(f)<1);energy+=f*f;}
        assertTrue(energy>1);engine.configure(850,0,8,false,1,0,false);
        float last=0;for(int i=0;i<44100;i++)last=engine.next();assertEquals(0,last,.0001);
    }
}
