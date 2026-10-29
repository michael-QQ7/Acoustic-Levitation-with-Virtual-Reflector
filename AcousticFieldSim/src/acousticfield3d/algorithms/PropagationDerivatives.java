package acousticfield3d.algorithms;

import acousticfield3d.simulation.ReflectorSettings;

/** Double-precision, centered seven-point stencils; orders 0..3, including mixed derivatives. */
public final class PropagationDerivatives {
    private PropagationDerivatives() {}
    public static final int[][] ORDERS = {
        {0,0,0}, {1,0,0}, {0,1,0}, {0,0,1},
        {1,1,0}, {1,0,1}, {0,1,1}, {2,0,0}, {0,2,0}, {0,0,2},
        {3,0,0}, {0,3,0}, {0,0,3}, {1,2,0}, {1,0,2},
        {2,1,0}, {0,1,2}, {2,0,1}, {0,2,1}
    };
    private static final double[][] W = {
        {0,0,0,1,0,0,0},
        {-1.0/60,3.0/20,-3.0/4,0,3.0/4,-3.0/20,1.0/60},
        {1.0/90,-3.0/20,3.0/2,-49.0/18,3.0/2,-3.0/20,1.0/90},
        {1.0/8,-1,13.0/8,0,-13.0/8,1,-1.0/8}
    };

    public static double[][] evaluate(AcousticPropagation.Source source, ReflectorSettings reflector,
            double x, double y, double z, double h, boolean directivity) {
        if (!(h > 0) || !Double.isFinite(h)) throw new IllegalArgumentException("Derivative step must be positive");
        double[][] result = new double[ORDERS.length][2];
        double[][] samples = new double[343][2];
        for (int ix=0; ix<7; ix++) for (int iy=0; iy<7; iy++) for (int iz=0; iz<7; iz++) {
            // No requested derivative uses all three nonzero offsets.
            if (ix != 3 && iy != 3 && iz != 3) continue;
            AcousticPropagation.transfer(source, reflector, x+(ix-3)*h, y+(iy-3)*h,
                    z+(iz-3)*h, directivity, samples[(ix*7+iy)*7+iz]);
        }
        for (int d=0; d<ORDERS.length; d++) {
            int[] o = ORDERS[d];
            for (int ix=0; ix<7; ix++) for (int iy=0; iy<7; iy++) for (int iz=0; iz<7; iz++) {
                double weight = W[o[0]][ix] * W[o[1]][iy] * W[o[2]][iz];
                if (weight == 0) continue;
                double[] v = samples[(ix*7+iy)*7+iz];
                result[d][0] += weight * v[0]; result[d][1] += weight * v[1];
            }
            double scale = Math.pow(h, o[0]+o[1]+o[2]);
            result[d][0] /= scale; result[d][1] /= scale;
        }
        return result;
    }
}
