package acousticfield3d.algorithms.dpcat;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 32-state coordinate direct search. No continuous optimization or post-quantization. */
public final class DpcatOptimizer {
    private static final double[] COS=new double[32], SIN=new double[32];
    static { for(int k=0;k<32;k++){COS[k]=Math.cos(2*Math.PI*k/32); SIN[k]=Math.sin(2*Math.PI*k/32);} }
    public static final class Result {
        public int[] codes;
        public int[] initialCodes;
        public double[][] pressure;
        public double initialCost, finalCost, mean, std, cv, initialCV;
        public int sweeps, changedTransducers, acceptedChanges, restart;
        public boolean converged;
        public final List<Double> sweepCosts=new ArrayList<>();
    }
    /** Stable population std (Welford); complex modulus, not magnitude residual for C1. */
    public static double[] stats(double[][] p) {
        double mean=0,m2=0;
        for(int n=0;n<p[0].length;n++) { double a=Math.hypot(p[0][n],p[1][n]), d=a-mean; mean+=d/(n+1); m2+=d*(a-mean); }
        double std=Math.sqrt(Math.max(0,m2/p[0].length));
        return new double[]{mean,std,mean>0?std/mean:Double.NaN};
    }
    public static double acousticCost(double[][] p, double[][] target, DpcatConfig cfg) {
        if(cfg.objective==DpcatConfig.Objective.C1_COMPLEX) {
            if(target==null || target.length!=2 || target[0].length!=p[0].length || target[1].length!=p[0].length)
                throw new IllegalArgumentException("C1 requires one complex target per position");
            double sum=0; for(int n=0;n<p[0].length;n++) sum+=Math.hypot(p[0][n]-target[0][n],p[1][n]-target[1][n]); return sum;
        }
        double[] s=stats(p);
        // C2=-sum|p|+alpha*std; C2N=-mean+beta*std/(mean+epsilon).
        return cfg.objective==DpcatConfig.Objective.C2_PAPER ? -p[0].length*s[0]+cfg.alpha*s[1] : -s[0]+cfg.beta*s[1]/(s[0]+cfg.epsilon);
    }
    public static int distance(int a,int b) { int d=Math.abs(a-b); return Math.min(d,32-d); }
    public static double meanDistance(int[] a,int[] b) {
        double sum=0; for(int i=0;i<a.length;i++) sum+=distance(a[i],b[i]); return sum/a.length;
    }
    private static double penalty(int[] codes,int[] previous) {
        if(previous==null)return 0; double sum=0; for(int i=0;i<codes.length;i++){int d=distance(codes[i],previous[i]);sum+=d*d;} return sum;
    }
    public static void checkCancelled(BooleanSupplier cancel) {
        if(Thread.currentThread().isInterrupted() || cancel.getAsBoolean()) throw new CancellationException("DPCAT cancelled");
    }
    public static Result optimize(DpcatMatrix g,int[] initial,int[] previous,double[][] target,DpcatConfig cfg,
            BooleanSupplier cancel,Consumer<String> progress) {
        cfg.validate(); g.pressure(initial);
        if(previous!=null)g.pressure(previous);
        if(cfg.initialization==DpcatConfig.Initialization.PREVIOUS_FRAME && previous==null)
            throw new IllegalArgumentException("Previous-frame initialization requires a previous frame");
        Result best=null; Random random=new Random(cfg.seed);
        for(int run=0;run<cfg.restarts;run++) {
            checkCancelled(cancel); int[] codes=(cfg.initialization==DpcatConfig.Initialization.PREVIOUS_FRAME?previous:initial).clone();
            if(run>0 || cfg.initialization==DpcatConfig.Initialization.RANDOM) for(int i=0;i<codes.length;i++)codes[i]=random.nextInt(32);
            Result r=search(g,codes,previous,target,cfg,cancel); r.restart=run+1;
            progress.accept("Restart "+r.restart+": "+r.initialCost+" -> "+r.finalCost+", sweeps="+r.sweeps);
            if(best==null || r.finalCost<best.finalCost)best=r;
        }
        return best;
    }
    private static Result search(DpcatMatrix g,int[] codes,int[] previous,double[][] target,DpcatConfig cfg,BooleanSupplier cancel) {
        int[] start=codes.clone(); double[][] p=g.pressure(codes), candidate=new double[2][g.count];
        double pen=penalty(codes,previous), cost=acousticCost(p,target,cfg)+cfg.lambdaPhase*pen;
        DpcatConfig.finite(cost,"Initial cost"); Result r=new Result(); r.initialCodes=start.clone(); r.initialCost=cost; r.initialCV=stats(p)[2]; r.sweepCosts.add(cost);
        for(int sweep=0;sweep<cfg.maxSweeps;sweep++) {
            boolean changed=false;
            for(int i=0;i<g.sources;i++) {
                checkCancelled(cancel); int old=codes[i], winner=old; double best=cost;
                int oldDist=previous==null?0:distance(old,previous[i]);
                for(int k=0;k<32;k++) {
                    if(k==old)continue;
                    increment(g,p,candidate,i,old,k);
                    int newDist=previous==null?0:distance(k,previous[i]);
                    double c=acousticCost(candidate,target,cfg)+cfg.lambdaPhase*(pen+newDist*newDist-oldDist*oldDist);
                    if(c<best){best=c;winner=k;}
                }
                if(winner!=old) {
                    increment(g,p,candidate,i,old,winner);
                    double[][] temp=p;p=candidate;candidate=temp;
                    int d=previous==null?0:distance(winner,previous[i]); pen+=d*d-oldDist*oldDist;
                    codes[i]=winner;cost=best;changed=true;r.acceptedChanges++;
                }
            }
            r.sweeps=sweep+1;r.sweepCosts.add(cost);
            if(!changed){r.converged=true;break;}
        }
        r.codes=codes;r.pressure=g.pressure(codes); // one final full sum detects/avoids accumulated roundoff
        r.finalCost=acousticCost(r.pressure,target,cfg)+cfg.lambdaPhase*penalty(codes,previous);
        double[] s=stats(r.pressure);r.mean=s[0];r.std=s[1];r.cv=s[2];
        for(int i=0;i<codes.length;i++)if(codes[i]!=start[i])r.changedTransducers++;
        return r;
    }
    /** p_new[n] = p[n] + G[n][i]*A[i]*(exp(j*new)-exp(j*old)); O(Ntarget). */
    public static void increment(DpcatMatrix g,double[][] p,double[][] out,int i,int old,int next) {
        double dc=COS[next]-COS[old],ds=SIN[next]-SIN[old];
        for(int n=0;n<g.count;n++) {
            out[0][n]=p[0][n]+g.real[n][i]*dc-g.imag[n][i]*ds;
            out[1][n]=p[1][n]+g.real[n][i]*ds+g.imag[n][i]*dc;
        }
    }
}
