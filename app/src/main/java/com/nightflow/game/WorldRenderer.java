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
    public volatile boolean brake=false,gas=false,garage=false;
    public volatile Settings preview=null;
    public volatile float orbitYaw=34,garageFraction=.57f;
    public volatile String failure=null;
    public volatile int framesRendered=0,lastGlError=0;
    private final MainActivity owner;
    private Geometry.Mesh road,ground,cityHigh,cityLow,mountains,shadow,wheel,showroom,gate;
    private final Geometry.Mesh[][] cars=new Geometry.Mesh[4][3];
    private int scene,sky,post,width=1,height=1,framebuffer=0,texture=0,depth=0,quality=-1;
    private int uVP,uModel,uCamera,uPaint,uTravel,uNight,uPlayer;
    private final float[] projection=new float[16],view=new float[16],vp=new float[16],model=new float[16];
    private float cameraX=-1.7f,cameraFov=62,accumulator=0;
    private Settings configured;
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
            for(int i=0;i<4;i++)for(int j=0;j<3;j++)cars[i][j]=Geometry.car(i,j);
            wheel=Geometry.wheel();showroom=Geometry.showroom();gate=Geometry.gate();
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
    private void render(){
        Settings s=garage&&preview!=null?preview:settings;
        if(configured!=settings){configured=settings;state.configure(settings);}
        if(quality!=s.quality||framebuffer==0)allocate(s);
        long now=System.nanoTime();float dt=lastTime==0?1f/60:Math.min(.1f,(now-lastTime)/1e9f);lastTime=now;
        accumulator+=dt;
        while(accumulator>=1f/120){state.tick(1f/120,s.control==0?tilt:touch,brake,gas);accumulator-=1f/120;}
        if(state.crashSerial!=notifiedCrash){notifiedCrash=state.crashSerial;owner.onCollision();}
        int rw=Math.max(1,Math.round(width*s.scale())),rh=Math.max(1,Math.round(height*s.scale()));
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer);GLES30.glViewport(0,0,rw,rh);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT|GLES30.GL_DEPTH_BUFFER_BIT);GLES30.glUseProgram(sky);
        GLES30.glUniform1f(loc(sky,"uNight"),s.night?1:0);GLES30.glUniform1f(loc(sky,"uGarage"),garage?1:0);
        GLES30.glUniform1f(loc(sky,"uAspect"),(float)width/height);GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        boolean menu=state.phase==GameState.MENU;
        cameraX+=(state.x-cameraX)*(1-(float)Math.exp(-dt*4.5f));
        float cx,cy,cz,lookX,lookY,lookZ;
        float aspect=(float)width/height;
        if(garage){
            GLES30.glViewport(0,0,Math.max(1,Math.round(rw*garageFraction)),rh);aspect*=garageFraction;
            float a=(float)Math.toRadians(orbitYaw);cx=(float)Math.sin(a)*5.6f;cz=(float)Math.cos(a)*5.6f;cy=2.45f;
            lookX=0;lookY=.70f;lookZ=0;
        }else{
            cx=menu?state.x+4.5f:cameraX*.70f+state.x*.30f;
            cy=menu?2.25f:s.camera==2?1.30f:s.camera==1?4.85f:3.05f;
            cz=menu?6.4f:s.camera==2?-.78f:s.camera==1?10.2f:7.65f;
            cy+=menu?0:state.pitch*.025f;
            lookX=menu?state.x-1.4f:cameraX*.65f;lookY=menu?.72f:.95f;lookZ=menu?-2.2f:-27;
        }
        Matrix.setLookAtM(view,0,cx,cy,cz,lookX,lookY,lookZ,0,1,0);
        float wantedFov=garage?48:s.fov+Math.min(6,state.speed/15);
        cameraFov+=(wantedFov-cameraFov)*(1-(float)Math.exp(-dt*4));
        Matrix.perspectiveM(projection,0,garage?48:cameraFov,aspect,.12f,640);Matrix.multiplyMM(vp,0,projection,0,view,0);
        GLES30.glUseProgram(scene);GLES30.glUniformMatrix4fv(uVP,1,false,vp,0);GLES30.glUniform3f(uCamera,cx,cy,cz);
        GLES30.glUniform1f(uTravel,state.travel);GLES30.glUniform1f(uNight,garage?0:s.night?1:0);
        GLES30.glUniform1f(uPlayer,state.x);GLES30.glUniform1f(loc(scene,"uGarage"),garage?1:0);
        GLES30.glUniform1f(loc(scene,"uWet"),s.wet?1:0);GLES30.glUniform1f(loc(scene,"uGhost"),0);
        GLES30.glUniform1f(loc(scene,"uBrake"),0);
        if(garage){draw(showroom,0,0,0,0,0xffffff);}
        else{
            draw(ground,0,0,0,0,0xffffff);draw(road,0,0,0,0,0xffffff);draw(mountains,0,0,0,0,0xffffff);
            float offset=state.travel%320;Geometry.Mesh city=s.quality>=2?cityHigh:cityLow;
            draw(city,0,0,offset,0,0xffffff);draw(city,0,0,offset-320,0,0xffffff);
            if(state.mode==GameState.TIME_ATTACK&&state.phase!=GameState.MENU&&state.gateDistance<state.targetDistance){
                float z=state.distance-state.gateDistance;if(z> -220)draw(gate,state.gateX,0,z,0,0xffffff);
            }
        }
        if(s.shadows||garage){
            GLES30.glEnable(GLES30.GL_BLEND);GLES30.glDepthMask(false);
            if(garage||s.camera!=2||menu)drawShadow(garage?0:state.x,0,1.5f,2.85f);
            if(!garage)for(GameState.Car c:state.traffic)if(c.active&&c.z> -190)drawShadow(c.x,c.z,1.3f,c.length*.63f);
            if(!garage&&state.mode==GameState.AI_RACE&&state.phase!=GameState.MENU)for(GameState.Rival r:state.rivals){float z=state.distance-r.distance;if(z> -190&&z<24)drawShadow(r.x,z,1.3f,2.8f);}
            GLES30.glDepthMask(true);GLES30.glDisable(GLES30.GL_BLEND);
        }
        if(!garage){
            for(GameState.Car c:state.traffic)if(c.active&&c.z> -225)drawCar(c.shape,0,c.x,0,c.z,0,0,0,Settings.PAINTS[c.paint],0,state.wheelAngle,0,false);
            if(state.mode==GameState.AI_RACE&&state.phase!=GameState.MENU)for(GameState.Rival r:state.rivals){
                float z=state.distance-r.distance;if(z> -220&&z<24)drawCar(r.shape,1,r.x,0,z,0,0,0,Settings.PAINTS[r.paint],1,state.wheelAngle,0,false);
            }
        }
        if(garage||s.camera!=2||menu){
            if(state.invincible<=0||((int)(state.animation*10)%2==0)){
                GLES30.glUniform1f(loc(scene,"uBrake"),brake&&!menu?1:0);
                drawCar(s.car,s.kits[s.car],garage?0:state.x,0,0,garage||menu?0:state.yaw,
                    garage||menu?0:state.roll,garage||menu?0:state.pitch,Settings.PAINTS[s.paint],s.rims,
                    garage||menu?0:state.wheelAngle,garage||menu?0:state.steer*20,s.neon);
                GLES30.glUniform1f(loc(scene,"uBrake"),0);
            }
        }
        if(!garage&&state.mode==GameState.TIME_ATTACK&&state.ghostVisible&&state.phase!=GameState.MENU){
            GLES30.glEnable(GLES30.GL_BLEND);GLES30.glDepthMask(false);GLES30.glUniform1f(loc(scene,"uGhost"),1);
            drawCar(s.car,s.kits[s.car],state.ghostX,0,state.ghostZ,0,0,0,0x72dce8,0,state.wheelAngle,0,false);
            GLES30.glUniform1f(loc(scene,"uGhost"),0);GLES30.glDepthMask(true);GLES30.glDisable(GLES30.GL_BLEND);
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);GLES30.glViewport(0,0,width,height);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glUseProgram(post);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        GLES30.glUniform1i(loc(post,"uScene"),0);GLES30.glUniform2f(loc(post,"uPixel"),1f/rw,1f/rh);
        GLES30.glUniform1f(loc(post,"uBloom"),s.bloom&&s.quality>0?1:0);GLES30.glUniform1f(loc(post,"uFlash"),state.flash);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);framesRendered++;fpsFrames++;
        if(fpsStart==0)fpsStart=now;
        if(now-fpsStart>1_000_000_000L){actualFps=fpsFrames*1e9f/(now-fpsStart);fpsStart=now;fpsFrames=0;
            int error=GLES30.glGetError();if(error!=GLES30.GL_NO_ERROR)lastGlError=error;}
    }
    private void carMatrix(float x,float y,float z,float yaw,float roll,float pitch){
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.rotateM(model,0,yaw,0,1,0);
        Matrix.rotateM(model,0,roll,0,0,1);Matrix.rotateM(model,0,pitch,1,0,0);
    }
    private void drawCar(int style,int kit,float x,float y,float z,float yaw,float roll,float pitch,int paint,int rims,float angle,float steering,boolean neon){
        carMatrix(x,y,z,yaw,roll,pitch);GLES30.glUniformMatrix4fv(uModel,1,false,model,0);
        GLES30.glUniform3f(uPaint,((paint>>16)&255)/255f,((paint>>8)&255)/255f,(paint&255)/255f);
        int rim=rims==1?0xaf8551:rims==2?0x283039:0xc5d1dc;
        GLES30.glUniform3f(loc(scene,"uRim"),((rim>>16)&255)/255f,((rim>>8)&255)/255f,(rim&255)/255f);
        cars[style][kit].draw();float stretch=style==1?1.09f:style==2?.93f:1;
        for(int side:new int[]{-1,1})for(int front:new int[]{-1,1}){
            carMatrix(x,y,z,yaw,roll,pitch);Matrix.translateM(model,0,side*(kit==2?1.035f:.943f),.366f,front*1.4f*stretch);
            if(front<0)Matrix.rotateM(model,0,-steering,0,1,0);Matrix.rotateM(model,0,angle,1,0,0);
            GLES30.glUniformMatrix4fv(uModel,1,false,model,0);wheel.draw();
        }
        if(neon){
            GLES30.glEnable(GLES30.GL_BLEND);GLES30.glDepthMask(false);GLES30.glUniform1f(loc(scene,"uNeon"),1);
            drawShadow(x,z,1.5f,2.7f);GLES30.glUniform1f(loc(scene,"uNeon"),0);
            GLES30.glDepthMask(true);GLES30.glDisable(GLES30.GL_BLEND);
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
        uniform float uNight,uAspect,uGarage;
        out vec4 outColor;
        float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
        float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+1.0),f.x),f.y);}
        void main(){
          if(uGarage>.5){
            float glow=exp(-length((uv-vec2(.30,.55))*vec2(1.0,1.6))*4.0);
            outColor=vec4(vec3(.025,.04,.062)+vec3(.065,.09,.125)*glow,1);return;
          }
          vec3 low=mix(vec3(.64,.69,.73),vec3(.055,.10,.18),uNight);
          vec3 high=mix(vec3(.105,.245,.43),vec3(.006,.018,.045),uNight);
          vec3 col=mix(low,high,smoothstep(.05,1.0,uv.y));
          vec2 sun=(uv-vec2(.72,.56))*vec2(uAspect,1.0);float d=length(sun);
          col+=vec3(.40,.27,.14)*exp(-d*7.0)*(1.0-uNight);
          col=mix(col,mix(vec3(1.0,.92,.78),vec3(.75,.86,.95),uNight),1.0-smoothstep(.025,.028,d));
          float cloud=noise(uv*vec2(9.0*uAspect,12.0))+noise(uv*vec2(19.0*uAspect,24.0))*.4;
          col=mix(col,vec3(.83,.84,.82),smoothstep(.77,1.2,cloud)*.24*(1.0-uNight)*smoothstep(.22,.7,uv.y));
          vec2 grid=uv*vec2(350.0*uAspect,350.0);
          float star=step(.998,hash(floor(grid)))*pow(max(0.0,1.0-length(fract(grid)-.5)*2.0),5.0);
          col+=star*vec3(.8,.88,1.0)*smoothstep(.38,.8,uv.y)*uNight;
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
        uniform vec3 uCamera,uPaint,uRim;
        uniform float uTravel,uNight,uPlayer,uGarage,uWet,uBrake,uGhost,uNeon;
        out vec4 outColor;
        float hash(vec2 p){return fract(sin(dot(p,vec2(41.3,289.1)))*43758.5453);}
        vec3 environment(vec3 r){
          vec3 sky=mix(vec3(.22,.32,.43),vec3(.53,.66,.81),smoothstep(-.15,1.0,r.y));
          sky=mix(sky,sky*vec3(.19,.26,.42),uNight);
          if(uGarage>.5){
            sky=mix(vec3(.08,.11,.16),vec3(.34,.45,.58),smoothstep(-.2,.8,r.y));
            float softbox=pow(max(0.0,1.0-abs(r.z-.22)*5.5),5.0)*smoothstep(.1,.45,r.y);
            softbox+=pow(max(0.0,1.0-abs(r.x+.6)*12.0),4.0)*smoothstep(.0,.55,r.y);
            sky+=softbox*vec3(.9,1.0,1.08);
          }else{
            float skyline=step(.45,hash(floor(vec2(atan(r.z,r.x)*15.0,0))))*(1.0-smoothstep(.02,.30,r.y));
            sky=mix(sky,vec3(.10,.15,.20),skyline*.8);
          }
          return sky;
        }
        void main(){
          vec3 n=normalize(vNormal),eye=normalize(uCamera-vWorld);
          vec3 light=normalize(vec3(-.45,.78,.35));float diff=max(dot(n,light),0.0);
          float ndv=max(dot(n,eye),0.0),fres=pow(1.0-ndv,5.0);
          vec3 ref=reflect(-eye,n),env=environment(ref);
          vec3 base=vKind>1.5&&vKind<2.5?uPaint:vColor;
          vec3 ambient=mix(vec3(.29,.34,.39),vec3(.12,.17,.25),uNight);
          vec3 col=base*(ambient+diff*mix(vec3(.90,.85,.74),vec3(.24,.30,.43),uNight));
          col+=base*vec3(.08,.13,.20)*max(n.y,0.0);
          if(vKind>.5&&vKind<1.5){
            float z=vWorld.z-uTravel;float grain=hash(floor(vec2(vWorld.x,z)*95.0));
            float fade=1.0-smoothstep(8.0,70.0,length(vWorld-uCamera));col*=.84+grain*.26*fade;
            float lane=min(abs(vWorld.x),min(abs(vWorld.x-3.4),abs(vWorld.x+3.4)));
            float aa=max(fwidth(lane),.014),dash=1.0-step(.40,fract(z/11.0));
            float line=(1.0-smoothstep(.046-aa,.046+aa,lane))*dash;
            float edge=1.0-smoothstep(.045-aa,.045+aa,abs(abs(vWorld.x)-6.75));
            col=mix(col,vec3(.70,.72,.68)*(1.0-uNight*.6),max(line,edge)*.88);
            float tyreMarks=(exp(-pow((abs(vWorld.x-1.7)-.76)*15.0,2.0))+exp(-pow((abs(vWorld.x+1.7)-.76)*15.0,2.0)))*.05;
            col*=1.0-tyreMarks;
            float along=mod(z+10.0,20.0)-10.0;
            float pool=exp(-along*along*.12)*exp(-pow((abs(vWorld.x)-5.9)*.9,2.0));
            col+=vec3(.30,.39,.47)*pool*(.12+.6*uNight);
            float wet=.5+.5*sin(vWorld.x*2.1+sin(z*.35));
            col=mix(col,col*.68+env*.20+vec3(.6,.68,.74)*pow(max(dot(reflect(-light,n),eye),0.0),95.0)*.35,uWet*wet);
            float fwd=max(-vWorld.z,0.0),beam=exp(-pow((vWorld.x-uPlayer)/(1.5+fwd*.13),2.0));
            col+=beam*smoothstep(1.0,4.0,fwd)*(1.0-smoothstep(14.0,45.0,fwd))*vec3(.14,.17,.20)*uNight;
          }
          if(vKind>1.5&&vKind<2.5){
            float spec=pow(max(dot(reflect(-light,n),eye),0.0),130.0);
            float ao=smoothstep(.22,.68,vLocal.y);
            col=base*(ambient*.8+diff*.72)*(.62+.38*ao);
            col+=env*(.16+.50*fres)+vec3(.83,.87,.91)*spec*.70;
            col+=base*.02*hash(floor(vLocal.xz*230.0));
          }
          if(vKind>2.5&&vKind<3.5)col=base*1.20;
          if(vKind>3.5&&vKind<4.5&&abs(n.y)<.5){
            float axis=abs(n.x)>.5?vWorld.z-uTravel:vWorld.x;vec2 cell=vec2(axis*.8,vWorld.y*.56);vec2 f=fract(cell);
            float win=step(.08,f.x)*step(f.x,.90)*step(.12,f.y)*step(f.y,.89);
            vec3 glass=environment(ref)*.34+vec3(.015,.03,.045);
            float lit=step(.69,hash(floor(cell)));vec3 glow=mix(vec3(.81,.62,.36),vec3(.45,.59,.67),step(.6,hash(floor(cell)+9.0)));
            col=mix(col,glass+glow*lit*(.06+uNight*.56),win);
          }
          if(vKind>4.5&&vKind<5.5){
            float a=(1.0-smoothstep(.22,1.0,length(vLocal.xz)))*.62;
            outColor=vec4(uNeon>.5?uPaint*1.2:vec3(.006,.012,.02),a*(uNeon>.5?.52:1.0));return;
          }
          if(vKind>5.5&&vKind<6.5)col=vec3(.012,.029,.044)+env*(.30+.58*fres);
          if(vKind>6.5&&vKind<7.5)col=env*.70+base*.18+pow(max(dot(reflect(-light,n),eye),0.0),90.0)*.55;
          if(vKind>7.5&&vKind<8.5){
            float radial=length(vWorld.xz);col=vec3(.075,.095,.125)+vec3(.055,.08,.105)*exp(-radial*.16);
            float joint=min(abs(fract(vWorld.x*.25)-.5),abs(fract(vWorld.z*.25)-.5));col*=.92+.08*smoothstep(.001,.01,joint);
            col+=vec3(.11,.16,.19)*exp(-pow(radial-3.25,2.0)*6.0);
          }
          if(vKind>8.5&&vKind<9.5){
            float weave=mod(floor(vLocal.x*110.0)+floor(vLocal.z*110.0),2.0);col=vec3(.019,.026,.033)+env*(.07+.12*fres)+weave*.007;
          }
          if(vKind>9.5&&vKind<10.5)col=base*(.75+uBrake*.9);
          if(vKind>10.5&&vKind<11.5)col=vec3(.012,.016,.020)+vec3(.035)*diff+env*.018;
          if(vKind>11.5)col=uRim*(.28+diff*.44)+env*(.20+.20*fres);
          vec3 fog=mix(vec3(.46,.53,.59),vec3(.055,.10,.17),uNight);
          float fogAmount=uGarage>.5?0.0:1.0-exp(-max(length(vWorld-uCamera)-45.0,0.0)*.005);
          col=mix(col,fog,min(.95,fogAmount));
          if(uGhost>.5)col=mix(col,vec3(.20,.72,.82),.6);
          outColor=vec4(col,uGhost>.5?.28:1.0);
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
          vec3 a=texture(uScene,uv+vec2(uPixel.x,0)).rgb,b=texture(uScene,uv-vec2(uPixel.x,0)).rgb;
          vec3 c=texture(uScene,uv+vec2(0,uPixel.y)).rgb,d=texture(uScene,uv-vec2(0,uPixel.y)).rgb;
          vec3 mean=(a+b+c+d)*.25;
          float edge=length(col-mean);col=mix(col,mean,smoothstep(.10,.38,edge)*.38);
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
          col*=1.0-dot(center,center)*.22;
          col=mix(col,vec3(.85,.19,.24),uFlash*.25);
          col=pow(max(col,vec3(0.0)),vec3(.92));
          outColor=vec4(col,1.0);
        }
        """;
}
