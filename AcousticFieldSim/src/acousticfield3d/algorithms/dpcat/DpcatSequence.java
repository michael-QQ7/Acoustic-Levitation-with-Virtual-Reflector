package acousticfield3d.algorithms.dpcat;

import acousticfield3d.simulation.*;
import java.util.*;
import java.util.function.*;

public final class DpcatSequence {
    public static final class Frame {
        public int index;
        public double dwellMillis, timestampMillis, meanCodeChange;
        public DpcatTargets.Layout layout;
        public DpcatOptimizer.Result result;
        public int[] initialCodes;
        public double[][] complexTarget;
    }
    public final DpcatSnapshot snapshot;
    public final DpcatConfig config;
    public final List<Frame> frames=new ArrayList<>();
    public DpcatSequence(DpcatSnapshot snapshot,DpcatConfig config) {this.snapshot=snapshot;this.config=copy(config);}

    public static DpcatSequence generate(DpcatSnapshot snapshot,DpcatConfig config,List<DpcatTargets.Layout> layouts,
            double dwellMillis,int[] previous,BooleanSupplier cancel,Consumer<String> progress) {
        config.validate();DpcatConfig.positive(dwellMillis,"Dwell ms");
        if(layouts.isEmpty())throw new IllegalArgumentException("No frames");
        if(config.initialization==DpcatConfig.Initialization.PREVIOUS_FRAME && previous==null)
            throw new IllegalArgumentException("No previous optimized frame; use Current or Random first");
        DpcatSequence sequence=new DpcatSequence(snapshot,config);
        int[] initial=config.initialization==DpcatConfig.Initialization.PREVIOUS_FRAME?previous.clone():snapshot.initialCodes.clone();
        double[][] firstReference=new DpcatMatrix(snapshot,layouts.get(0).positions).pressure(initial);
        int[] continuity=previous;int number=0;double time=0;
        for(DpcatTargets.Layout layout:layouts) {
            DpcatOptimizer.checkCancelled(cancel);DpcatMatrix g=new DpcatMatrix(snapshot,layout.positions);
            double[][] target=DpcatTargets.reference(firstReference,g.count,config.targetScale);
            DpcatConfig cfg=copy(config);
            if(number>0){cfg.initialization=DpcatConfig.Initialization.CURRENT;cfg.restarts=1;} // mandatory warm start, no fresh randomness
            Frame f=new Frame();f.index=number++;f.layout=layout;f.dwellMillis=dwellMillis;f.timestampMillis=time;time+=dwellMillis;
            f.initialCodes=initial.clone();f.complexTarget=target;
            progress.accept("Frame "+f.index+" "+layout.stage+", separation="+(layout.separation*1000)+" mm, targets="+g.count);
            f.result=DpcatOptimizer.optimize(g,initial,continuity,target,cfg,cancel,progress);
            f.initialCodes=f.result.initialCodes.clone();
            f.meanCodeChange=continuity==null?0:DpcatOptimizer.meanDistance(f.result.codes,continuity);
            sequence.frames.add(f);initial=f.result.codes.clone();continuity=initial;
            progress.accept(DpcatExport.report(snapshot,config,f));
        }
        return sequence;
    }
    private static DpcatConfig copy(DpcatConfig c) {
        DpcatConfig n=new DpcatConfig();n.objective=c.objective;n.initialization=c.initialization;n.maxSweeps=c.maxSweeps;
        n.restarts=c.restarts;n.seed=c.seed;n.alpha=c.alpha;n.beta=c.beta;n.epsilon=c.epsilon;n.lambdaPhase=c.lambdaPhase;n.targetScale=c.targetScale;return n;
    }
    public Animation toAnimation(Simulation current) {
        snapshot.checkCurrent(current);Animation a=new Animation();a.setName("DPCAT "+config.objective+" (discrete)");
        a.setDpcatDiscrete(true);a.setDpcatModelFingerprint(snapshot.fingerprint);
        for(Frame f:frames) {
            AnimKeyFrame k=snapshot.keyFrame(f.result.codes);k.setNumber(f.index);k.setDuration(1);
            k.setDpcatDwellMillis(f.dwellMillis);k.setDpcatMetadata(DpcatExport.metadata(config,f));a.keyFrames.add(k);
        }return a;
    }
}
