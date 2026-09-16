package com.nightflow.game;

/** Shared by physics and the garage. Displayed upgrades are the upgrades actually simulated. */
public final class VehicleSpec {
    private static final int[] HP={265,345,225,440};
    private static final float[] MASS={1360,1720,1170,1460};
    public static final float[][] RATIOS={{3.60f,2.25f,1.60f,1.22f,1f,.81f},
        {3.80f,2.65f,1.95f,1.52f,1.20f,.97f,.78f},
        {3.70f,2.70f,2.06f,1.65f,1.38f,1.15f,.96f,.80f}};
    public final int hp, cylinders, gearbox;
    public final float mass, grip, shiftDuration, topSpeed;
    public final float[] ratios;
    public VehicleSpec(Settings s) {
        hp=Math.round(HP[s.car]*new float[]{1,1.18f,1.4f,1.67f}[s.engines[s.car]]);
        mass=MASS[s.car]; cylinders=s.car==1?8:s.car==3?10:s.car==2?4:6;
        gearbox=s.gearboxes[s.car];ratios=RATIOS[gearbox];
        shiftDuration=new float[]{.34f,.20f,.12f}[gearbox];
        grip=1+s.tyres[s.car]*.17f;
        topSpeed=58+(float)Math.cbrt(hp/265f)*18+gearbox*3;
    }
    public float engineRpm(float speed,int gear) {
        return Math.max(850,speed/(2*(float)Math.PI*.345f)*60*ratios[gear-1]*3.6f);
    }
    public float driveAcceleration(float rpm,int gear,float throttle) {
        float curve=.66f+.34f*(float)Math.sin(Math.PI*GameState.clamp((rpm-850)/7700,0,1));
        float torque=hp*.7457f*9550/6100*curve;
        return Math.min(10.8f*grip,torque*ratios[gear-1]*3.6f*.88f/.345f/mass)*throttle;
    }
}
