package com.nightflow.game;

import android.opengl.GLES30;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Random;

/** Original procedural meshes: position, normal, colour, material (10 floats). */
public final class Geometry {
    public static final class Mesh {
        private final int buffer,count;
        Mesh(float[] data) {
            count=data.length/10;
            FloatBuffer b=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            b.put(data).position(0);
            int[] ids=new int[1]; GLES30.glGenBuffers(1,ids,0); buffer=ids[0];
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,buffer);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,data.length*4,b,GLES30.GL_STATIC_DRAW);
        }
        public void draw() {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,buffer);
            GLES30.glEnableVertexAttribArray(0); GLES30.glEnableVertexAttribArray(1);
            GLES30.glEnableVertexAttribArray(2); GLES30.glEnableVertexAttribArray(3);
            GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,40,0);
            GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,40,12);
            GLES30.glVertexAttribPointer(2,3,GLES30.GL_FLOAT,false,40,24);
            GLES30.glVertexAttribPointer(3,1,GLES30.GL_FLOAT,false,40,36);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,count);
        }
    }
    static final class Builder {
        float[] data=new float[65536]; int at;
        void vertex(float[] p,float[] n,int color,float kind) {
            if(at+10>data.length) data=Arrays.copyOf(data,data.length*2);
            data[at++]=p[0];data[at++]=p[1];data[at++]=p[2];
            data[at++]=n[0];data[at++]=n[1];data[at++]=n[2];
            data[at++]=((color>>16)&255)/255f;data[at++]=((color>>8)&255)/255f;data[at++]=(color&255)/255f;
            data[at++]=kind;
        }
        void tri(float[] a,float[] b,float[] c,int color,float kind) {
            float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
            float vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            float l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            float[] n={nx/Math.max(l,0.00001f),ny/Math.max(l,0.00001f),nz/Math.max(l,0.00001f)};
            vertex(a,n,color,kind);vertex(b,n,color,kind);vertex(c,n,color,kind);
        }
        void quad(float[] a,float[] b,float[] c,float[] d,int col,float kind) { tri(a,b,c,col,kind);tri(a,c,d,col,kind); }
        void box(float x,float y,float z,float sx,float sy,float sz,int c,float k) {
            float l=x-sx/2,r=x+sx/2,b=y-sy/2,t=y+sy/2,n=z-sz/2,f=z+sz/2;
            quad(p(l,b,f),p(r,b,f),p(r,t,f),p(l,t,f),c,k);
            quad(p(r,b,n),p(l,b,n),p(l,t,n),p(r,t,n),c,k);
            quad(p(l,b,n),p(l,b,f),p(l,t,f),p(l,t,n),c,k);
            quad(p(r,b,f),p(r,b,n),p(r,t,n),p(r,t,f),c,k);
            quad(p(l,t,f),p(r,t,f),p(r,t,n),p(l,t,n),c,k);
            quad(p(l,b,n),p(r,b,n),p(r,b,f),p(l,b,f),c,k);
        }
        void cylinderX(float x,float y,float z,float radius,float width,int color,int seg) {
            for(int i=0;i<seg;i++) {
                double a=2*Math.PI*i/seg,b=2*Math.PI*(i+1)/seg;
                float ay=y+(float)Math.sin(a)*radius,az=z+(float)Math.cos(a)*radius;
                float by=y+(float)Math.sin(b)*radius,bz=z+(float)Math.cos(b)*radius;
                quad(p(x-width/2,ay,az),p(x+width/2,ay,az),p(x+width/2,by,bz),p(x-width/2,by,bz),color,0);
                tri(p(x-width/2,y,z),p(x-width/2,ay,az),p(x-width/2,by,bz),color,0);
                tri(p(x+width/2,y,z),p(x+width/2,by,bz),p(x+width/2,ay,az),color,0);
            }
        }
        void loft(float[] z,float[] w,float[] bottom,float[] top,int color,float kind) {
            float[][][] rings=new float[z.length][8][3];
            for(int i=0;i<z.length;i++) {
                float b=bottom[i],t=top[i],h=t-b;
                rings[i]=new float[][]{p(-w[i]*.77f,b,z[i]),p(w[i]*.77f,b,z[i]),
                    p(w[i],b+h*.25f,z[i]),p(w[i],t-h*.18f,z[i]),p(w[i]*.78f,t,z[i]),
                    p(-w[i]*.78f,t,z[i]),p(-w[i],t-h*.18f,z[i]),p(-w[i],b+h*.25f,z[i])};
            }
            for(int i=0;i<z.length-1;i++) for(int j=0;j<8;j++)
                quad(rings[i][j],rings[i][(j+1)%8],rings[i+1][(j+1)%8],rings[i+1][j],color,kind);
            for(int j=1;j<7;j++) {
                tri(rings[0][0],rings[0][j+1],rings[0][j],color,kind);
                int last=z.length-1;
                tri(rings[last][0],rings[last][j],rings[last][j+1],color,kind);
            }
        }
        Mesh build() { return new Mesh(Arrays.copyOf(data,at)); }
    }
    static float[] p(float x,float y,float z) { return new float[]{x,y,z}; }

    public static Mesh car(int style) {
        Builder b=new Builder();
        float stretch=style==1?1.08f:1;
        float roof=style==1?1.52f:style==2?1.26f:1.36f;
        float[] zs={-2.16f*stretch,-1.85f*stretch,-.68f,.78f,1.85f*stretch,2.12f*stretch};
        b.loft(zs,new float[]{.66f,.87f,.96f,.96f,.89f,.76f},
            new float[]{.36f,.28f,.26f,.26f,.30f,.39f},
            new float[]{.57f,.73f,.84f,.85f,.76f,.63f},0xffffff,2);
        b.box(0,.28f,0,1.6f,.16f,3.8f,0x111923,0);
        b.loft(new float[]{-1.04f,-.45f,.57f,1.22f},
            new float[]{.75f,.67f,.67f,.76f},
            new float[]{.8f,.82f,.82f,.81f},
            new float[]{.83f,roof,roof+.03f,.84f},0x143249,6);
        b.box(0,roof+.015f,.05f,1.05f,.055f,.98f,0xffffff,2);
        for(int s:new int[]{-1,1}) {
            b.box(s*.8f,.77f,.25f,.04f,.07f,1.92f,0x819daa,0);
            b.box(s*.69f,1.12f,.24f,.065f,.51f,.075f,0x162637,0);
            b.box(s*.98f,.91f,-.67f,.22f,.13f,.29f,0xffffff,2);
            b.box(s*.946f,.69f,.47f,.025f,.045f,.19f,0xc4d0d8,0);
            for(float z:new float[]{-1.35f*stretch,1.32f*stretch}) {
                b.cylinderX(s*.87f,.365f,z,.365f,.265f,0x10151c,24);
                b.cylinderX(s*1.013f,.365f,z,.241f,.015f,0x52677b,24);
                b.cylinderX(s*1.024f,.365f,z,.176f,.02f,0x15212d,20);
                b.cylinderX(s*1.04f,.365f,z,.075f,.025f,0xd2dce3,16);
                for(int i=0;i<5;i++) {
                    double a=i*Math.PI*2/5;
                    float y=.365f+(float)Math.cos(a)*.13f,zz=z+(float)Math.sin(a)*.13f;
                    b.box(s*1.038f,y,zz,.02f,.105f,.052f,0xb0bac8,0);
                }
            }
            b.box(s*.57f,.59f,zs[0]-.015f,.35f,.065f,.05f,0xc8f8ff,3);
            b.box(s*.52f,.58f,zs[zs.length-1]+.015f,.43f,.065f,.045f,0xff294d,3);
            b.box(s*.54f,.335f,zs[zs.length-1]+.02f,.18f,.12f,.08f,0x85949f,0);
        }
        b.box(0,.46f,zs[0]-.02f,.7f,.13f,.035f,0x0c151e,0);
        b.box(0,.60f,zs[zs.length-1]+.04f,.45f,.08f,.032f,0xdbe8ed,0);
        b.box(0,.53f,zs[zs.length-1]+.05f,.25f,.055f,.02f,0x173249,0);
        if(style==2) {
            b.box(-.55f,.97f,1.74f,.08f,.28f,.09f,0x152230,0);
            b.box(.55f,.97f,1.74f,.08f,.28f,.09f,0x152230,0);
            b.box(0,1.12f,1.74f,1.8f,.06f,.28f,0x1b2d3b,0);
            b.box(0,.86f,-1.35f,.33f,.015f,.56f,0x17303c,0);
        }
        return b.build();
    }
    public static Mesh road() {
        Builder b=new Builder();
        b.quad(p(-7,0,24),p(7,0,24),p(7,0,-610),p(-7,0,-610),0x273245,1);
        return b.build();
    }
    public static Mesh ground() {
        Builder b=new Builder();
        b.box(0,-.17f,-275,900,.2f,750,0x162a30,0);
        b.box(-7.6f,.04f,-270,1.1f,.09f,620,0x566272,0);
        b.box(7.6f,.04f,-270,1.1f,.09f,620,0x566272,0);
        return b.build();
    }
    public static Mesh shadow() {
        Builder b=new Builder();
        b.quad(p(-1,.016f,1),p(1,.016f,1),p(1,.016f,-1),p(-1,.016f,-1),0x07111b,5);
        return b.build();
    }
    public static Mesh city(boolean detailed) {
        Builder b=new Builder(); Random r=new Random(78);
        for(int i=0;i<16;i++) {
            float z=-i*20-10;
            for(int s:new int[]{-1,1}) {
                b.box(s*8.1f,3.1f,z,.12f,6.2f,.12f,0x3d4f62,0);
                b.box(s*7.05f,6.16f,z,2.2f,.1f,.12f,0x52687a,0);
                b.box(s*6.12f,6.10f,z,.85f,.075f,.28f,0xffddb5,3);
                b.box(s*8.05f,.62f,z+7,.08f,.55f,.1f,0x71ebd8,3);
            }
        }
        for(int s:new int[]{-1,1}) {
            b.box(s*8.55f,.64f,-160,.12f,.12f,320,0x516678,0);
            for(int i=0;i<64;i++) b.box(s*8.55f,.32f,-i*5,.1f,.64f,.1f,0x3a4d5b,0);
            int count=detailed?48:25;
            for(int i=0;i<count;i++) {
                float z=-r.nextFloat()*320, x=s*(16+r.nextFloat()*(detailed?65:37));
                float w=4+r.nextFloat()*9,h=7+r.nextFloat()*48,d=5+r.nextFloat()*9;
                int tint=i%3==0?0x34445d:i%3==1?0x29394b:0x44475d;
                b.box(x,h/2,z,w,h,d,tint,4);
                if(i%4==0) {
                    b.box(x,h+.5f,z,w*.78f,1,d*.78f,0x34475c,0);
                    b.box(x,h+1.1f,z,w*.8f,.08f,d*.8f,0x5bc2c6,3);
                }
            }
            for(int i=0;i<20;i++) {
                float z=-i*16+3,x=s*(10.6f+r.nextFloat()*2);
                b.box(x,1.5f,z,.22f,3,.22f,0x2a3543,0);
                float[] top=p(x,5.6f,z);
                float[] a=p(x-1.3f,2,z-1.3f),bb=p(x+1.3f,2,z-1.3f);
                float[] c=p(x+1.3f,2,z+1.3f),d=p(x-1.3f,2,z+1.3f);
                b.tri(a,top,bb,0x254650,0);b.tri(bb,top,c,0x305359,0);
                b.tri(c,top,d,0x254650,0);b.tri(d,top,a,0x1f3e47,0);
            }
        }
        // A gantry recurs once per tile; the route remains open beneath it.
        b.box(-8.9f,4.5f,-112,.28f,9,.28f,0x43546d,0);
        b.box(8.9f,4.5f,-112,.28f,9,.28f,0x43546d,0);
        b.box(0,8.8f,-112,18,.25f,.28f,0x43546d,0);
        b.box(-2.7f,7.65f,-112,5.4f,1.65f,.15f,0x173e4d,0);
        b.box(-2.7f,7.25f,-111.90f,4.1f,.08f,.025f,0x67eddb,3);
        b.box(3.6f,7.65f,-112,4,1.65f,.15f,0x223d59,0);
        for(int i=0;i<3;i++) b.box(2.5f+i*.95f,7.65f,-111.90f,.55f,.07f,.025f,0xb6dce1,3);
        return b.build();
    }
    public static Mesh mountains() {
        Builder b=new Builder();Random r=new Random(90);
        for(int i=0;i<24;i++) {
            float x=-430+i*38,h=25+r.nextFloat()*65;
            b.tri(p(x-55,-2,-540),p(x+55,-2,-540),p(x,h,-520),0x38425d,0);
            b.tri(p(x,h,-520),p(x+55,-2,-540),p(x+70,-2,-590),0x29394d,0);
        }
        return b.build();
    }
}
