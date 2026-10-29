package acousticfield3d.gui.panels;

import acousticfield3d.algorithms.SimplePhaseAlgorithms;
import acousticfield3d.gui.MainForm;
import acousticfield3d.math.Vector3f;
import acousticfield3d.scene.Entity;
import acousticfield3d.scene.MeshEntity;
import acousticfield3d.simulation.ReflectorSettings;
import acousticfield3d.simulation.Transducer;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.text.ParseException;
import java.util.Locale;
import javax.swing.*;

/** Programmatic panel; independent of NetBeans-generated form blocks. */
public class ReflectorPanel extends JPanel {
    private final MainForm mf;
    private final JCheckBox enabled = new JCheckBox("Enable reflection");
    private final JCheckBox visible = new JCheckBox("Show plate", true);
    private final JSpinner planeY = spinner(-40, -2000, 2000, 0.1);
    private final JSpinner gap = spinner(40, 0.1, 2000, 0.1);
    private final JSpinner magnitude = spinner(1, 0, 1, 0.01);
    private final JSpinner phase = spinner(0, -360, 360, 1);
    private final JSpinner width = spinner(180, 1, 4000, 1);
    private final JSpinner depth = spinner(180, 1, 4000, 1);
    private final JTextArea status = new JTextArea(7, 22);
    private boolean loading;

    public ReflectorPanel(MainForm mf) {
        this.mf = mf;
        setLayout(new BorderLayout(4, 4));
        JPanel rows = new JPanel(new GridLayout(0, 2, 3, 5));
        rows.add(enabled); rows.add(visible);
        row(rows, "Plane Y (mm)", planeY); row(rows, "Gap from array (mm)", gap);
        JButton below = new JButton("Place below (-Y)"), above = new JButton("Place above (+Y)");
        rows.add(below); rows.add(above);
        row(rows, "Pressure |R|", magnitude); row(rows, "Reflection phase (deg)", phase);
        row(rows, "Visual width X (mm)", width); row(rows, "Visual depth Z (mm)", depth);
        JButton apply = new JButton("Apply / refresh"), slice = new JButton("Add XY slice");
        rows.add(apply); rows.add(slice);
        JButton focus = new JButton("Focus selected point (pressure)");
        JPanel top = new JPanel(new BorderLayout(4, 6));
        top.add(rows, BorderLayout.NORTH); top.add(focus, BorderLayout.SOUTH);
        status.setEditable(false); status.setLineWrap(true); status.setWrapStyleWord(true); status.setOpaque(false);
        add(top, BorderLayout.NORTH); add(status, BorderLayout.CENTER);
        enabled.addActionListener(e -> applySettings()); visible.addActionListener(e -> applySettings());
        for (JSpinner sp : new JSpinner[]{planeY, magnitude, phase, width, depth})
            sp.addChangeListener(e -> { if (!loading) applySettings(); });
        apply.addActionListener(e -> applySettings());
        below.addActionListener(e -> place(-1)); above.addActionListener(e -> place(1));
        slice.addActionListener(e -> addSlice()); focus.addActionListener(e -> focusPoint());
        reloadFromSimulation();
    }
    private static JSpinner spinner(double value, double min, double max, double step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        s.setEditor(new JSpinner.NumberEditor(s, "0.###")); return s;
    }
    private static void row(JPanel p, String label, JComponent field) { p.add(new JLabel(label)); p.add(field); }
    private static double value(JSpinner s) { return ((Number)s.getValue()).doubleValue(); }
    public void reloadFromSimulation() {
        loading = true;
        ReflectorSettings r = mf.simulation.getReflector();
        enabled.setSelected(r.isEnabled()); visible.setSelected(r.isVisible());
        planeY.setValue(r.getPlaneY()*1000); magnitude.setValue(r.getMagnitude());
        phase.setValue(r.getPhaseDegrees()); width.setValue(r.getVisualWidth()*1000); depth.setValue(r.getVisualDepth()*1000);
        loading = false; updateStatus();
    }
    private boolean applySettings() {
        if (loading) return false;
        try {
            loading = true;
            for (JSpinner sp : new JSpinner[]{planeY, magnitude, phase, width, depth}) sp.commitEdit();
            ReflectorSettings r = new ReflectorSettings();
            r.setEnabled(enabled.isSelected()); r.setVisible(visible.isSelected());
            r.setPlaneY(value(planeY)/1000); r.setMagnitude(value(magnitude)); r.setPhaseDegrees(value(phase));
            r.setVisualWidth(value(width)/1000); r.setVisualDepth(value(depth)/1000);
            synchronized (mf) { mf.simulation.setReflector(r); }
            mf.needUpdate(); updateStatus(); return true;
        } catch (ParseException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid reflector setting", JOptionPane.ERROR_MESSAGE);
            return false;
        } finally { loading = false; }
    }
    private double arrayY() {
        double sum = 0; int count = 0;
        for (Transducer t : mf.simulation.transducers) if (t.getAmplitude() != 0) {
            sum += t.getTransform().getTranslation().y; count++;
        }
        return count == 0 ? 0 : sum/count;
    }
    private void place(int sign) {
        try { gap.commitEdit(); } catch (ParseException ex) { return; }
        if (mf.simulation.transducers.isEmpty()) { JOptionPane.showMessageDialog(this, "Load your array first."); return; }
        planeY.setValue(arrayY()*1000 + sign*value(gap)); applySettings();
    }
    private void updateStatus() {
        ReflectorSettings r = mf.simulation.getReflector();
        String caution = "";
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (Transducer t : mf.simulation.transducers) if (t.getAmplitude() != 0) {
            double y = t.getTransform().getTranslation().y; min=Math.min(min,y); max=Math.max(max,y);
        }
        if (r.isEnabled() && r.getPlaneY() >= min && r.getPlaneY() <= max)
            caution = "\nWARNING: plane intersects the active array. Move it outside the array.";
        status.setText(String.format(Locale.US,
            "%s | Array mean Y: %.2f mm\nActual Y gap: %.2f mm\nInfinite flat plane; width/depth affect appearance only.\n"
            + "Use fields on the array side of the plate.\nHeight changes the field, but does not re-optimize phases automatically.%s",
            r.isEnabled()?"Reflection ON":"Reflection OFF", arrayY()*1000, Math.abs(r.getPlaneY()-arrayY())*1000, caution));
    }
    private void focusPoint() {
        if (!applySettings()) return;
        Entity target = null; int count = 0;
        for (Entity e : mf.selection) if ((e.getTag() & Entity.TAG_CONTROL_POINT) != 0) { target=e; count++; }
        if (count != 1) { JOptionPane.showMessageDialog(this, "Select exactly one control point in the scene."); return; }
        synchronized (mf) {
            SimplePhaseAlgorithms.focus(mf.simulation.transducers, target.getTransform().getTranslation(), mf.simulation);
        }
        mf.needUpdate();
    }
    private void addSlice() {
        if (!applySettings()) return;
        Vector3f center = new Vector3f(); int n = 0;
        for (Transducer t : mf.simulation.transducers) if (t.getAmplitude() != 0) { center.addLocal(t.getTransform().getTranslation()); n++; }
        if (n == 0) { JOptionPane.showMessageDialog(this, "Load an array with active channels first."); return; }
        center.divideLocal(n);
        ReflectorSettings r = mf.simulation.getReflector();
        float height = (float)Math.abs(center.y-r.getPlaneY());
        center.y = (float)((center.y+r.getPlaneY())/2);
        MeshEntity slice = mf.rtSlicePanel.createSlice();
        slice.getTransform().getTranslation().set(center);
        slice.getTransform().getRotation().set(0,0,0,1);
        slice.getTransform().getScale().set((float)r.getVisualWidth(), Math.max(height,0.001f),1);
        synchronized (mf) { mf.scene.getEntities().add(slice); }
        mf.needUpdate();
    }
}
