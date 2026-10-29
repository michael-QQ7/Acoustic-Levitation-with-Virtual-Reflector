package acousticfield3d.algorithms.dpcat;

import acousticfield3d.algorithms.AcousticPropagation;
import acousticfield3d.math.Vector3f;
import acousticfield3d.simulation.*;
import acousticfield3d.protocols.MegaFPGA;
import java.util.*;

public class DpcatCoreTest {
    static int assertions;
    public static void check(boolean c,String m){assertions++;if(!c)throw new AssertionError(m);}
    static void near(double a,double b,double tol,String m){check(Math.abs(a-b)<=tol,m+": "+a+" vs "+b);}
    public static Simulation fixture() {
        Simulation s=new Simulation();s.setMediumSpeed(343);s.getReflector().setEnabled(true);s.getReflector().setPlaneY(-.075);
        Random random=new Random(192);
        for(int i=0;i<256;i++) {
            Transducer t=new Transducer();t.setOrderNumber(i);t.setDriverPinNumber(i);
            t.getTransform().getTranslation().set((i%16-7.5f)*.0105f,0,(i/16-7.5f)*.0105f);
            t.getTransform().getRotation().fromAngles((float)Math.PI,0,0);
            t.setAmplitude(i<64?0:1);t.setPhase(random.nextInt(32)/16f);t.setPhaseCorrection((i%7-3)*.017f);s.transducers.add(t);
        }
        return s;
    }
    public static List<Vector3f> points(double d) {return Arrays.asList(new Vector3f((float)(-d/2),-.0375f,0),new Vector3f((float)(d/2),-.0375f,0));}
    public static void main(String[] args) {
        Simulation s=fixture();DpcatSnapshot snap=new DpcatSnapshot(s);DpcatMatrix g=new DpcatMatrix(snap,points(.014));
        double[][] p=g.pressure(snap.initialCodes), tmp=new double[2][g.count];
        for(int k=0;k<32;k++) {
            int[] test=snap.initialCodes.clone();test[5]=k;double[][] full=g.pressure(test);
            DpcatOptimizer.increment(g,p,tmp,5,snap.initialCodes[5],k);
            for(int n=0;n<g.count;n++) {near(tmp[0][n],full[0][n],1e-9,"increment Re");near(tmp[1][n],full[1][n],1e-9,"increment Im");}
        }
        DpcatConfig cfg=new DpcatConfig();cfg.objective=DpcatConfig.Objective.C1_COMPLEX;
        near(DpcatOptimizer.acousticCost(new double[][]{{1},{0}},new double[][]{{0},{1}},cfg),Math.sqrt(2),1e-12,"C1 retains phase");
        cfg.objective=DpcatConfig.Objective.C2_PAPER;cfg.alpha=2;
        near(DpcatOptimizer.acousticCost(new double[][]{{1,3},{0,0}},null,cfg),-2,1e-12,"C2 population std");
        cfg.objective=DpcatConfig.Objective.C2_NORMALIZED;cfg.beta=4;
        near(DpcatOptimizer.acousticCost(new double[][]{{1,3},{0,0}},null,cfg),0,1e-11,"C2N formula");
        check(DpcatOptimizer.distance(31,0)==1,"circular wrap");
        DpcatConfig held=new DpcatConfig();held.objective=DpcatConfig.Objective.C2_PAPER;held.lambdaPhase=1e12;
        held.initialization=DpcatConfig.Initialization.PREVIOUS_FRAME;
        int[] reference=snap.initialCodes.clone();reference[0]=31;
        DpcatOptimizer.Result stable=DpcatOptimizer.optimize(g,snap.initialCodes,reference,null,held,()->false,x->{});
        check(Arrays.equals(stable.codes,reference),"large continuity penalty retains previous codes");
        check(Arrays.equals(stable.initialCodes,reference),"Previous Frame initialization uses previous codes");
        cfg.objective=DpcatConfig.Objective.C2_PAPER;cfg.alpha=100;
        int[][] heightCodes=new int[3][];double[] heights={-.075,-.070,-.065};double lastG=Double.NaN;
        for(int h=0;h<heights.length;h++) {
            s.getReflector().setPlaneY(heights[h]);snap=new DpcatSnapshot(s);g=new DpcatMatrix(snap,points(.014));
            if(h>0)check(Math.abs(lastG-g.real[0][0])>1e-6,"height changes transfer");lastG=g.real[0][0];
            DpcatOptimizer.Result r=DpcatOptimizer.optimize(g,snap.initialCodes,null,null,cfg,()->false,x->{});
            heightCodes[h]=r.codes;
            check(r.finalCost<=r.initialCost+1e-8,"cost decreases");
            for(int i=1;i<r.sweepCosts.size();i++)check(r.sweepCosts.get(i)<=r.sweepCosts.get(i-1)+1e-8,"monotonic sweep");
            int[] hw=snap.hardwareCodes(r.codes);check(hw.length==256,"complete state");
            float[] correction=new float[256],phase=new float[256];
            for(int i=0;i<256;i++){correction[i]=s.transducers.get(i).getPhaseCorrection();phase[i]=s.transducers.get(i).getPhase();}
            snap.apply(r.codes,s);
            for(int i=0;i<256;i++) {
                check(Float.floatToIntBits(correction[i])==Float.floatToIntBits(s.transducers.get(i).getPhaseCorrection()),"correction bitwise unchanged");
                check(i<64?hw[i]==32:hw[i]>=0&&hw[i]<32,"ON/OFF code");
                if(i<64)near(phase[i],s.transducers.get(i).getPhase(),0,"disabled phase unchanged");
            }
            String command=new String(new MegaFPGA().encodePattern(s.transducers),java.nio.charset.StandardCharsets.US_ASCII);
            int[] encoded=Arrays.stream(command.substring(7).trim().split(",")).mapToInt(Integer::parseInt).toArray();
            check(Arrays.equals(encoded,hw),"export equals existing hardware encoder");
            System.out.printf(Locale.US,"height %.0f mm: cost %.5f -> %.5f; CV %.6f -> %.6f%n",-heights[h]*1000,r.initialCost,r.finalCost,r.initialCV,r.cv);
            // Identical initial state for the next height comparison.
            s=fixture();
        }
        check(!Arrays.equals(heightCodes[0],heightCodes[1])&&!Arrays.equals(heightCodes[1],heightCodes[2]),"height changes optimum");
        s.getReflector().setEnabled(false);snap=new DpcatSnapshot(s);g=new DpcatMatrix(snap,points(.014));
        double[] direct=new double[2];Transducer t=snap.active.get(0);Vector3f pos=points(.014).get(0);
        AcousticPropagation.transfer(t,snap.model,pos.x,pos.y,pos.z,direct);
        near(g.real[0][0],direct[0]*t.getAmplitude()*t.getPower(),1e-10,"reflector off direct Re");
        near(g.imag[0][0],direct[1]*t.getAmplitude()*t.getPower(),1e-10,"reflector off direct Im");
        cfg.alpha=1000;DpcatOptimizer.Result r=DpcatOptimizer.optimize(g,snap.initialCodes,null,null,cfg,()->false,x->{});
        check(r.cv<r.initialCV,"high-alpha fixture CV decreases");
        System.out.printf(Locale.US,"Uniformity fixture CV %.6f -> %.6f (not a general guarantee)%n",r.initialCV,r.cv);
        final DpcatMatrix matrix=g;final DpcatSnapshot captured=snap;
        try {DpcatOptimizer.optimize(matrix,captured.initialCodes,null,null,cfg,()->true,x->{});throw new AssertionError("cancel");}
        catch(java.util.concurrent.CancellationException expected){}
        s.transducers.get(64).setPhaseCorrection(.9f);
        try{snap.apply(r.codes,s);throw new AssertionError("stale model");}catch(IllegalStateException expected){}
        System.out.println("PASS DPCAT core: "+assertions+" assertions");
    }
}
