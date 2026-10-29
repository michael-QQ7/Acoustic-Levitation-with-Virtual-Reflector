package acousticfield3d.simulation;

/** JavaBean persisted inside the simulation XML. SI units except phase in degrees. */
public class ReflectorSettings {
    private boolean enabled;
    private boolean visible = true;
    private double planeY = -0.040;
    private double magnitude = 1.0;
    private double phaseDegrees;
    private double visualWidth = 0.180;
    private double visualDepth = 0.180;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { visible = v; }
    public double getPlaneY() { return planeY; }
    public void setPlaneY(double v) { finite(v); planeY = v; }
    public double getMagnitude() { return magnitude; }
    public void setMagnitude(double v) {
        finite(v);
        if (v < 0 || v > 1) throw new IllegalArgumentException("Pressure |R| must be in [0, 1]");
        magnitude = v;
    }
    public double getPhaseDegrees() { return phaseDegrees; }
    public void setPhaseDegrees(double v) { finite(v); phaseDegrees = v; }
    public double getVisualWidth() { return visualWidth; }
    public void setVisualWidth(double v) { positive(v); visualWidth = v; }
    public double getVisualDepth() { return visualDepth; }
    public void setVisualDepth(double v) { positive(v); visualDepth = v; }
    private static void finite(double v) {
        if (!Double.isFinite(v)) throw new IllegalArgumentException("A finite number is required");
    }
    private static void positive(double v) {
        finite(v);
        if (v <= 0) throw new IllegalArgumentException("Size must be positive");
    }
}
