package acousticfield3d.algorithms.dpcat;

import acousticfield3d.algorithms.AcousticPropagation;
import acousticfield3d.math.Vector3f;
import acousticfield3d.simulation.Transducer;
import java.util.*;

/** G[n][i]*A[i], calculated once, using the existing direct+image transfer only. */
public final class DpcatMatrix {
    public final double[][] real, imag;
    public final List<Vector3f> targets;
    public final int sources, count;
    public DpcatMatrix(DpcatSnapshot snapshot, List<Vector3f> positions) {
        if(positions.isEmpty() || positions.size()>1024) throw new IllegalArgumentException("Target count: 1..1024");
        targets=new ArrayList<>(); for(Vector3f v:positions) {
            DpcatConfig.finite(v.x,"Target X"); DpcatConfig.finite(v.y,"Target Y"); DpcatConfig.finite(v.z,"Target Z"); targets.add(v.clone());
        }
        sources=snapshot.active.size(); count=targets.size(); real=new double[count][sources]; imag=new double[count][sources];
        double[] g=new double[2];
        for(int i=0;i<sources;i++) {
            Transducer t=snapshot.active.get(i);
            AcousticPropagation.Source source=new AcousticPropagation.Source(t,snapshot.model.getMediumSpeed());
            for(int n=0;n<count;n++) {
                Vector3f p=targets.get(n);
                AcousticPropagation.transfer(source,snapshot.model.getReflector(),p.x,p.y,p.z,true,g);
                double amplitude=t.getAmplitude()*t.getPower();
                real[n][i]=g[0]*amplitude; imag[n][i]=g[1]*amplitude;
                DpcatConfig.finite(real[n][i],"Transfer Re"); DpcatConfig.finite(imag[n][i],"Transfer Im");
            }
        }
    }
    public double[][] pressure(int[] codes) {
        if(codes.length!=sources) throw new IllegalArgumentException("Phase count mismatch");
        double[][] p=new double[2][count];
        for(int i=0;i<sources;i++) {
            DpcatSnapshot.internal(codes[i]); double a=2*Math.PI*codes[i]/32, c=Math.cos(a), s=Math.sin(a);
            for(int n=0;n<count;n++) { p[0][n]+=real[n][i]*c-imag[n][i]*s; p[1][n]+=real[n][i]*s+imag[n][i]*c; }
        }
        return p;
    }
}
