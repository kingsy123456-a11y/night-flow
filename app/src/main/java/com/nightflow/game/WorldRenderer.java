package com.nightflow.game;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class WorldRenderer implements GLSurfaceView.Renderer {
    public final GameState state=new GameState();
    public volatile Settings settings;
    public volatile float tilt=0,touch=0,actualFps=0;
    public volatile boolean brake=false,gas=false;
    public volatile String failure=null;
    public volatile int framesRendered=0,lastGlError=0;
    private final MainActivity owner;
    private Geometry.Mesh road,ground,cityHigh,cityLow,mountains,shadow;
    private final Geometry.Mesh[] cars=new Geometry.Mesh[3];
    private int scene,sky,post,width=1,height=1,framebuffer=0,texture=0,depth=0,quality=-1;
    private int uVP,uModel,uCamera,uPaint,uTravel,uNight,uPlayer;
    private final float[] projection=new float[16],view=new float[16],vp=new float[16],model=new float[16];
    private float cameraX=-1.7f;
    private long lastTime=0,fpsStart=0;
    private int fpsFrames=0,notifiedCrash=0;

    public WorldRenderer(MainActivity owner,Settings settings) { this.owner=owner;this.settings=settings; }
    @Override public void onSurfaceCreated(GL10 unused,EGLConfig config) {
        try {
            framebuffer=texture=depth=0;quality=-1;lastTime=0;
            scene=program(SCENE_VERTEX,SCENE_FRAGMENT);
            sky=program(FULL_VERTEX,SKY_FRAGMENT);post=program(FULL_VERTEX,POST_FRAGMENT);
            int[] vao=new int[1];GLES30.glGenVertexArrays(1,vao,0);GLES30.glBindVertexArray(vao[0]);
            uVP=loc(scene,"uVP");uModel=loc(scene,"uModel");uCamera=loc(scene,"uCamera");
            uPaint=loc(scene,"uPaint");uTravel=loc(scene,"uTravel");uNight=loc(scene,"uNight");uPlayer=loc(scene,"uPlayer");
            road=Geometry.road();ground=Geometry.ground();shadow=Geometry.shadow();
            cityHigh=Geometry.city(true);cityLow=Geometry.city(false);mountains=Geometry.mountains();
            for(int i=0;i<3;i++) cars[i]=Geometry.car(i);
            GLES30.glDisable(GLES30.GL_CULL_FACE);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        } catch(RuntimeException e) { fail(e); }
    }
    @Override public void onSurfaceChanged(GL10 unused,int w,int h) {
        width=w;height=h;quality=-1;lastTime=0;
    }
    @Override public void onDrawFrame(GL10 unused) {
        if(failure!=null) return;
        try { render(); } catch(RuntimeException e) { fail(e); }
    }
    private void render() {
        Settings s=settings;
        if(quality!=s.quality||framebuffer==0) allocate(s);
        long now=System.nanoTime();
        float dt=lastTime==0?1f/60:(now-lastTime)/1e9f;lastTime=now;
        state.tick(dt,s.control==0?tilt:touch,brake,gas);
        if(state.crashSerial!=notifiedCrash) { notifiedCrash=state.crashSerial;owner.onCollision(); }
        int rw=Math.max(1,Math.round(width*s.scale())),rh=Math.max(1,Math.round(height*s.scale()));
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer);
        GLES30.glViewport(0,0,rw,rh);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT|GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glUseProgram(sky);
        GLES30.glUniform1f(loc(sky,"uNight"),s.night?1:0);
        GLES30.glUniform1f(loc(sky,"uAspect"),(float)width/height);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        cameraX+=(state.x-cameraX)*(1f-(float)Math.exp(-Math.min(dt,.05f)*4));
        boolean menu=state.phase==GameState.MENU;
        float cx=menu?state.x+4.8f:cameraX*.72f+state.x*.28f;
        float cy=menu?2.6f:s.camera==2?1.28f:s.camera==1?5.25f:3.45f;
        float cz=menu?6.7f:s.camera==2?-.75f:s.camera==1?10.5f:7.6f;
        float lookX=menu?state.x-1.5f:cameraX*.55f;
        Matrix.setLookAtM(view,0,cx,cy,cz,lookX,menu?.78f:.95f,menu?-2.3f:-28,0,1,0);
        Matrix.perspectiveM(projection,0,s.fov+(gas?3:0),(float)width/height,.15f,640);
        Matrix.multiplyMM(vp,0,projection,0,view,0);
        GLES30.glUseProgram(scene);
        GLES30.glUniformMatrix4fv(uVP,1,false,vp,0);
        GLES30.glUniform3f(uCamera,cx,cy,cz);
        GLES30.glUniform1f(uTravel,state.travel);
        GLES30.glUniform1f(uNight,s.night?1:0);
        GLES30.glUniform1f(uPlayer,state.x);
        draw(ground,0,0,0,0,0xffffff); draw(road,0,0,0,0,0xffffff);
        draw(mountains,0,0,0,0,0xffffff);
        float offset=state.travel%320;
        Geometry.Mesh city=s.quality>=2?cityHigh:cityLow;
        draw(city,0,0,offset,0,0xffffff);
        draw(city,0,0,offset-320,0,0xffffff);
        if(s.shadows) {
            GLES30.glEnable(GLES30.GL_BLEND);GLES30.glDepthMask(false);
            if(s.camera!=2||menu) drawShadow(state.x,0,1.3f,2.75f);
            for(GameState.Car c:state.traffic) if(c.active&&c.z> -190) drawShadow(c.x,c.z,1.3f,c.length*.63f);
            GLES30.glDepthMask(true);GLES30.glDisable(GLES30.GL_BLEND);
        }
        for(GameState.Car c:state.traffic) if(c.active&&c.z> -225)
            draw(cars[c.shape],c.x,0,c.z,0,Settings.PAINTS[c.paint]);
        if(s.camera!=2||menu) {
            boolean visible=state.invincible<=0||((int)(state.animation*10)%2==0);
            if(visible) draw(cars[s.car],state.x,.013f*(float)Math.sin(state.animation*9),0,-state.steer*7,Settings.PAINTS[s.paint]);
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
        GLES30.glViewport(0,0,width,height);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glUseProgram(post);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        GLES30.glUniform1i(loc(post,"uScene"),0);
        GLES30.glUniform2f(loc(post,"uPixel"),1f/rw,1f/rh);
        GLES30.glUniform1f(loc(post,"uBloom"),s.bloom&&s.quality>0?1:0);
        GLES30.glUniform1f(loc(post,"uFlash"),state.flash);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);
        framesRendered++;fpsFrames++;
        if(fpsStart==0) fpsStart=now;
        if(now-fpsStart>1_000_000_000L) {
            actualFps=fpsFrames*1e9f/(now-fpsStart);fpsStart=now;fpsFrames=0;
            int error=GLES30.glGetError();if(error!=GLES30.GL_NO_ERROR) lastGlError=error;
        }
    }
    private void draw(Geometry.Mesh mesh,float x,float y,float z,float yaw,int paint) {
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);
        if(yaw!=0) Matrix.rotateM(model,0,yaw,0,1,0);
        GLES30.glUniformMatrix4fv(uModel,1,false,model,0);
        GLES30.glUniform3f(uPaint,((paint>>16)&255)/255f,((paint>>8)&255)/255f,(paint&255)/255f);
        mesh.draw();
    }
    private void drawShadow(float x,float z,float w,float length) {
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,0,z);Matrix.scaleM(model,0,w,1,length);
        GLES30.glUniformMatrix4fv(uModel,1,false,model,0);shadow.draw();
    }
    private void allocate(Settings s) {
        if(framebuffer!=0) {
            GLES30.glDeleteFramebuffers(1,new int[]{framebuffer},0);
            GLES30.glDeleteTextures(1,new int[]{texture},0);
            GLES30.glDeleteRenderbuffers(1,new int[]{depth},0);
        }
        int w=Math.max(1,Math.round(width*s.scale())),h=Math.max(1,Math.round(height*s.scale()));
        int[] id=new int[1];GLES30.glGenTextures(1,id,0);texture=id[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGBA,w,h,0,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,null);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glGenRenderbuffers(1,id,0);depth=id[0];
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER,depth);
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER,GLES30.GL_DEPTH_COMPONENT16,w,h);
        GLES30.glGenFramebuffers(1,id,0);framebuffer=id[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_COLOR_ATTACHMENT0,GLES30.GL_TEXTURE_2D,texture,0);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,GLES30.GL_DEPTH_ATTACHMENT,GLES30.GL_RENDERBUFFER,depth);
        int status=GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
        if(status!=GLES30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Framebuffer "+status);
        quality=s.quality;
    }
    private void fail(RuntimeException e) {
        failure=e.toString();Log.e("NightFlow","Rendering failed",e);
        owner.runOnUiThread(()->owner.showGraphicsError(failure));
    }
    private static int loc(int p,String name) { return GLES30.glGetUniformLocation(p,name); }
    private static int shader(int kind,String source) {
        int s=GLES30.glCreateShader(kind);GLES30.glShaderSource(s,source);GLES30.glCompileShader(s);
        int[] ok=new int[1];GLES30.glGetShaderiv(s,GLES30.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) throw new IllegalStateException(GLES30.glGetShaderInfoLog(s));
        return s;
    }
    private static int program(String v,String f) {
        int a=shader(GLES30.GL_VERTEX_SHADER,v),b=shader(GLES30.GL_FRAGMENT_SHADER,f);
        int p=GLES30.glCreateProgram();GLES30.glAttachShader(p,a);GLES30.glAttachShader(p,b);GLES30.glLinkProgram(p);
        int[] ok=new int[1];GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,ok,0);
        GLES30.glDeleteShader(a);GLES30.glDeleteShader(b);
        if(ok[0]==0) throw new IllegalStateException(GLES30.glGetProgramInfoLog(p));
        return p;
    }
    private static final String FULL_VERTEX="""
        #version 300 es
        precision highp float;
        out vec2 uv;
        void main(){
          vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));
          uv=p; gl_Position=vec4(p*2.0-1.0,0.0,1.0);
        }
        """;
    private static final String SKY_FRAGMENT="""
        #version 300 es
        precision highp float;
        in vec2 uv;
        uniform float uNight,uAspect;
        out vec4 outColor;
        float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
        void main(){
          vec3 low=mix(vec3(.62,.36,.42),vec3(.09,.17,.25),uNight);
          vec3 high=mix(vec3(.055,.12,.23),vec3(.012,.028,.072),uNight);
          vec3 col=mix(low,high,smoothstep(.12,1.0,uv.y));
          vec2 sun=(uv-vec2(.70,.34))*vec2(uAspect,1.0);
          float d=length(sun);
          col+=vec3(.53,.18,.12)*exp(-d*7.0)*(1.0-uNight);
          col=mix(col,mix(vec3(1.0,.68,.48),vec3(.75,.86,.95),uNight),1.0-smoothstep(.052,.057,d));
          vec2 grid=uv*vec2(350.0*uAspect,350.0);
          float star=step(.997,hash(floor(grid)))*pow(max(0.0,1.0-length(fract(grid)-.5)*2.0),5.0);
          col+=star*vec3(.8,.88,1.0)*smoothstep(.38,.8,uv.y)*(.25+.75*uNight);
          outColor=vec4(col,1.0);
        }
        """;
    private static final String SCENE_VERTEX="""
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec3 aNormal;
        layout(location=2) in vec3 aColor;
        layout(location=3) in float aKind;
        uniform mat4 uVP,uModel;
        out vec3 vWorld,vNormal,vColor,vLocal;
        flat out float vKind;
        void main(){
          vec4 w=uModel*vec4(aPos,1.0);vWorld=w.xyz;vLocal=aPos;
          vNormal=normalize(mat3(uModel)*aNormal);vColor=aColor;vKind=aKind;
          gl_Position=uVP*w;
        }
        """;
    private static final String SCENE_FRAGMENT="""
        #version 300 es
        precision highp float;
        in vec3 vWorld,vNormal,vColor,vLocal;
        flat in float vKind;
        uniform vec3 uCamera,uPaint;
        uniform float uTravel,uNight,uPlayer;
        out vec4 outColor;
        float hash(vec2 p){return fract(sin(dot(p,vec2(41.3,289.1)))*43758.5453);}
        void main(){
          vec3 n=normalize(vNormal),eye=normalize(uCamera-vWorld);
          vec3 light=normalize(vec3(-.5,.7,-.55));
          float diff=max(dot(n,light),0.0);
          float fres=pow(1.0-max(dot(n,eye),0.0),3.0);
          vec3 base=vKind>1.5&&vKind<2.5?uPaint:vColor;
          vec3 col=base*(mix(.43,.32,uNight)+diff*mix(.66,.35,uNight));
          col+=vec3(.13,.25,.33)*max(n.y,0.0)*.26;
          if(vKind>.5&&vKind<1.5){
            float z=vWorld.z-uTravel;
            float grain=hash(floor(vec2(vWorld.x,z)*70.0));
            col*=.8+grain*.24;
            float lane=min(abs(vWorld.x),min(abs(vWorld.x-3.4),abs(vWorld.x+3.4)));
            float dash=1.0-step(.38,fract(z/10.0));
            float line=(1.0-smoothstep(.045,.072,lane))*dash;
            float edge=1.0-smoothstep(.04,.075,abs(abs(vWorld.x)-6.75));
            col=mix(col,vec3(.64,.69,.65),max(line,edge)*.8);
            float along=mod(z+10.0,20.0)-10.0;
            float pool=exp(-along*along*.045);
            float sides=exp(-pow((abs(vWorld.x)-5.9)*.63,2.0));
            col+=vec3(.45,.25,.16)*pool*sides*.34;
            float sheen=pow(max(dot(reflect(-light,n),eye),0.0),24.0);
            col+=vec3(.29,.22,.29)*sheen;
            float fwd=max(-vWorld.z,0.0);
            float beam=exp(-pow((vWorld.x-uPlayer)/(1.6+fwd*.12),2.0));
            beam*=smoothstep(1.0,4.0,fwd)*(1.0-smoothstep(12.0,38.0,fwd));
            col+=beam*vec3(.10,.15,.18)*(.35+uNight);
          }
          if(vKind>1.5&&vKind<2.5){
            float spec=pow(max(dot(reflect(-light,n),eye),0.0),70.0);
            col+=vec3(.72,.81,.85)*spec*.7;
            col+=vec3(.22,.42,.5)*fres*.65;
            col+=vec3(.18,.12,.20)*smoothstep(.1,.85,n.y);
          }
          if(vKind>2.5&&vKind<3.5) col=base*1.35;
          if(vKind>3.5&&vKind<4.5&&abs(n.y)<.5){
            float axis=abs(n.x)>.5?vWorld.z-uTravel:vWorld.x;
            vec2 cell=vec2(axis*.48,vWorld.y*.42);
            vec2 f=fract(cell);
            float window=step(.18,f.x)*step(f.x,.66)*step(.18,f.y)*step(f.y,.68);
            float lit=step(.36,hash(floor(cell)));
            vec3 glow=mix(vec3(.95,.62,.30),vec3(.22,.68,.77),step(.6,hash(floor(cell)+9.0)));
            col+=glow*window*lit*(.38+uNight*.37);
          }
          if(vKind>4.5&&vKind<5.5){
            float a=(1.0-smoothstep(.25,1.0,length(vLocal.xz)))*.53;
            outColor=vec4(.015,.03,.05,a);return;
          }
          if(vKind>5.5){
            col=vec3(.035,.085,.12)+vec3(.22,.38,.47)*fres;
            col+=vec3(.38,.25,.31)*pow(max(n.y,0.0),2.0)*.5;
            col+=pow(max(dot(reflect(-light,n),eye),0.0),100.0)*.45;
          }
          vec3 fog=mix(vec3(.40,.31,.39),vec3(.075,.13,.20),uNight);
          float fogAmount=1.0-exp(-max(length(vWorld-uCamera)-35.0,0.0)*.0062);
          outColor=vec4(mix(col,fog,min(.97,fogAmount)),1.0);
        }
        """;
    private static final String POST_FRAGMENT="""
        #version 300 es
        precision highp float;
        in vec2 uv;
        uniform sampler2D uScene;
        uniform vec2 uPixel;
        uniform float uBloom,uFlash;
        out vec4 outColor;
        vec3 bright(vec2 p){return max(texture(uScene,p).rgb-vec3(.67),0.0);}
        void main(){
          vec3 col=texture(uScene,uv).rgb;
          if(uBloom>.5){
            vec3 glow=vec3(0.0);
            for(int i=1;i<=3;i++){
              vec2 d=uPixel*float(i)*2.8;
              glow+=bright(uv+vec2(d.x,0.0))+bright(uv-vec2(d.x,0.0));
              glow+=bright(uv+vec2(0.0,d.y))+bright(uv-vec2(0.0,d.y));
            }
            col+=glow*.065;
          }
          vec2 center=uv-.5;
          col*=1.0-dot(center,center)*.36;
          col=mix(col,vec3(.85,.19,.24),uFlash*.25);
          col=pow(max(col,vec3(0.0)),vec3(.94));
          outColor=vec4(col,1.0);
        }
        """;
}
