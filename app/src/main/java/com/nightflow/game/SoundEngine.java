package com.nightflow.game;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

/** An original 78 BPM composition, synthesized locally; no recordings or network assets. */
public final class SoundEngine {
    private static final int RATE=44100,TABLE=8192;
    private final float[] sine=new float[TABLE],frequency=new float[128];
    private final float[] delayL=new float[13231],delayR=new float[17771];
    private final GameState state;
    public volatile Settings settings;
    public volatile boolean active=false,ready=false;
    public volatile float duck=1;
    private volatile boolean alive=true;
    private final Object signal=new Object();
    private final Thread thread;
    private long sample=0;
    private int dl=0,dr=0,random=12347,lastCrash=0;
    private float impact=0;
    private final EngineSynth engine=new EngineSynth();
    private static final int[][] CHORDS={{52,55,59,62},{48,52,55,59},{43,47,50,54},{50,54,57,64}};
    private static final int[] MELODY={0,7,12,10,7,3,14,7,0,12,7,3,10,7,2,7};
    public SoundEngine(GameState state,Settings settings) {
        this.state=state;this.settings=settings;
        for(int i=0;i<TABLE;i++) sine[i]=(float)Math.sin(i*Math.PI*2/TABLE);
        for(int i=0;i<128;i++) frequency[i]=(float)(440*Math.pow(2,(i-69)/12.0));
        thread=new Thread(this::run,"NightFlow-audio");thread.setDaemon(true);thread.start();
    }
    public void setActive(boolean enabled) { active=enabled;synchronized(signal){signal.notifyAll();} }
    public void close() { alive=false;setActive(false); }
    private float osc(double cycles) { return sine[((int)(cycles*TABLE))&(TABLE-1)]; }
    private float noise() { random=random*1664525+1013904223;return (random>>8)/8388608f; }
    private void run() {
        AudioTrack track=null;
        try {
            int minimum=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
            track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(16384,minimum*2)).build();
            if(track.getState()!=AudioTrack.STATE_INITIALIZED) return;
            ready=true;boolean playing=false;short[] buffer=new short[2048];
            while(alive) {
                if(!active) {
                    if(playing) {track.pause();track.flush();playing=false;}
                    synchronized(signal){if(alive&&!active) signal.wait(150);}
                    continue;
                }
                if(!playing) {track.play();playing=true;}
                generate(buffer);
                int written=0;
                while(written<buffer.length&&alive&&active) {
                    int n=track.write(buffer,written,buffer.length-written,AudioTrack.WRITE_BLOCKING);
                    if(n<0) throw new IllegalStateException("AudioTrack write "+n);
                    if(n==0) break;
                    written+=n;
                }
            }
        } catch(Exception e) {Log.w("NightFlow","Audio unavailable",e);}
        finally {ready=false;if(track!=null){try{track.pause();}catch(IllegalStateException ignored){}track.release();}}
    }
    private void generate(short[] out) {
        Settings cfg=settings;
        double beatSeconds=60.0/78.0;
        int cylinders=cfg.car==1?8:cfg.car==3?10:cfg.car==2?4:6;
        engine.configure(state.rpm,state.throttle,cylinders,state.shiftTime>0,state.shiftSerial,state.speed,
            state.phase==GameState.RUNNING);
        if(lastCrash!=state.crashSerial) {lastCrash=state.crashSerial;impact=.45f;}
        for(int i=0;i<out.length;i+=2,sample++) {
            double time=sample/(double)RATE;
            double beats=time/beatSeconds;
            int chordIndex=((int)(beats/16))%4;
            int[] chord=CHORDS[chordIndex];
            double inChord=beats%16;
            float chordEnvelope=(float)(Math.min(1,inChord*1.5)*Math.min(1,(16-inChord)*2));
            float padL=0,padR=0;
            for(int n=0;n<4;n++) {
                double ph=time*frequency[chord[n]];
                padL+=osc(ph*.9993+n*.07)*.024f;
                padR+=osc(ph*1.0007+n*.12)*.024f;
            }
            padL*=chordEnvelope;padR*=chordEnvelope;
            double halfBeat=(beats*2)%1;
            int step=((int)(beats*2))%16;
            int melody=chord[0]+12+MELODY[step];
            float pluck=(float)(Math.exp(-halfBeat*6)*Math.min(1,halfBeat*65));
            float lead=(osc(time*frequency[Math.min(127,melody)])+
                osc(time*frequency[Math.min(127,melody)]*2)*.19f)*pluck*.075f;
            double inBeat=beats%1;
            float beatEnv=(float)Math.exp(-inBeat*7);
            float bass=osc(time*frequency[chord[0]-12])*.085f*(.7f+.3f*beatEnv);
            float kick=0;
            if(((int)beats)%4==0||((int)beats)%4==2) {
                float env=(float)Math.exp(-inBeat*24);
                kick=osc(inBeat*beatSeconds*49+(.12*(1-Math.exp(-inBeat*20))))*env*.095f;
            }
            float hiss=noise();
            float hat=(float)(Math.exp(-halfBeat*55))*.009f*hiss;
            float delayedL=delayL[dl],delayedR=delayR[dr];
            delayL[dl]=(padR+lead)*.24f+delayedL*.48f;
            delayR[dr]=(padL+lead)*.24f+delayedR*.48f;
            dl=(dl+1)%delayL.length;dr=(dr+1)%delayR.length;
            float effect=(engine.next()+hiss*impact)*cfg.effects;
            impact*=.9995f;
            float left=(padL+lead+bass+kick+hat+delayedL)*cfg.music+effect;
            float right=(padR+lead*.93f+bass+kick+hat+delayedR)*cfg.music+effect;
            out[i]=pcm(left*duck);out[i+1]=pcm(right*duck);
        }
    }
    private short pcm(float v) {v=v/(1+.4f*Math.abs(v));return (short)(Math.max(-.94f,Math.min(.94f,v))*32767);}
}
