package com.nightflow.game;

import java.io.*;
import java.util.Arrays;

/** Immutable replay, indexed by elapsed race time. No networking or opponents' personal data. */
public final class GhostRun {
    public final float duration;
    private final float[] samples; // time, lateral position, distance
    public GhostRun(float duration,float[] samples) {
        if(!Float.isFinite(duration)||duration<=0||samples.length<6||samples.length%3!=0||samples.length>36000)
            throw new IllegalArgumentException("Invalid replay size/duration");
        float last=-1,dist=-1;
        for(int i=0;i<samples.length;i+=3){
            float t=samples[i],x=samples[i+1],z=samples[i+2];
            if(!Float.isFinite(t)||!Float.isFinite(x)||!Float.isFinite(z)||t<=last||z<dist||Math.abs(x)>7||t>duration+.1f)
                throw new IllegalArgumentException("Invalid replay sample");
            last=t;dist=z;
        }
        this.duration=duration;this.samples=samples.clone();
    }
    public void at(float time,float[] out) {
        int lo=0,hi=samples.length/3-1;
        if(time<=samples[0]){out[0]=samples[1];out[1]=samples[2];return;}
        if(time>=samples[hi*3]){out[0]=samples[hi*3+1];out[1]=samples[hi*3+2];return;}
        while(lo+1<hi){int m=(lo+hi)/2;if(samples[m*3]<=time)lo=m;else hi=m;}
        float f=(time-samples[lo*3])/(samples[hi*3]-samples[lo*3]);
        out[0]=samples[lo*3+1]+(samples[hi*3+1]-samples[lo*3+1])*f;
        out[1]=samples[lo*3+2]+(samples[hi*3+2]-samples[lo*3+2])*f;
    }
    public void write(OutputStream stream)throws IOException {
        DataOutputStream d=new DataOutputStream(stream);d.writeInt(0x4e463032);d.writeFloat(duration);d.writeInt(samples.length);
        for(float f:samples)d.writeFloat(f);d.flush();
    }
    public static GhostRun read(InputStream stream)throws IOException {
        DataInputStream d=new DataInputStream(stream);
        if(d.readInt()!=0x4e463032)throw new IOException("Replay version");
        float duration=d.readFloat();int n=d.readInt();if(n<6||n>36000||n%3!=0)throw new IOException("Replay size");
        float[] s=new float[n];for(int i=0;i<n;i++)s[i]=d.readFloat();
        try{return new GhostRun(duration,s);}catch(IllegalArgumentException e){throw new IOException(e);}
    }
    public static final class Recorder {
        private final float[] values=new float[36000];private int count;
        public void add(float time,float x,float distance,boolean force) {
            if(count>=values.length||count>0&&(time<=values[count-3]||!force&&time-values[count-3]<.1f))return;
            values[count++]=time;values[count++]=x;values[count++]=distance;
        }
        public GhostRun finish(float duration){return count<6?null:new GhostRun(duration,Arrays.copyOf(values,count));}
    }
}
