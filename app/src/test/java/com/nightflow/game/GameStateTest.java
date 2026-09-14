package com.nightflow.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameStateTest {
    private GameState clear(int mode) {
        GameState g=new GameState(42);g.start(mode);g.countdown=0;
        for(GameState.Car c:g.traffic)c.active=false;
        return g;
    }
    @Test public void sweptCollisionCatchesFastCrossing() {
        assertTrue(GameState.sweptCollision(0,0,0,-15,15,4.3f));
        assertFalse(GameState.sweptCollision(3.4f,3.4f,0,-15,15,4.3f));
        assertFalse(GameState.sweptCollision(0,10,0,-20,0,4.3f));
    }
    @Test public void classicCrashEndsRunButZenRecovers() {
        for(int mode=0;mode<2;mode++) {
            GameState g=clear(mode);GameState.Car c=g.traffic[0];
            c.active=true;c.x=g.x;c.z=-1;c.speed=0;c.length=4.3f;
            g.tick(1f/60,0,false,false);
            assertEquals(1,g.crashSerial);
            assertEquals(mode==0?GameState.RUNNING:GameState.CRASHED,g.phase);
            if(mode==0){assertTrue(g.invincible>0);assertFalse(c.active);}
        }
    }
    @Test public void pauseFreezesAllMotionAndResumeCountsDown() {
        GameState g=clear(0);g.tick(.05f,0,false,false);g.pause();
        float x=g.x,d=g.distance,t=g.travel;
        for(int i=0;i<300;i++)g.tick(.016f,1,false,true);
        assertEquals(x,g.x,0);assertEquals(d,g.distance,0);assertEquals(t,g.travel,0);
        g.resume();assertEquals(GameState.RUNNING,g.phase);assertTrue(g.countdown>=1);
    }
    @Test public void brakingActuallyReducesSpeed() {
        GameState g=clear(0);g.speed=50;g.tick(.05f,0,true,true);
        assertTrue(g.speed<50);assertTrue(g.speed>12);
    }
    @Test public void touchAndTiltStayWithinRoadAndRejectNonFiniteInput() {
        GameState g=clear(0);
        for(int i=0;i<300;i++){for(GameState.Car c:g.traffic)c.active=false;g.tick(.05f,1,false,false);}
        assertTrue(g.x<=GameState.ROAD_LIMIT);assertTrue(g.x>5.5);
        g.tick(.05f,Float.NaN,false,false);g.tick(Float.NaN,0,false,false);
        assertTrue(Float.isFinite(g.x));assertTrue(Float.isFinite(g.distance));
    }
    @Test public void sensorAxesAccountForBothLandscapeOrientationsAndCalibration() {
        assertEquals(-3/3.8f,GameState.steerFromSensor(0,-3,1,0,1,false),.001f);
        assertEquals(3/3.8f,GameState.steerFromSensor(0,-3,3,0,1,false),.001f);
        assertEquals(0,GameState.steerFromSensor(0,-3,1,3,1,false),.001f);
        assertEquals(3/3.8f,GameState.steerFromSensor(0,-3,1,0,1,true),.001f);
        assertEquals(-2/3.8f,GameState.steerFromSensor(2,0,0,0,1,false),.001f);
        assertEquals(2/3.8f,GameState.steerFromSensor(2,0,2,0,1,false),.001f);
    }
    @Test public void passingCountsOnlyOnce() {
        GameState g=clear(0);g.speed=31;GameState.Car c=g.traffic[0];
        c.active=true;c.x=5.1f;c.z=4.4f;c.length=4.3f;c.speed=0;
        g.tick(.05f,0,false,false);assertEquals(1,g.passes);
        g.tick(.05f,0,false,false);assertEquals(1,g.passes);
    }
    private float distanceAt(int fps) {
        GameState g=clear(0);
        for(int i=0;i<fps*20;i++){for(GameState.Car c:g.traffic)c.active=false;g.tick(1f/fps,0,false,false);}
        return g.distance;
    }
    @Test public void progressionIsConsistentAcrossFrameRates() {
        assertEquals(distanceAt(30),distanceAt(120),1.5f);
    }
    @Test public void everyInitialTrafficRowLeavesAnOpenLane() {
        for(int seed=0;seed<200;seed++) {
            GameState g=new GameState(seed);g.start(2);
            boolean[][] taken=new boolean[3][4];int[] counts=new int[3];
            for(GameState.Car c:g.traffic) if(c.active) {
                int row=c.z> -100?0:c.z> -150?1:2,lane=-1;
                for(int i=0;i<4;i++)if(c.x==GameState.LANES[i])lane=i;
                assertTrue(lane>=0);assertFalse(taken[row][lane]);taken[row][lane]=true;counts[row]++;
            }
            for(int count:counts)assertTrue(count>=2&&count<=3);
        }
    }
}
