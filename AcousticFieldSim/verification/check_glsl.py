"""Linux/Mesa headless check: compile shipped shaders and compare their field with a double reference.
Requires Python 3, numpy and system libEGL/libGL. Does not need a window or hardware connection.
"""
import ctypes as C, os
from pathlib import Path
import numpy as np
os.environ.setdefault('EGL_PLATFORM','surfaceless')
egl=C.CDLL('libEGL.so.1');gl=C.CDLL('libGL.so.1')
def fn(lib,name,restype,*args):
 f=getattr(lib,name);f.restype=restype;f.argtypes=list(args);return f
P=C.c_void_p;I=C.c_int;U=C.c_uint;F=C.c_float
getdisplay=fn(egl,'eglGetDisplay',P,P);init=fn(egl,'eglInitialize',U,P,P,P)
display=getdisplay(None);assert init(display,None,None),'EGL initialize'
assert fn(egl,'eglBindAPI',U,U)(0x30A2)
attrs=(I*7)(0x3033,1,0x3040,8,0x3024,8,0x3038);config=P();n=I()
assert fn(egl,'eglChooseConfig',U,P,P,P,I,P)(display,attrs,C.byref(config),1,C.byref(n)) and n.value
context=fn(egl,'eglCreateContext',P,P,P,P,P)(display,config,None,(I*1)(0x3038))
surf=fn(egl,'eglCreatePbufferSurface',P,P,P,P)(display,config,(I*5)(0x3057,1,0x3056,1,0x3038))
assert fn(egl,'eglMakeCurrent',U,P,P,P,P)(display,surf,surf,context)
print('GL:',fn(gl,'glGetString',C.c_char_p,U)(0x1F02).decode())
create=fn(gl,'glCreateShader',U,U);src=fn(gl,'glShaderSource',None,U,I,P,P);compile_=fn(gl,'glCompileShader',None,U)
getiv=fn(gl,'glGetShaderiv',None,U,U,P);getlog=fn(gl,'glGetShaderInfoLog',None,U,I,P,P)
def shader(code,kind):
 obj=create(kind);b=code.encode();arr=(C.c_char_p*1)(b);src(obj,1,arr,None);compile_(obj)
 ok=I();getiv(obj,0x8B81,C.byref(ok))
 if not ok.value:
  buf=C.create_string_buffer(12000);getlog(obj,12000,None,buf);raise AssertionError(buf.value.decode())
 return obj
newprogram=fn(gl,'glCreateProgram',U);attach=fn(gl,'glAttachShader',None,U,U);link=fn(gl,'glLinkProgram',None,U)
piv=fn(gl,'glGetProgramiv',None,U,U,P);plog=fn(gl,'glGetProgramInfoLog',None,U,I,P,P)
def program(v,f):
 p=newprogram();attach(p,shader(v,0x8B31));attach(p,shader(f,0x8B30));link(p)
 ok=I();piv(p,0x8B82,C.byref(ok))
 if not ok.value:
  b=C.create_string_buffer(12000);plog(p,12000,None,b);raise AssertionError(b.value.decode())
 return p
root=Path(__file__).resolve().parents[1]/'src/acousticfield3d/resources'
def expand(name):
 text=(root/name).read_text().replace('_N_TRANS_','256')
 import re
 return re.sub(r'//INCLUDE ([^\s]+)',lambda m:expand(m[1]),text)
for mode in ('AMP','PHASE','AMPPHASE'):
 f=expand('SliceRT_F.glsl')
 for m in ('AMP','PHASE','AMPPHASE'):f=f.replace('_USE_'+m+'_','' if m==mode else '//')
 program(expand('SliceRT_V.glsl'),f)
program(expand('Volumetric_V.glsl'),expand('Volumetric_F.glsl'))
print('PASS: amplitude / phase / amp-phase / volumetric GLSL compile and link (256 channels)')
f='''#define N_TRANS 256
uniform vec3 tPos[N_TRANS]; uniform vec3 tNorm[N_TRANS]; uniform vec4 tSpecs[N_TRANS];
uniform vec3 probe;
'''+expand('FieldCalcsStencil.glsl')+'\nvoid main(){gl_FragColor=vec4(fieldAt(probe),0.0,1.0);}\n'
p=program('void main(){gl_Position=gl_Vertex;}',f);fn(gl,'glUseProgram',None,U)(p)
# Float render target preserves signed complex pressure without display color clipping.
tex=U();fn(gl,'glGenTextures',None,I,P)(1,C.byref(tex));fn(gl,'glBindTexture',None,U,U)(0x0DE1,tex)
fn(gl,'glTexImage2D',None,U,I,I,I,I,I,U,U,P)(0x0DE1,0,0x8814,1,1,0,0x1908,0x1406,None)
fbo=U();fn(gl,'glGenFramebuffers',None,I,P)(1,C.byref(fbo));fn(gl,'glBindFramebuffer',None,U,U)(0x8D40,fbo)
fn(gl,'glFramebufferTexture2D',None,U,U,U,U,I)(0x8D40,0x8CE0,0x0DE1,tex,0)
assert fn(gl,'glCheckFramebufferStatus',U,U)(0x8D40)==0x8CD5
loc=fn(gl,'glGetUniformLocation',I,U,C.c_char_p)
def uniform(name,x,kind=F):
 fn(gl,'glUniform1f' if kind==F else 'glUniform1i',None,I,kind)(loc(p,name.encode()),x)
pos=np.array([[(i%16-7.5)*.01,0,(i//16-7.5)*.01] for i in range(256)],np.float32)
normal=np.tile([.17,.97,.09],(256,1)).astype(np.float32);normal/=np.linalg.norm(normal,axis=1)[:,None]
spec=np.array([[2*np.pi*40000/343,0 if i<64 else 1.28,np.sin(i*.71),.01] for i in range(256)],np.float32)
for name,a in [('tPos',pos),('tNorm',normal),('tSpecs',spec)]:
 fn(gl,'glUniform'+str(a.shape[1])+'fv',None,I,I,P)(loc(p,name.encode()),256,a.ctypes.data_as(P))
fn(gl,'glViewport',None,I,I,I,I)(0,0,1,1)
begin=fn(gl,'glBegin',None,U);vertex=fn(gl,'glVertex2f',None,F,F);end=fn(gl,'glEnd',None)
worst=0
for enabled,H,rho,phase in [(0,-.04,1,0),(1,-.04,0,0),(1,-.04,1,0),(1,-.046,.7,.43),(1,.041,.9,-.2)]:
 uniform('reflectorEnabled',enabled,I);uniform('reflectorY',H);uniform('reflectorMagnitude',rho);uniform('reflectorPhase',phase)
 for probe in [[.003,-.021,.009],[.027,-.04,-.023],[.012,.018,-.009]]:
  probe=np.array(probe,np.float32);fn(gl,'glUniform3fv',None,I,I,P)(loc(p,b'probe'),1,probe.ctypes.data_as(P))
  begin(4);vertex(-1,-1);vertex(3,-1);vertex(-1,3);end()
  pixel=np.zeros(4,np.float32);fn(gl,'glReadPixels',None,I,I,I,I,U,U,P)(0,0,1,1,0x1908,0x1406,pixel.ctypes.data_as(P))
  total=0j
  for mirror in range(2 if enabled else 1):
   ps=pos.astype(float).copy();ns=normal.astype(float).copy();gain=1.;offset=0.
   if mirror:ps[:,1]=2*np.float32(H)-ps[:,1];ns[:,1]*=-1;gain=np.float32(rho);offset=np.float32(phase)
   delta=probe.astype(float)-ps;d=np.linalg.norm(delta,axis=1);cos=np.sum(delta*ns,axis=1)/d/np.linalg.norm(ns,axis=1)
   q=spec[:,0]*.5*spec[:,3]*np.sqrt(np.maximum(0,1-cos*cos))
   total+=np.sum(gain*spec[:,1]*np.sinc(q/np.pi)/d*np.exp(1j*(spec[:,0]*d+spec[:,2]+offset)))
  error=abs(complex(*pixel[:2])-total)/max(1,abs(total));worst=max(worst,error)
  assert error<2e-4,(enabled,H,probe,pixel[:2],total,error)
print('PASS: GPU complex field vs double reference, OFF mask, R=0, +/-Y heights, complex R; max relative error',worst)
