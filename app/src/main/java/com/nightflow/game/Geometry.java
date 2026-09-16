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
        float curve(float[] values,float at){
            int i=Math.min(values.length-2,(int)at);float t=at-i;
            float a=values[Math.max(0,i-1)],b=values[i],c=values[i+1],d=values[Math.min(values.length-1,i+2)];
            return .5f*((2*b)+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t);
        }
        void skin(float[] zs,float[] widths,float[] bottoms,float[] tops,int color,int kind){
            int along=(zs.length-1)*5+1,around=32;
            float[][][] v=new float[along][around][3],n=new float[along][around][3];
            for(int i=0;i<along;i++){
                float at=i/5f,z=curve(zs,at),w=curve(widths,at),b=curve(bottoms,at),t=curve(tops,at);
                for(int j=0;j<around;j++){
                    double a=j*Math.PI*2/around;float co=(float)Math.cos(a),si=(float)Math.sin(a);
                    float xx=(float)Math.copySign(Math.pow(Math.abs(co),.46),co);
                    float yy=(float)Math.copySign(Math.pow(Math.abs(si),.58),si);
                    v[i][j]=p(w*xx,b+(t-b)*(.5f+.5f*yy),z);
                }
            }
            for(int i=0;i<along;i++)for(int j=0;j<around;j++){
                float[] a=v[i][(j+around-1)%around],b=v[i][(j+1)%around];
                float[] c=v[Math.max(0,i-1)][j],d=v[Math.min(along-1,i+1)][j];
                float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=d[0]-c[0],vy=d[1]-c[1],vz=d[2]-c[2];
                float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
                float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);n[i][j]=p(nx/len,ny/len,nz/len);
            }
            for(int i=0;i<along-1;i++)for(int j=0;j<around;j++){
                int k=(j+1)%around;
                vertex(v[i][j],n[i][j],color,kind);vertex(v[i][k],n[i][k],color,kind);vertex(v[i+1][k],n[i+1][k],color,kind);
                vertex(v[i][j],n[i][j],color,kind);vertex(v[i+1][k],n[i+1][k],color,kind);vertex(v[i+1][j],n[i+1][j],color,kind);
            }
            for(int end:new int[]{0,along-1}){
                float[] center=p(0,(bottoms[end==0?0:bottoms.length-1]+tops[end==0?0:tops.length-1])/2,zs[end==0?0:zs.length-1]);
                for(int j=0;j<around;j++){
                    float[] normal=p(0,0,end==0?-1:1);vertex(center,normal,color,kind);
                    vertex(v[end][j],normal,color,kind);vertex(v[end][(j+1)%around],normal,color,kind);
                }
            }
        }
        void ellipsoid(float x,float y,float z,float rx,float ry,float rz,int color,int kind){
            int lon=14,lat=8;
            for(int i=0;i<lat;i++)for(int j=0;j<lon;j++){
                float[][] ps=new float[4][3],ns=new float[4][3];int[][] ij={{i,j},{i,j+1},{i+1,j+1},{i+1,j}};
                for(int k=0;k<4;k++){
                    double a=ij[k][0]*Math.PI/lat,b=ij[k][1]*Math.PI*2/lon;
                    float xx=(float)(Math.sin(a)*Math.cos(b)),yy=(float)Math.cos(a),zz=(float)(Math.sin(a)*Math.sin(b));
                    ps[k]=p(x+xx*rx,y+yy*ry,z+zz*rz);float len=(float)Math.sqrt(xx*xx/(rx*rx)+yy*yy/(ry*ry)+zz*zz/(rz*rz));
                    ns[k]=p(xx/rx/len,yy/ry/len,zz/rz/len);
                }
                for(int k:new int[]{0,1,2,0,2,3})vertex(ps[k],ns[k],color,kind);
            }
        }
        void beam(float[] a,float[] b,float radius,int color,int kind){
            float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
            float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(len<.001f)return;
            dx/=len;dy/=len;dz/=len;
            float ux=dy,uy=-dx,uz=0;if(Math.abs(ux)+Math.abs(uy)<.01f){ux=1;uy=0;}
            float ul=(float)Math.sqrt(ux*ux+uy*uy);ux/=ul;uy/=ul;
            float vx=dy*uz-dz*uy,vy=dz*ux-dx*uz,vz=dx*uy-dy*ux;
            for(int i=0;i<8;i++){
                double t=i*Math.PI/4,q=(i+1)*Math.PI/4;
                float[] n1=p((float)(ux*Math.cos(t)+vx*Math.sin(t)),(float)(uy*Math.cos(t)+vy*Math.sin(t)),(float)(uz*Math.cos(t)+vz*Math.sin(t)));
                float[] n2=p((float)(ux*Math.cos(q)+vx*Math.sin(q)),(float)(uy*Math.cos(q)+vy*Math.sin(q)),(float)(uz*Math.cos(q)+vz*Math.sin(q)));
                float[][] ps={p(a[0]+n1[0]*radius,a[1]+n1[1]*radius,a[2]+n1[2]*radius),p(b[0]+n1[0]*radius,b[1]+n1[1]*radius,b[2]+n1[2]*radius),p(b[0]+n2[0]*radius,b[1]+n2[1]*radius,b[2]+n2[2]*radius),p(a[0]+n2[0]*radius,a[1]+n2[1]*radius,a[2]+n2[2]*radius)};
                for(int k:new int[]{0,1,2,0,2,3})vertex(ps[k],k<2?n1:n2,color,kind);
            }
        }
        Mesh build() { return new Mesh(Arrays.copyOf(data,at)); }
    }
    static float[] p(float x,float y,float z) { return new float[]{x,y,z}; }

    public static Mesh car(int style){return car(style,0);}
    public static Mesh car(int style,int kit){
        Builder b=new Builder();float stretch=style==1?1.09f:style==2?.93f:1;
        float roof=style==1?1.46f:style==2?1.40f:style==3?1.16f:1.29f;
        float wide=kit==2?.105f:kit==1?.025f:0;
        float[] z={-2.28f*stretch,-2.10f*stretch,-1.48f*stretch,-.55f,.55f,1.42f*stretch,2.06f*stretch,2.22f*stretch};
        b.skin(z,new float[]{.72f,.88f+wide,.95f+wide,.92f,.93f,.99f+wide,.95f+wide,.79f},
            new float[]{.30f,.24f,.26f,.25f,.25f,.26f,.28f,.35f},
            new float[]{.61f,.70f,.84f,.86f,.89f,.83f,.77f,.66f},0xffffff,2);
        b.box(0,.255f,0,1.70f,.13f,3.9f*stretch,0x111820,9);
        b.skin(new float[]{-1.04f,-.48f,.35f,.77f,1.48f},new float[]{.75f,.65f,.65f,.65f,.78f},
            new float[]{.82f,.84f,.85f,.85f,.81f},new float[]{.85f,roof,roof+.045f,roof,.83f},0x132c40,6);
        b.skin(new float[]{-.51f,-.32f,.35f,.75f},new float[]{.50f,.61f,.61f,.48f},
            new float[]{roof-.01f,roof+.005f,roof+.014f,roof-.01f},new float[]{roof+.021f,roof+.055f,roof+.065f,roof+.02f},0xffffff,2);
        // Continuous pillars, window seals, door shut-lines and mirrors.
        for(int side:new int[]{-1,1}){
            b.beam(p(side*.78f,.84f,-1.05f),p(side*.66f,roof,-.48f),.032f,0xffffff,2);
            b.beam(p(side*.67f,roof,.76f),p(side*.79f,.84f,1.49f),.055f,0xffffff,2);
            b.beam(p(side*.68f,roof+.005f,.35f),p(side*.83f,.84f,.35f),.035f,0x09131b,0);
            b.beam(p(side*.80f,.849f,-.98f),p(side*.84f,.862f,1.12f),.014f,0x71858e,7);
            b.beam(p(side*.934f,.77f,.4f),p(side*.935f,.4f,.43f),.006f,0x132129,0);
            b.ellipsoid(side*1.0f,.88f,-.67f,.18f,.065f,.17f,0xffffff,2);
            b.box(side*.945f,.72f,.56f,.024f,.023f,.19f,0x718595,7);
            b.box(side*.942f,.38f,.3f,.023f,.018f,1.50f,0x132129,9);
            b.box(side*.61f,.62f,z[0]-.008f,.46f,.05f,.025f,0xd4eeff,3);
            b.box(side*.55f,.56f,z[0]-.015f,.35f,.035f,.024f,0x243549,0);
            b.box(side*.68f,.40f,z[0]+.02f,.29f,.12f,.045f,0x091016,9);
            b.box(side*.60f,.64f,z[z.length-1]+.008f,.48f,.044f,.028f,0xf03930,10);
            b.box(side*.56f,.32f,z[z.length-1]+.05f,.24f,.085f,.13f,0xa5afb5,7);
            b.box(side*.56f,.32f,z[z.length-1]+.12f,.17f,.051f,.015f,0x060c13,0);
            b.beam(p(side*.55f,.76f,-1.99f),p(side*.59f,.855f,-.95f),.009f,0xffffff,2);
            if(kit>0){
                b.box(side*(.94f+wide),.24f,.08f,.12f,.065f,3.2f,0x161b25,9);
                b.box(side*.57f,1.0f,1.85f*stretch,.055f,.39f,.08f,0x1f2931,9);
            }
            if(kit==2)for(float zz:new float[]{-1.40f*stretch,1.40f*stretch}){
                for(int i=0;i<14;i++){
                    double a=Math.PI*i/14,aa=Math.PI*(i+1)/14;
                    b.beam(p(side*1.02f,.37f+(float)Math.sin(a)*.42f,zz+(float)Math.cos(a)*.42f),
                        p(side*1.02f,.37f+(float)Math.sin(aa)*.42f,zz+(float)Math.cos(aa)*.42f),.052f,0xffffff,2);
                }
            }
        }
        b.box(0,.45f,z[0]-.02f,.79f,.13f,.035f,0x080f16,9);
        for(int i=0;i<7;i++)b.box(-.32f+i*.105f,.45f,z[0]-.041f,.012f,.095f,.016f,0x71818d,7);
        b.box(0,.525f,z[z.length-1]+.023f,.43f,.11f,.018f,0xbdc5c9,0);
        b.box(0,.545f,z[z.length-1]+.037f,.18f,.028f,.016f,0x122332,0);
        b.box(0,.24f,z[z.length-1]-.10f,1.65f,.085f,.4f,0x101b26,9);
        if(style==3)b.box(0,.642f,z[z.length-1]+.024f,.76f,.029f,.02f,0xe83c35,10);
        if(kit>0){
            b.skin(new float[]{1.65f*stretch,1.78f*stretch,1.94f*stretch},new float[]{1.05f,1.08f,1.04f},new float[]{1.17f,1.17f,1.14f},new float[]{1.21f,1.225f,1.20f},0x17212d,9);
            b.box(0,.23f,z[0]+.06f,2.00f+wide,.06f,.42f,0x14212d,9);
            if(kit==2){b.box(-1.06f,1.18f,1.8f*stretch,.045f,.18f,.42f,0x101e26,9);b.box(1.06f,1.18f,1.8f*stretch,.045f,.18f,.42f,0x101e26,9);}
        }
        return b.build();
    }
    public static Mesh wheel(){
        Builder b=new Builder();int around=32,tube=10;
        float[][][] pos=new float[around][tube][3],norm=new float[around][tube][3];
        for(int i=0;i<around;i++)for(int j=0;j<tube;j++){
            double a=i*Math.PI*2/around,t=j*Math.PI*2/tube;
            float x=(float)Math.sin(t)*.125f,r=.285f+(float)Math.cos(t)*.081f;
            pos[i][j]=p(x,(float)Math.sin(a)*r,(float)Math.cos(a)*r);
            norm[i][j]=p((float)Math.sin(t),(float)(Math.cos(t)*Math.sin(a)),(float)(Math.cos(t)*Math.cos(a)));
        }
        for(int i=0;i<around;i++)for(int j=0;j<tube;j++){
            int ii=(i+1)%around,jj=(j+1)%tube;int[][] ids={{i,j},{ii,j},{ii,jj},{i,j},{ii,jj},{i,jj}};
            for(int[] id:ids)b.vertex(pos[id[0]][id[1]],norm[id[0]][id[1]],0x171b20,11);
        }
        for(int side:new int[]{-1,1}){
            b.cylinderX(side*.107f,0,0,.224f,.012f,0x25313b,32);
            b.cylinderX(side*.132f,0,0,.048f,.01f,0x9babb6,20);
            for(int i=0;i<10;i++){
                double a=i*Math.PI*2/10;
                b.beam(p(side*.131f,(float)Math.sin(a)*.047f,(float)Math.cos(a)*.047f),
                    p(side*.122f,(float)Math.sin(a+.09)*.226f,(float)Math.cos(a+.09)*.226f),.013f,0xc8d0d6,12);
            }
            for(int i=0;i<32;i++){
                double a=i*Math.PI*2/32,aa=(i+1)*Math.PI*2/32;
                b.beam(p(side*.125f,(float)Math.sin(a)*.234f,(float)Math.cos(a)*.234f),
                    p(side*.125f,(float)Math.sin(aa)*.234f,(float)Math.cos(aa)*.234f),.014f,0xd5dee2,12);
            }
        }
        return b.build();
    }
    public static Mesh showroom(){
        Builder b=new Builder();b.quad(p(-100,-.018f,100),p(100,-.018f,100),p(100,-.018f,-100),p(-100,-.018f,-100),0x26303e,8);
        for(int i=0;i<96;i++){
            double a=i*Math.PI*2/96,q=(i+1)*Math.PI*2/96;
            b.quad(p((float)Math.cos(a)*3.25f,.005f,(float)Math.sin(a)*3.25f),p((float)Math.cos(q)*3.25f,.005f,(float)Math.sin(q)*3.25f),
                p((float)Math.cos(q)*3.28f,.005f,(float)Math.sin(q)*3.28f),p((float)Math.cos(a)*3.28f,.005f,(float)Math.sin(a)*3.28f),0x769dab,3);
        }
        return b.build();
    }
    public static Mesh gate(){
        Builder b=new Builder();b.box(-2.05f,1.45f,0,.06f,2.9f,.06f,0x65dabc,3);b.box(2.05f,1.45f,0,.06f,2.9f,.06f,0x65dabc,3);
        b.box(0,2.92f,0,4.16f,.06f,.06f,0x65dabc,3);return b.build();
    }
    public static Mesh road() {
        Builder b=new Builder();
        b.quad(p(-7,0,24),p(7,0,24),p(7,0,-610),p(-7,0,-610),0x33383e,1);
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
            int count=detailed?38:24;
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
                b.ellipsoid(x,4.4f,z,1.45f,2.15f,1.55f,0x385744,0);
                if(detailed){b.ellipsoid(x-.85f,3.6f,z+.4f,1.0f,1.25f,1.2f,0x314c3b,0);b.ellipsoid(x+.75f,4.1f,z-.7f,1.1f,1.4f,1.1f,0x3f5e46,0);}
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
