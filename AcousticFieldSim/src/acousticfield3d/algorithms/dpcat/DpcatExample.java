package acousticfield3d.algorithms.dpcat;

import acousticfield3d.math.Vector3f;
import acousticfield3d.simulation.*;
import acousticfield3d.utils.FileUtils;
import java.nio.file.*;
import java.util.*;

/** Offline, reproducible example using a supplied existing .xml.gz. Never opens a serial port. */
public final class DpcatExample {
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Usage: DpcatExample input.xml.gz output-directory");
        Path out=Paths.get(args[1]);Files.createDirectories(out);
        Simulation s=(Simulation)FileUtils.readCompressedObject(Paths.get(args[0]).toFile());s.sortTransducers();s.sortAnimations();
        Vector3f center=new Vector3f();int n=0;for(Transducer t:s.transducers)if(t.getAmplitude()>0){center.addLocal(t.getTransform().getTranslation());n++;}
        if(n==0)throw new IllegalArgumentException("No active array");center.divideLocal(n);
        s.getReflector().setEnabled(true);s.getReflector().setPlaneY(center.y-.075);center.y-=.0375f;
        DpcatConfig cfg=new DpcatConfig();cfg.objective=DpcatConfig.Objective.C2_PAPER;cfg.alpha=10;
        List<DpcatTargets.Layout> two=DpcatTargets.trajectory(DpcatTargets.Mode.TWO_POINT,center,DpcatTargets.Axis.X,
            DpcatTargets.parseMillimetres(DpcatTargets.DEFAULT_SEPARATIONS),.008,.001,4);
        // Seed the simple C1 reference with a discrete two-pressure-target result; not a stability certification.
        DpcatSnapshot before=new DpcatSnapshot(s);DpcatSequence seed=DpcatSequence.generate(before,cfg,Collections.singletonList(two.get(0)),50,null,()->false,System.out::println);
        before.apply(seed.frames.get(0).result.codes,s);
        DpcatSnapshot snapshot=new DpcatSnapshot(s);cfg.objective=DpcatConfig.Objective.C1_COMPLEX;
        DpcatSequence a=DpcatSequence.generate(snapshot,cfg,two,50,null,()->false,System.out::println);
        DpcatExport.sequence(out.resolve("example_A_C1_sequence.csv"),a);DpcatExport.phases(out.resolve("example_A_frame0_phases.csv"),snapshot,a.frames.get(0).result.codes);
        s.animations.add(a.toAnimation(s));
        cfg.objective=DpcatConfig.Objective.C2_PAPER;
        for(DpcatTargets.Mode mode:new DpcatTargets.Mode[]{DpcatTargets.Mode.TWO_WIDE,DpcatTargets.Mode.POINT_WIDE_SINGLE}) {
            List<DpcatTargets.Layout> path=DpcatTargets.trajectory(mode,center,DpcatTargets.Axis.X,DpcatTargets.parseMillimetres(DpcatTargets.DEFAULT_SEPARATIONS),.008,.001,4);
            DpcatSequence seq=DpcatSequence.generate(snapshot,cfg,path,50,null,()->false,System.out::println);
            DpcatExport.sequence(out.resolve("example_"+mode+"_C2_sequence.csv"),seq);s.animations.add(seq.toAnimation(s));
        }
        for(int i=0;i<s.animations.getSize();i++)s.animations.getAt(i).setNumber(i);
        a.snapshot.apply(a.frames.get(0).result.codes,s);FileUtils.writeCompressedObject(out.resolve("example_192_DPCAT_75mm.xml.gz").toFile(),s);
        Files.writeString(out.resolve("EXAMPLE.txt"),"Offline example. Source: "+Paths.get(args[0]).getFileName()+"\nActive: "+n+"\nCenter (m): "+center
            +"\nReflector gap: 75 mm; X axis; dwell: 50 ms after Mega ACK.\nA: C2 discrete seed followed by C1 fixed reference; B/C: C2 alpha=10.\n"
            +"These are pressure-target candidates, not verified stable droplet traps. No hardware transmission was performed.\n");
        System.out.println("Example saved: "+out.toAbsolutePath());
    }
}
