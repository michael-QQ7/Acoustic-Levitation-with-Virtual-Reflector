package acousticfield3d.algorithms.dpcat;

/** All distances in the algorithm are metres; phases are 32-state codes. */
public final class DpcatConfig {
    public enum Objective { C1_COMPLEX, C2_PAPER, C2_NORMALIZED }
    public enum Initialization { CURRENT, RANDOM, PREVIOUS_FRAME }
    public Objective objective = Objective.C1_COMPLEX;
    public Initialization initialization = Initialization.CURRENT;
    public int maxSweeps = 10, restarts = 1;
    public long seed = 192;
    public double alpha = 1, beta = 1, epsilon = 1e-12, lambdaPhase = 0, targetScale = 1;

    public void validate() {
        if (objective == null || initialization == null) throw new IllegalArgumentException("Select objective and initialization");
        if (maxSweeps < 1 || maxSweeps > 10000 || restarts < 1 || restarts > 100)
            throw new IllegalArgumentException("Sweeps: 1..10000; restarts: 1..100");
        nonnegative(alpha, "alpha"); nonnegative(beta, "beta"); nonnegative(lambdaPhase, "lambdaPhase");
        positive(epsilon, "epsilon"); positive(targetScale, "C1 target scale");
    }
    public static void finite(double v, String label) {
        if (!Double.isFinite(v)) throw new IllegalArgumentException(label + " must be finite");
    }
    public static void positive(double v, String label) { finite(v,label); if (v <= 0) throw new IllegalArgumentException(label+" must be > 0"); }
    public static void nonnegative(double v, String label) { finite(v,label); if (v < 0) throw new IllegalArgumentException(label+" must be >= 0"); }
}
