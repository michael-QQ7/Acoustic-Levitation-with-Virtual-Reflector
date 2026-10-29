package acousticfield3d.workers;

import acousticfield3d.algorithms.dpcat.*;
import acousticfield3d.protocols.MegaFPGA;
import acousticfield3d.simulation.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** One complete frame per ACK. Dwell is a minimum hold AFTER Mega ACK, not a real-time period. */
public final class DpcatPlayback {
    public static void validate(Animation a,Simulation s,AnimKeyFrame key) {
        if(!a.isDpcatDiscrete())throw new IllegalArgumentException("Not a DPCAT animation");
        if(!Objects.equals(a.getDpcatModelFingerprint(),DpcatSnapshot.fingerprint(s)))
            throw new IllegalStateException("DPCAT model changed: regenerate after array/reflector/calibration edits");
        if(key.getTransPhases().size()!=s.transducers.size() || key.getTransAmplitudes().size()!=s.transducers.size())
            throw new IllegalStateException("Incomplete DPCAT frame");
        DpcatConfig.positive(key.getDpcatDwellMillis(),"Frame dwell");
        for(Transducer t:s.transducers) {
            Float amp=key.getTransAmplitudes().get(t), phase=key.getTransPhases().get(t);
            if(amp==null || phase==null || !Float.isFinite(phase) || Float.floatToIntBits(amp)!=Float.floatToIntBits(t.getAmplitude()))
                throw new IllegalStateException("DPCAT amplitude/channel state changed");
            if(amp>0 && (phase<0 || phase>=2 || Math.abs(phase*16-Math.round(phase*16))>1e-6))
                throw new IllegalStateException("DPCAT phase must be one of 32 optimized states");
        }
    }
    /** Returns timing CSV; ack_ms means Mega parser ACK, NOT an FPGA/physical-field ACK. */
    public static String play(Animation a,Simulation s,MegaFPGA device,BooleanSupplier stop,
            IntConsumer applyOnUi,Consumer<String> log) throws Exception {
        if(a.keyFrames.getSize()==0)throw new IllegalArgumentException("Empty sequence");
        Object lease=null;long start=System.nanoTime();
        StringBuilder timing=new StringBuilder("frame,mode,requested_dwell_ms,submit_ms,mega_ack_ms,hold_end_ms\n");
        try {
            if(device!=null)lease=device.beginDiscreteSequence();
            for(int i=0;i<a.keyFrames.getSize();i++) {
                DpcatOptimizer.checkCancelled(stop);AnimKeyFrame key=a.keyFrames.getAt(i);
                validate(a,s,key);applyOnUi.accept(i);DpcatOptimizer.checkCancelled(stop);
                long submitted=System.nanoTime(),ack=submitted;
                if(device!=null) {
                    // Encode immutable frame values, so unrelated scene phase edits cannot alter the submitted pattern.
                    List<Transducer> frame=new ArrayList<>();
                    for(Transducer t:s.transducers) {
                        Transducer c=new Transducer();c.setOrderNumber(t.getOrderNumber());c.setAmplitude(key.getTransAmplitudes().get(t));
                        c.setPhase(key.getTransPhases().get(t));c.setPhaseCorrection(t.getPhaseCorrection());frame.add(c);
                    }
                    CompletableFuture<Long> done=device.sendDiscretePattern(lease,frame);
                    while(true) {
                        DpcatOptimizer.checkCancelled(stop);
                        try {ack=done.get(50,TimeUnit.MILLISECONDS);break;}catch(TimeoutException wait){}
                    }
                }
                long deadline=ack+(long)(key.getDpcatDwellMillis()*1e6);
                while(System.nanoTime()<deadline) {
                    DpcatOptimizer.checkCancelled(stop);
                    long remaining=deadline-System.nanoTime();if(remaining>0)TimeUnit.NANOSECONDS.sleep(Math.min(remaining,20_000_000));
                }
                double end=(System.nanoTime()-start)/1e6;
                timing.append(i).append(',').append(device==null?"preview":"mega_ack").append(',').append(key.getDpcatDwellMillis()).append(',')
                    .append((submitted-start)/1e6).append(',').append(device==null?"":(ack-start)/1e6).append(',').append(end).append('\n');
                log.accept(String.format(Locale.US,"Frame %d complete | requested hold %.3f ms | submit %.3f ms | %s %.3f ms",
                    i,key.getDpcatDwellMillis(),(submitted-start)/1e6,device==null?"preview":"Mega ACK",(ack-start)/1e6));
            }
            return timing.toString();
        } finally {if(device!=null && lease!=null)device.endDiscreteSequence(lease);}
    }
}
