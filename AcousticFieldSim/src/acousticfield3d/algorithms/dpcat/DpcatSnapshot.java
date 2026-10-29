package acousticfield3d.algorithms.dpcat;

import acousticfield3d.simulation.*;
import acousticfield3d.math.Vector3f;
import java.util.*;

/** Isolates worker calculations from GUI changes. Never writes amplitude or correction. */
public final class DpcatSnapshot {
    public static final int LEVELS = 32, CHANNELS = 256, OFF = 32;
    public final Simulation model = new Simulation();
    public final Simulation original;
    public final List<Transducer> active = new ArrayList<>();
    public final String fingerprint;
    public final int[] initialCodes;
    private final List<Transducer> originals;

    public DpcatSnapshot(Simulation s) {
        original = s; originals = new ArrayList<>(s.transducers);
        DpcatConfig.positive(s.getMediumSpeed(), "Sound speed");
        model.setMediumSpeed(s.getMediumSpeed());
        ReflectorSettings r = s.getReflector(), copy = new ReflectorSettings();
        copy.setEnabled(r.isEnabled()); copy.setPlaneY(r.getPlaneY()); copy.setMagnitude(r.getMagnitude());
        copy.setPhaseDegrees(r.getPhaseDegrees()); model.setReflector(copy);
        boolean[] used = new boolean[CHANNELS];
        for (Transducer t : s.transducers) {
            int ch = t.getOrderNumber();
            if (ch < 0 || ch >= CHANNELS || used[ch]) throw new IllegalArgumentException("Duplicate/out-of-range FPGA address: " + ch);
            used[ch] = true;
            DpcatConfig.finite(t.getAmplitude(), "Amplitude"); DpcatConfig.finite(t.getPhase(), "Phase");
            DpcatConfig.finite(t.getPhaseCorrection(), "Correction");
            DpcatConfig.positive(t.getFrequency(), "Frequency"); DpcatConfig.positive(t.getApperture(), "Aperture");
            DpcatConfig.positive(t.getPower(), "Power");
            Vector3f p = t.getTransform().getTranslation();
            DpcatConfig.finite(p.x,"X"); DpcatConfig.finite(p.y,"Y"); DpcatConfig.finite(p.z,"Z");
            Transducer c = new Transducer();
            c.setOrderNumber(ch); c.setDriverPinNumber(t.getDriverPinNumber());
            c.getTransform().getTranslation().set(p); c.getTransform().getRotation().set(t.getTransform().getRotation());
            c.setFrequency(t.getFrequency()); c.setApperture(t.getApperture()); c.setPower(t.getPower());
            c.setAmplitude(t.getAmplitude()); c.setPhase(t.getPhase()); c.setPhaseCorrection(t.getPhaseCorrection());
            model.transducers.add(c); if (c.getAmplitude() > 0) active.add(c);
        }
        if (active.isEmpty()) throw new IllegalArgumentException("No active transducers (amplitude > 0)");
        initialCodes = new int[active.size()];
        for (int i=0;i<initialCodes.length;i++) initialCodes[i] = Transducer.calcDiscPhase(active.get(i).getPhase(), LEVELS);
        fingerprint = fingerprint(s);
    }
    /** Excludes optimized phase, includes calibration, geometry and all propagation inputs. */
    public static String fingerprint(Simulation s) {
        StringBuilder b = new StringBuilder().append(s.getMediumSpeed());
        ReflectorSettings r=s.getReflector();
        b.append('|').append(r.isEnabled()).append('|').append(r.getPlaneY()).append('|').append(r.getMagnitude()).append('|').append(r.getPhaseDegrees());
        List<Transducer> ordered=new ArrayList<>(s.transducers);
        ordered.sort(Comparator.comparingInt(Transducer::getOrderNumber)); // JavaBeans XML may reconstruct list ordering differently.
        for(Transducer t:ordered) b.append(';').append(t.getOrderNumber()).append(',').append(t.getAmplitude())
            .append(',').append(t.getPhaseCorrection()).append(',').append(t.getFrequency()).append(',').append(t.getPower())
            .append(',').append(t.getApperture()).append(',').append(t.getTransform().getTranslation())
            .append(',').append(t.getTransform().getRotation());
        return b.toString();
    }
    public void checkCurrent(Simulation s) {
        if (s != original || !fingerprint.equals(fingerprint(s)) || !originals.equals(s.transducers))
            throw new IllegalStateException("Array/reflector/calibration changed. Generate a new DPCAT result.");
    }
    public static float internal(int code) {
        if (code < 0 || code >= LEVELS) throw new IllegalArgumentException("Phase code outside 0..31");
        return code / 16f; // phase radians / pi = 2*code/32 (exact in float)
    }
    public void validateCodes(int[] codes) {
        if(codes.length != active.size()) throw new IllegalArgumentException("Active phase count mismatch");
        for(int k:codes) internal(k);
    }
    public int[] hardwareCodes(int[] codes) {
        validateCodes(codes);
        int[] out=new int[CHANNELS]; Arrays.fill(out,OFF);
        for(int i=0;i<codes.length;i++) {
            Transducer t=active.get(i);
            out[t.getOrderNumber()]=Transducer.calcDiscPhase(internal(codes[i])+t.getPhaseCorrection(),LEVELS);
        }
        return out;
    }
    public void apply(int[] codes, Simulation s) {
        checkCurrent(s); validateCodes(codes);
        Map<Integer,Integer> byChannel = new HashMap<>();
        for(int i=0;i<codes.length;i++) byChannel.put(active.get(i).getOrderNumber(),codes[i]);
        for(Transducer t:s.transducers) if(t.getAmplitude()>0) t.setPhase(internal(byChannel.get(t.getOrderNumber())));
    }
    public AnimKeyFrame keyFrame(int[] codes) {
        validateCodes(codes); AnimKeyFrame k=new AnimKeyFrame();
        Map<Integer,Integer> byChannel=new HashMap<>();
        for(int i=0;i<codes.length;i++) byChannel.put(active.get(i).getOrderNumber(),codes[i]);
        for(int i=0;i<originals.size();i++) {
            Transducer t=originals.get(i), saved=model.transducers.get(i);
            k.getTransAmplitudes().put(t,saved.getAmplitude());
            k.getTransPhases().put(t,saved.getAmplitude()>0?internal(byChannel.get(saved.getOrderNumber())):saved.getPhase());
        }
        return k;
    }
}
