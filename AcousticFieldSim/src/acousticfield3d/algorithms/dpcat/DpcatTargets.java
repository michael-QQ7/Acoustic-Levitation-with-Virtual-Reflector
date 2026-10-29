package acousticfield3d.algorithms.dpcat;

import acousticfield3d.math.Vector3f;
import java.util.*;

/** Target construction is deliberately separate from optimizer and propagation. */
public final class DpcatTargets {
    public enum Mode { TWO_POINT, TWO_WIDE, SINGLE_WIDE, POINT_WIDE_SINGLE }
    public enum Axis { X, Z }
    public static final String DEFAULT_SEPARATIONS="14, 12, 10, 9.6, 9.4, 9.2, 9.0, 8.8";
    public static final class Layout {
        public final String stage;
        public final Vector3f center;
        public final Axis axis;
        public final double separation, width;
        public final List<Vector3f> positions;
        public Layout(String stage,Vector3f c,Axis axis,double separation,double width,double spacing,boolean single) {
            this.stage=stage;this.center=c.clone();this.axis=axis;this.separation=separation;this.width=width;
            DpcatConfig.nonnegative(separation,"Separation");DpcatConfig.nonnegative(width,"Width");DpcatConfig.positive(spacing,"Target spacing");
            positions=new ArrayList<>();
            if(single || separation==0)line(positions,c,axis,0,width,spacing);
            else {line(positions,c,axis,-separation/2,width,spacing);line(positions,c,axis,separation/2,width,spacing);}
        }
    }
    private static void line(List<Vector3f> out,Vector3f center,Axis axis,double offset,double width,double spacing) {
        int intervals=width==0?0:(int)Math.ceil(width/spacing);
        if(intervals>511)throw new IllegalArgumentException("Too many targets: increase spacing");
        for(int j=0;j<=intervals;j++) {
            double shift=offset+(intervals==0?0:-width/2+width*j/intervals);
            Vector3f p=center.clone();if(axis==Axis.X)p.x+=shift;else p.z+=shift;
            // Overlapping wide regions must not double-weight identical positions.
            boolean duplicate=false;for(Vector3f v:out)if(v.distance(p)<1e-8f){duplicate=true;break;}
            if(!duplicate)out.add(p);
        }
    }
    public static double[] parseMillimetres(String list) {
        String[] values=list.trim().split("[,;\\s]+");
        if(values.length==0 || values.length>500)throw new IllegalArgumentException("Separation list: 1..500 entries");
        double[] d=new double[values.length];
        for(int i=0;i<d.length;i++){d[i]=Double.parseDouble(values[i])/1000;DpcatConfig.nonnegative(d[i],"Separation");}
        return d;
    }
    public static String linearList(double initialMm,double finalMm,int steps) {
        DpcatConfig.nonnegative(initialMm,"Initial separation");DpcatConfig.nonnegative(finalMm,"Final separation");
        if(steps<2||steps>500)throw new IllegalArgumentException("Steps: 2..500");
        StringBuilder b=new StringBuilder();for(int i=0;i<steps;i++){
            if(i>0)b.append(", ");b.append(String.format(Locale.US,"%.6f",initialMm+(finalMm-initialMm)*i/(steps-1)));
        }return b.toString();
    }
    public static List<Layout> trajectory(Mode mode,Vector3f center,Axis axis,double[] separations,double width,double spacing,int transitionSteps) {
        if(separations.length==0||separations.length>500)throw new IllegalArgumentException("Empty/oversized trajectory");
        if(transitionSteps<1||transitionSteps>50)throw new IllegalArgumentException("Transition steps: 1..50");
        List<Layout> out=new ArrayList<>();
        for(double d:separations)out.add(new Layout(mode==Mode.TWO_WIDE?"two-wide":"approach",center,axis,
            mode==Mode.SINGLE_WIDE?0:d,(mode==Mode.TWO_WIDE||mode==Mode.SINGLE_WIDE)?width:0,spacing,mode==Mode.SINGLE_WIDE));
        if(mode==Mode.POINT_WIDE_SINGLE) {
            double d=separations[separations.length-1], bridgeWidth=Math.max(width,d);
            for(int i=1;i<=transitionSteps;i++)out.add(new Layout("widen",center,axis,d,bridgeWidth*i/transitionSteps,spacing,false));
            double connectedWidth=d+bridgeWidth;
            out.add(new Layout("central-wide",center,axis,0,connectedWidth,spacing,true));
            for(int i=1;i<=transitionSteps;i++)out.add(new Layout("contract",center,axis,0,connectedWidth*(1-(double)i/transitionSteps),spacing,true));
        }
        return out;
    }
    /** Simple C1 reference, NOT a reproduction of the paper's dual-active-array construction.
     * Capture first-frame current complex pressure, scale it, carry that reference forward.
     * When topology changes, nearest normalized sample-index mapping preserves reference phases.
     */
    public static double[][] reference(double[][] first,int count,double scale) {
        double[][] out=new double[2][count];
        for(int n=0;n<count;n++) {
            int j=count==1?first[0].length/2:(int)Math.round((double)n*(first[0].length-1)/(count-1));
            out[0][n]=first[0][j]*scale;out[1][n]=first[1][j]*scale;
        }return out;
    }
}
