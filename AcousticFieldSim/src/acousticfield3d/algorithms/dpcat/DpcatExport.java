package acousticfield3d.algorithms.dpcat;

import acousticfield3d.math.Vector3f;
import acousticfield3d.simulation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** CSV codes are the actual post-correction FPGA commands; phase_internal excludes correction. */
public final class DpcatExport {
    public static String report(DpcatSnapshot s,DpcatConfig c,DpcatSequence.Frame f) {
        DpcatOptimizer.Result r=f.result;ReflectorSettings ref=s.model.getReflector();
        int min=31,max=0;for(int v:s.hardwareCodes(r.codes))if(v!=32){min=Math.min(min,v);max=Math.max(max,v);}
        return String.format(Locale.US,"Active transducers: %d | Phase levels: 32%nReflector enabled: %s | Y: %.4f mm | magnitude: %.5f | phase: %.3f deg%n"
            +"Target count: %d | Objective: %s%nInitial cost: %.9g | Final cost: %.9g%nSweeps: %d | Changed transducers: %d | Accepted changes: %d | Converged: %s%n"
            +"Mean |p|: %.9g | Std |p|: %.9g | CV: %.9g -> %.9g%nPhase code min/max: %d/%d | Mean circular change: %.6f%n"
            +"Requested dwell: %.3f ms | Targets are pressure constraints; stable levitation NOT validated.%n",
            s.active.size(),ref.isEnabled(),ref.getPlaneY()*1000,ref.getMagnitude(),ref.getPhaseDegrees(),f.layout.positions.size(),c.objective,
            r.initialCost,r.finalCost,r.sweeps,r.changedTransducers,r.acceptedChanges,r.converged,r.mean,r.std,r.initialCV,r.cv,min,max,f.meanCodeChange,f.dwellMillis);
    }
    public static void phases(Path path,DpcatSnapshot s,int[] codes) throws IOException {
        int[] hardware=s.hardwareCodes(codes),optimized=new int[256];Arrays.fill(optimized,32);
        Transducer[] byChannel=new Transducer[256];for(Transducer t:s.model.transducers)byChannel[t.getOrderNumber()]=t;
        for(int i=0;i<codes.length;i++)optimized[s.active.get(i).getOrderNumber()]=codes[i];
        try(BufferedWriter w=Files.newBufferedWriter(path,StandardCharsets.UTF_8)) {
            w.write("index,phase_code,phase_internal,phase_correction,active,optimized_code,amplitude,power\n");
            for(int ch=0;ch<256;ch++) {
                Transducer t=byChannel[ch];boolean active=t!=null&&t.getAmplitude()>0;
                w.write(ch+","+hardware[ch]+","+(active?DpcatSnapshot.internal(optimized[ch]):t==null?0:t.getPhase())+","+(t==null?0:t.getPhaseCorrection())
                    +","+active+","+optimized[ch]+","+(t==null?0:t.getAmplitude())+","+(t==null?0:t.getPower())+"\n");
            }
        }
    }
    private static String targets(List<Vector3f> points) {
        StringJoiner j=new StringJoiner(";");for(Vector3f p:points)j.add(p.x*1000+":"+p.y*1000+":"+p.z*1000);return j.toString();
    }
    private static String complex(double[][] p) {
        StringJoiner j=new StringJoiner(";");for(int n=0;n<p[0].length;n++)j.add(p[0][n]+":"+p[1][n]);return j.toString();
    }
    private static String codes(DpcatSnapshot s,int[] values) {
        StringJoiner j=new StringJoiner(";");for(int i=0;i<values.length;i++)j.add(s.active.get(i).getOrderNumber()+":"+values[i]);return j.toString();
    }
    public static String metadata(DpcatConfig c,DpcatSequence.Frame f) {
        return "stage="+f.layout.stage+";axis="+f.layout.axis+";separation_m="+f.layout.separation+";width_m="+f.layout.width
            +";dwell_ms="+f.dwellMillis+";timestamp_ms="+f.timestampMillis+";objective="+c.objective+";cost="+f.result.finalCost
            +";mean="+f.result.mean+";std="+f.result.std+";cv="+f.result.cv+";targets_mm="+targets(f.layout.positions)
            +";c1_reference="+complex(f.complexTarget);
    }
    public static void sequence(Path path,DpcatSequence s) throws IOException {
        try(BufferedWriter w=Files.newBufferedWriter(path,StandardCharsets.UTF_8)) {
            w.write("frame,separation_mm,left_x_mm,right_x_mm,center_y_mm,center_z_mm,dwell_ms,cost,mean_pressure,std_pressure,cv,timestamp_ms,axis,stage,width_mm,left_z_mm,right_z_mm,mean_circular_code_change,targets_xyz_mm,c1_targets_re_im,objective,alpha,beta,lambda_phase,seed,sweeps,reflector_enabled,reflector_y_mm,reflection_magnitude,reflection_phase_deg,epsilon,c1_target_scale,max_sweeps,first_frame_restarts,first_frame_initialization,winning_restart,initial_optimized_codes,optimized_codes");
            for(int i=0;i<256;i++)w.write(",phase_"+i);w.write("\n");
            ReflectorSettings ref=s.snapshot.model.getReflector();DpcatConfig c=s.config;
            for(DpcatSequence.Frame f:s.frames) {
                DpcatTargets.Layout l=f.layout;double dx=l.axis==DpcatTargets.Axis.X?l.separation/2:0,dz=l.axis==DpcatTargets.Axis.Z?l.separation/2:0;
                w.write(f.index+","+l.separation*1000+","+(l.center.x-dx)*1000+","+(l.center.x+dx)*1000+","+l.center.y*1000+","+l.center.z*1000
                    +","+f.dwellMillis+","+f.result.finalCost+","+f.result.mean+","+f.result.std+","+f.result.cv+","+f.timestampMillis
                    +","+l.axis+","+l.stage+","+l.width*1000+","+(l.center.z-dz)*1000+","+(l.center.z+dz)*1000+","+f.meanCodeChange
                    +",\""+targets(l.positions)+"\",\""+complex(f.complexTarget)+"\","+c.objective+","+c.alpha+","+c.beta+","+c.lambdaPhase+","+c.seed+","+f.result.sweeps
                    +","+ref.isEnabled()+","+ref.getPlaneY()*1000+","+ref.getMagnitude()+","+ref.getPhaseDegrees()
                    +","+c.epsilon+","+c.targetScale+","+c.maxSweeps+","+c.restarts+","+c.initialization+","+f.result.restart
                    +",\""+codes(s.snapshot,f.initialCodes)+"\",\""+codes(s.snapshot,f.result.codes)+"\"");
                for(int v:s.snapshot.hardwareCodes(f.result.codes))w.write(","+v);w.write("\n");
            }
        }
        // Separate calibration/geometry snapshot supports later reproduction without embedding calibration in optimized phase.
        FileUtilsBridge.saveSnapshot(path.resolveSibling(path.getFileName()+".model.xml.gz"),s.snapshot.model);
    }
    private static final class FileUtilsBridge {
        static void saveSnapshot(Path p,Simulation s)throws IOException {acousticfield3d.utils.FileUtils.writeCompressedObject(p.toFile(),s);}
    }
}
