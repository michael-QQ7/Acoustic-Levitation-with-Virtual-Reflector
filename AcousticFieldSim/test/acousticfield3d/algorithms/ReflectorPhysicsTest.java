package acousticfield3d.algorithms;

import acousticfield3d.math.Vector2f;
import acousticfield3d.math.Vector3f;
import acousticfield3d.simulation.*;
import acousticfield3d.utils.FileUtils;
import java.io.File;
import java.util.Random;

/** No external test framework or display required. Throws on any regression. */
public class ReflectorPhysicsTest {
    static int checks;
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message); checks++;
    }
    static void near(double a, double b, double tolerance, String name) {
        check(Double.isFinite(a) && Double.isFinite(b) && Math.abs(a-b)<=tolerance,
            name+": "+a+" != "+b+" tolerance "+tolerance);
    }
    static Transducer source(float x,float y,float z) {
        Transducer t=new Transducer(); t.getTransform().getTranslation().set(x,y,z);
        t.setAmplitude(1); t.setFrequency(40000); t.setApperture(.010f); t.setPower(1.28f); return t;
    }
    static double[] at(Transducer t, Simulation s, double x,double y,double z) {
        double[] v=new double[2]; AcousticPropagation.transfer(t,s,x,y,z,v); return v;
    }
    static double distance(double[] a,double[] b) { return Math.hypot(a[0]-b[0],a[1]-b[1]); }
    static void propagation() {
        Simulation s=new Simulation(); s.setMediumSpeed(343);
        Transducer t=source(.012f,0,-.008f);
        ReflectorSettings r=s.getReflector(); r.setPlaneY(-.04);
        double[] direct=at(t,s,.006,-.017,.004);
        r.setEnabled(true); r.setMagnitude(0);
        near(distance(direct,at(t,s,.006,-.017,.004)),0,1e-12,"R=0 is direct");
        r.setMagnitude(1);
        t.getTransform().getRotation().fromAngles(.21f,.33f,.41f);
        // Neumann and Dirichlet boundary conditions, including tilted transducer normals.
        for(int i=0;i<5;i++) {
            double x=.007*i,z=.005;
            double[] plus=at(t,s,x,-.04+1e-6,z),minus=at(t,s,x,-.04-1e-6,z);
            near(distance(plus,minus),0,1e-9,"rigid plane normal derivative");
            double[] total=at(t,s,x,-.04,z);
            r.setEnabled(false); double[] d=at(t,s,x,-.04,z); r.setEnabled(true);
            near(total[0],2*d[0],1e-11,"rigid pressure doubles Re");
            near(total[1],2*d[1],1e-11,"rigid pressure doubles Im");
            r.setPhaseDegrees(180);
            near(Math.hypot(at(t,s,x,-.04,z)[0],at(t,s,x,-.04,z)[1]),0,1e-11,"pressure release boundary");
            r.setPhaseDegrees(0);
        }
        double[] old=at(t,s,0,-.02,0); r.setPlaneY(-.041);
        check(distance(old,at(t,s,0,-.02,0))>1,"height changes field");
        r.setMagnitude(.65); r.setPhaseDegrees(31);
        Vector2f api=CalcField.calcFieldForTrans(t,.37f,.003f,-.021f,.009f,s);
        double[] v=at(t,s,.003f,-.021f,.009f);
        near(api.x,v[0]*Math.cos(.37f)-v[1]*Math.sin(.37f),1e-5,"public CPU transfer Re");
        near(api.y,v[0]*Math.sin(.37f)+v[1]*Math.cos(.37f),1e-5,"public CPU transfer Im");
        System.out.println("PASS: direct/reflected fields, tilted normals, rigid/release boundaries, height and complex R");
    }
    static void derivatives() {
        Simulation s=new Simulation(); s.setMediumSpeed(343);
        Transducer t=source(0,0,0);
        AcousticPropagation.Source source=new AcousticPropagation.Source(t,343);
        double y=.026,k=source.k;
        // Exact radial derivatives of exp(ikr)/r on +Y axis, zero aperture => monopole.
        t.setApperture(0); source=new AcousticPropagation.Source(t,343);
        double re=Math.cos(k*y),im=Math.sin(k*y);
        double[][] exact={
            {re/y,im/y},
            {-re/(y*y)-k*im/y,-im/(y*y)+k*re/y},
            {(2/(y*y*y)-k*k/y)*re+2*k*im/(y*y),(2/(y*y*y)-k*k/y)*im-2*k*re/(y*y)},
            {(-6/Math.pow(y,4)+3*k*k/(y*y))*re-(6*k/Math.pow(y,3)-k*k*k/y)*im,
             (-6/Math.pow(y,4)+3*k*k/(y*y))*im+(6*k/Math.pow(y,3)-k*k*k/y)*re}
        };
        int[] indices={0,2,8,11};
        double last=Double.POSITIVE_INFINITY;
        for(int div:new int[]{32,64,128}) {
            double[][] result=PropagationDerivatives.evaluate(source,s.getReflector(),0,y,0,343.0/40000/div,true);
            double error=0;
            for(int d=0;d<=3;d++) {
                double e=distance(result[indices[d]],exact[d])/Math.hypot(exact[d][0],exact[d][1]);
                check(e<(div==32?5e-4:4e-5),"derivative order "+d+" div="+div+" rel="+e);
                error=Math.max(error,e);
            }
            check(error<last,"derivative convergence"); last=error;
        }
        System.out.println("PASS: first/second/third spatial derivatives and step convergence");
    }
    static void focusAndPersistence() throws Exception {
        Simulation s=new Simulation(); s.setMediumSpeed(343); s.getReflector().setEnabled(true);
        s.getReflector().setMagnitude(.83); s.getReflector().setPhaseDegrees(19);
        Vector3f p=new Vector3f(.009f,-.022f,-.013f);
        for(int i=0;i<256;i++) {
            Transducer t=source((i%16-7.5f)*.01f,0,(i/16-7.5f)*.01f);
            t.setOrderNumber(i);t.setDriverPinNumber(i);t.setAmplitude(i<64?0:1);
            t.setPhaseCorrection((i%32)/16f);t.setPhase(.17f);
            s.transducers.add(t);
        }
        SimplePhaseAlgorithms.focus(s.transducers,p,s);
        double real=0,imag=0,bound=0;
        for(int i=0;i<256;i++) {
            Transducer t=s.transducers.get(i);
            near(t.getPhaseCorrection(),(i%32)/16f,0,"correction preserved");
            near(t.getAmplitude(),i<64?0:1,0,"OFF preserved");
            if(i<64) {near(t.getPhase(),.17f,0,"OFF phase preserved"); continue;}
            double[] v=at(t,s,p.x,p.y,p.z);double phase=t.getPhase()*Math.PI;
            real+=v[0]*Math.cos(phase)-v[1]*Math.sin(phase);
            imag+=v[0]*Math.sin(phase)+v[1]*Math.cos(phase);bound+=Math.hypot(v[0],v[1]);
        }
        near(Math.hypot(real,imag),bound,1e-8*bound,"phase focus reaches triangle inequality bound");
        File tmp=File.createTempFile("reflector-roundtrip-",".xml.gz");
        try {
            FileUtils.writeCompressedObject(tmp,s);
            Simulation copy=(Simulation)FileUtils.readCompressedObject(tmp);
            copy.sortTransducers();
            check(copy.transducers.size()==256,"no image channels serialized");
            near(copy.getReflector().getMagnitude(),.83,0,"reflector coefficient serialized");
            near(copy.getReflector().getPhaseDegrees(),19,0,"reflection phase serialized");
            near(copy.getReflector().getPlaneY(),-.04,0,"height serialized");
            check(copy.getReflector().isEnabled(),"enabled serialized");
            for(int i=0;i<256;i++) {
                near(copy.transducers.get(i).getPhaseCorrection(),s.transducers.get(i).getPhaseCorrection(),0,"correction XML roundtrip");
                check(copy.transducers.get(i).getDriverPinNumber()==i,"hardware pin preserved");
                near(copy.transducers.get(i).getAmplitude(),i<64?0:1,0,"OFF XML roundtrip");
            }
            // A bean without the new property represents an old project; reflection defaults OFF.
            Simulation old=new Simulation();FileUtils.writeCompressedObject(tmp,old);
            check(!((Simulation)FileUtils.readCompressedObject(tmp)).getReflector().isEnabled(),"old-project default OFF");
        } finally {tmp.delete();}
        System.out.println("PASS: 192/256 focus, 64 OFF channels, correction/pin preservation, XML.GZ save/load");
    }
    static void cacheGradients() {
        // Exercise the production phase-gradient algebra with reflected transfer and derivatives.
        CachedPointFieldCalc cp=new CachedPointFieldCalc(new Vector3f(),false,true,.0001f,new acousticfield3d.renderer.Renderer(null,null){
            @Override public int getnTransducers(){return 6;}
        });
        cp.allocate(null);cp.M1=2e-12;cp.M2=3e-17;cp.K1=2*cp.M1;cp.K2=2*cp.M2;
        double[][] re={cp.ka,cp.xka,cp.yka,cp.zka,cp.xyka,cp.xzka,cp.yzka,cp.xxka,cp.yyka,cp.zzka,
            cp.xxxka,cp.yyyka,cp.zzzka,cp.xyyka,cp.xzzka,cp.yxxka,cp.yzzka,cp.zxxka,cp.zyyka};
        double[][] im={cp.kb,cp.xkb,cp.ykb,cp.zkb,cp.xykb,cp.xzkb,cp.yzkb,cp.xxkb,cp.yykb,cp.zzkb,
            cp.xxxkb,cp.yyykb,cp.zzzkb,cp.xyykb,cp.xzzkb,cp.yxxkb,cp.yzzkb,cp.zxxkb,cp.zyykb};
        Simulation s=new Simulation();s.getReflector().setEnabled(true);s.getReflector().setPhaseDegrees(23);
        Random random=new Random(19);double[] phases=new double[6],g=new double[6];
        for(int i=0;i<6;i++) {
            Transducer t=source((i-3)*.011f,0,(i%2)*.012f);
            double[][] v=PropagationDerivatives.evaluate(new AcousticPropagation.Source(t,s.getMediumSpeed()),s.getReflector(),.003,-.021,.004,.0001,true);
            for(int d=0;d<re.length;d++){re[d][i]=v[d][0];im[d][i]=v[d][1];}
            phases[i]=random.nextDouble()*6;
        }
        for(int mode=0;mode<8;mode++) {
            eval(cp,phases,mode);
            if(mode==0)cp.gradientPressure(g);else if(mode==1)cp.gradientGorkov(g);
            else if(mode==2)cp.gradientGorkovGradientX(g);else if(mode==3)cp.gradientGorkovGradientY(g);
            else if(mode==4)cp.gradientGorkovGradientZ(g);else if(mode==5)cp.gradientGorkovLaplacianX(g);
            else if(mode==6)cp.gradientGorkovLaplacianY(g);else cp.gradientGorkovLaplacianZ(g);
            double scale=0;for(double v:g)scale=Math.max(scale,Math.abs(v));
            for(int i=0;i<6;i++) {
                double orig=phases[i],h=.002;
                phases[i]=orig+h;double plus=eval(cp,phases,mode);
                phases[i]=orig-h;double minus=eval(cp,phases,mode);phases[i]=orig;
                near(g[i],(plus-minus)/(2*h),scale*.001+1e-14,"phase gradient mode="+mode+" i="+i);
            }
        }
        java.util.Arrays.fill(cp.ka,0);java.util.Arrays.fill(cp.kb,0);cp.updatePressure(phases);cp.gradientPressure(g);
        for(double v:g)near(v,0,0,"zero field gradient finite");
        System.out.println("PASS: pressure, Gorkov, force and Laplacian phase gradients vs finite differences");
    }
    static double eval(CachedPointFieldCalc cp,double[] p,int mode) {
        if(mode==0){cp.updatePressure(p);return cp.evalPressure();}
        if(mode==1){cp.updateGorkov(p);return cp.evalGorkov();}
        if(mode<5){cp.updateGorkovGradient(p);return mode==2?cp.evalGorkovGradientX():mode==3?cp.evalGorkovGradientY():cp.evalGorkovGradientZ();}
        cp.updateGorkovLaplacian(p);
        return mode==5?cp.evalGorkovLaplacianX():mode==6?cp.evalGorkovLaplacianY():cp.evalGorkovLaplacianZ();
    }
    public static void main(String[] args) throws Exception {
        propagation();derivatives();focusAndPersistence();cacheGradients();
        System.out.println("ALL PASS: "+checks+" assertions");
    }
}
