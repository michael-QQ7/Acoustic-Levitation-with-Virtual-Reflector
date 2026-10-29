package acousticfield3d.algorithms;

import acousticfield3d.math.Vector3f;
import acousticfield3d.simulation.ReflectorSettings;
import acousticfield3d.simulation.Simulation;
import acousticfield3d.simulation.Transducer;

/**
 * Ultraino's sinc aperture model plus a single infinite-plane image source.
 * Phasor convention exp(i*k*r+i*phase); pressure reflection R=rho*exp(i*theta).
 * Images are mathematical sources only, never extra hardware channels.
 */
public final class AcousticPropagation {
    private AcousticPropagation() {}

    /** Immutable geometry snapshot, reusable for a spatial derivative stencil. */
    public static final class Source {
        public final double x, y, z, nx, ny, nz, k, aperture;
        public Source(Transducer t, double speed) {
            Vector3f p = t.getTransform().getTranslation();
            Vector3f n = t.getTransform().getRotation().mult(Vector3f.UNIT_Y, new Vector3f());
            x = p.x; y = p.y; z = p.z;
            double norm = n.length();
            nx = n.x / norm; ny = n.y / norm; nz = n.z / norm;
            k = 2.0 * Math.PI * t.getFrequency() / speed;
            aperture = t.getApperture();
        }
    }

    public static void transfer(Transducer t, Simulation s, double px, double py, double pz,
            double[] out) {
        transfer(new Source(t, s.getMediumSpeed()), s.getReflector(), px, py, pz, true, out);
    }

    public static void transfer(Source t, ReflectorSettings r, double px, double py, double pz,
            boolean directivity, double[] out) {
        out[0] = 0; out[1] = 0;
        add(t, t.x, t.y, t.z, t.nx, t.ny, t.nz, px, py, pz, 1, 0, directivity, out);
        if (r.isEnabled() && r.getMagnitude() != 0) {
            // Reflect both source position and its radiation normal about y=H.
            add(t, t.x, 2 * r.getPlaneY() - t.y, t.z, t.nx, -t.ny, t.nz,
                    px, py, pz, r.getMagnitude(), Math.toRadians(r.getPhaseDegrees()), directivity, out);
        }
    }

    private static void add(Source t, double sx, double sy, double sz,
            double nx, double ny, double nz, double px, double py, double pz,
            double gain, double phase, boolean directivity, double[] out) {
        double dx = px - sx, dy = py - sy, dz = pz - sz;
        // Only regularizes the singular source location; do not interpret fields there physically.
        double distance = Math.max(1e-9, Math.sqrt(dx*dx + dy*dy + dz*dz));
        double dir = 1;
        if (directivity) {
            double cosine = Math.max(-1, Math.min(1, (dx*nx + dy*ny + dz*nz) / distance));
            double q = 0.5 * t.k * t.aperture * Math.sqrt(Math.max(0, 1 - cosine*cosine));
            dir = Math.abs(q) < 1e-8 ? 1 : Math.sin(q) / q;
        }
        double amplitude = gain * dir / distance;
        double angle = t.k * distance + phase;
        out[0] += amplitude * Math.cos(angle);
        out[1] += amplitude * Math.sin(angle);
    }
}
