package acousticfield3d.algorithms.dpcat;

import acousticfield3d.math.Vector3f;
import acousticfield3d.simulation.*;
import acousticfield3d.utils.FileUtils;
import acousticfield3d.workers.DpcatPlayback;
import java.nio.file.*;
import java.util.*;

public class DpcatSequenceTest {
    private static void check(boolean c,String m){DpcatCoreTest.check(c,m);}
    public static void main(String[] args)throws Exception {
        Simulation s=DpcatCoreTest.fixture();DpcatSnapshot snap=new DpcatSnapshot(s);DpcatConfig c=new DpcatConfig();
        Vector3f center=new Vector3f(0,-.0375f,0);
        List<DpcatTargets.Layout> layouts=DpcatTargets.trajectory(DpcatTargets.Mode.TWO_POINT,center,DpcatTargets.Axis.X,
            DpcatTargets.parseMillimetres(DpcatTargets.DEFAULT_SEPARATIONS),.008,.001,4);
        DpcatSequence sequence=DpcatSequence.generate(snap,c,layouts,5,null,()->false,x->{});
        for(int i=0;i<sequence.frames.size();i++){
            DpcatSequence.Frame f=sequence.frames.get(i);int[] hw=snap.hardwareCodes(f.result.codes);
            check(hw.length==256,"256 channels every frame");for(int ch=0;ch<256;ch++)check(ch<64?hw[ch]==32:hw[ch]>=0&&hw[ch]<32,"legal complete state");
            if(i>0)check(Arrays.equals(f.initialCodes,sequence.frames.get(i-1).result.codes),"mandatory previous-frame warm start");
            check(Arrays.deepEquals(f.complexTarget,sequence.frames.get(0).complexTarget),"C1 fixed complex reference");
        }
        double warm=0,randomDistance=0;int[] prev=sequence.frames.get(0).result.codes;
        for(int i=1;i<sequence.frames.size();i++) {
            DpcatSequence.Frame f=sequence.frames.get(i);DpcatConfig random=new DpcatConfig();random.initialization=DpcatConfig.Initialization.RANDOM;random.seed=200+i;
            DpcatOptimizer.Result r=DpcatOptimizer.optimize(new DpcatMatrix(snap,f.layout.positions),prev,null,f.complexTarget,random,()->false,x->{});
            warm+=DpcatOptimizer.meanDistance(f.result.codes,prev);randomDistance+=DpcatOptimizer.meanDistance(r.codes,prev);prev=f.result.codes;
        }
        warm/=7;randomDistance/=7;check(warm<randomDistance,"fixture warm start smoother than random");
        System.out.printf(Locale.US,"Mean circular difference warm %.6f vs independent random %.6f (codes)%n",warm,randomDistance);
        for(DpcatTargets.Mode mode:DpcatTargets.Mode.values())for(DpcatTargets.Axis axis:DpcatTargets.Axis.values()) {
            List<DpcatTargets.Layout> path=DpcatTargets.trajectory(mode,center,axis,new double[]{.014,.010},.008,.001,2);
            c.objective=DpcatConfig.Objective.C2_NORMALIZED;c.maxSweeps=2;
            DpcatSequence wide=DpcatSequence.generate(snap,c,path,10,null,()->false,x->{});
            for(DpcatSequence.Frame f:wide.frames)check(Double.isFinite(f.result.finalCost),"wide finite objective");
            if(mode==DpcatTargets.Mode.POINT_WIDE_SINGLE)check(wide.frames.get(wide.frames.size()-1).layout.positions.size()==1,"mode C ends at single point");
        }
        DpcatTargets.Layout wide=new DpcatTargets.Layout("wide",center,DpcatTargets.Axis.X,0,.008,.001,true);
        check(wide.positions.size()==9,"8 mm width 1 mm spacing -> 9 positions");
        Animation a=sequence.toAnimation(s);int count=a.keyFrames.getSize();a.interpolate();check(a.keyFrames.getSize()==count,"DPCAT interpolation disabled");
        check(a.createCopy().isDpcatDiscrete(),"copy retains discrete flag");
        Path dir=Paths.get("verification/dpcat");Files.createDirectories(dir);
        s.animations.add(a);Path gz=dir.resolve("sequence_roundtrip.xml.gz");FileUtils.writeCompressedObject(gz.toFile(),s);
        Simulation loaded=(Simulation)FileUtils.readCompressedObject(gz.toFile());loaded.sortTransducers();loaded.sortAnimations();Animation restored=loaded.animations.getAt(0);
        check(restored.isDpcatDiscrete(),".gz discrete flag");check(restored.keyFrames.getSize()==8,".gz frame count");
        check(restored.keyFrames.getAt(0).getDpcatDwellMillis()==5,".gz dwell");
        check(restored.keyFrames.getAt(0).getDpcatMetadata().contains("targets_mm="),".gz metadata");
        if(!snap.fingerprint.equals(DpcatSnapshot.fingerprint(loaded))) {
            String other=DpcatSnapshot.fingerprint(loaded);int diff=0;while(diff<Math.min(other.length(),snap.fingerprint.length())&&other.charAt(diff)==snap.fingerprint.charAt(diff))diff++;
            System.out.println("Fingerprint mismatch at "+diff+" original="+snap.fingerprint.substring(Math.max(0,diff-30),Math.min(snap.fingerprint.length(),diff+120))+" loaded="+other.substring(Math.max(0,diff-30),Math.min(other.length(),diff+120)));
        }
        restored.applyAtFrame(1,loaded);
        for(Transducer t:loaded.transducers)check(t.getPhaseCorrection()==s.transducers.get(t.getOrderNumber()).getPhaseCorrection(),".gz correction");
        DpcatExport.sequence(dir.resolve("test_sequence.csv"),sequence);DpcatExport.phases(dir.resolve("test_phases.csv"),snap,sequence.frames.get(0).result.codes);
        check(Files.readAllLines(dir.resolve("test_phases.csv")).size()==257,"phase CSV 256 rows plus header");
        String timing=DpcatPlayback.play(a,s,null,()->false,i->a.applyAtFrame(i,s),x->{});
        check(timing.split("\n").length==9,"all preview frames timed");Files.writeString(dir.resolve("test_preview_timing.csv"),timing);
        // Defaults of unmodified animations keep legacy behavior after save/load.
        Animation legacy=new Animation();check(!legacy.isDpcatDiscrete(),"legacy default unchanged");
        s.transducers.get(3).setAmplitude(1);s.transducers.get(100).setAmplitude(0);
        DpcatSnapshot dynamic=new DpcatSnapshot(s);int[] hw=dynamic.hardwareCodes(dynamic.initialCodes);
        check(dynamic.active.size()==192&&hw[3]<32&&hw[100]==32,"dynamic active selection not hardcoded");
        System.out.println("PASS DPCAT trajectory, warm start, wide/X/Z, persistence, CSV and preview timing");
    }
}
