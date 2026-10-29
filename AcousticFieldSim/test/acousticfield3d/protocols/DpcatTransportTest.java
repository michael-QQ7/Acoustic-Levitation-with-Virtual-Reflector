package acousticfield3d.protocols;

import acousticfield3d.algorithms.dpcat.*;
import acousticfield3d.math.Vector3f;
import acousticfield3d.simulation.*;
import acousticfield3d.workers.DpcatPlayback;
import java.util.*;
import java.util.concurrent.*;

public class DpcatTransportTest {
    static void check(boolean c,String m){if(!c)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception {
        MegaFPGATest.Fake fake=new MegaFPGATest.Fake();MegaFPGA d=new MegaFPGA(fake,0,500);d.connect(1);
        MegaFPGATest.await(d::isReady,"ready");Object owner=d.beginDiscreteSequence();
        fake.autoAck=false;
        CompletableFuture<Long> first=d.sendDiscretePattern(owner,Collections.singletonList(MegaFPGATest.source(64,0,1)));
        final MegaFPGATest.Fake firstFake=fake;
        MegaFPGATest.await(()->firstFake.writes.size()==2,"first frame");
        d.sendPattern(Collections.singletonList(MegaFPGATest.source(64,1,1)));
        check(fake.writes.size()==2&&!first.isDone(),"no competing GUI frame / no false ACK");
        fake.reply("boot banner\n");check(!first.isDone(),"unrelated reply not ACK");fake.reply("phases ok\n");first.get(1,TimeUnit.SECONDS);
        fake.autoAck=true;
        for(int i=1;i<=20;i++)d.sendDiscretePattern(owner,Collections.singletonList(MegaFPGATest.source(64,i/16f,1))).get(1,TimeUnit.SECONDS);
        check(fake.writes.size()==22,"all twenty-one patterns transmitted");
        for(int i=0;i<=20;i++)check(MegaFPGATest.values(fake.writes.get(i+1).getBytes(java.nio.charset.StandardCharsets.US_ASCII))[64]==i,"ordered discrete pattern "+i);
        d.endDiscreteSequence(owner);d.disconnect();
        MegaFPGATest.Fake silent=new MegaFPGATest.Fake();silent.autoAck=false;d=new MegaFPGA(silent,0,80);d.connect(1);MegaFPGATest.await(d::isReady,"timeout ready");
        owner=d.beginDiscreteSequence();CompletableFuture<Long> failed=d.sendDiscretePattern(owner,Collections.singletonList(MegaFPGATest.source(64,0,1)));
        try{failed.get(1,TimeUnit.SECONDS);throw new AssertionError("missing ACK must fail");}catch(ExecutionException expected){}
        d.endDiscreteSequence(owner);d.disconnect();
        Simulation s=DpcatCoreTest.fixture();DpcatConfig c=new DpcatConfig();c.maxSweeps=1;
        DpcatSequence sequence=DpcatSequence.generate(new DpcatSnapshot(s),c,DpcatTargets.trajectory(DpcatTargets.Mode.TWO_POINT,
            new Vector3f(0,-.0375f,0),DpcatTargets.Axis.X,new double[]{.014,.012,.010},.008,.001,2),5,null,()->false,x->{});
        Animation a=sequence.toAnimation(s);fake=new MegaFPGATest.Fake();d=new MegaFPGA(fake,0,500);d.connect(1);MegaFPGATest.await(d::isReady,"play ready");
        String timing=DpcatPlayback.play(a,s,d,()->false,i->a.applyAtFrame(i,s),x->{});
        check(fake.writes.size()==4,"playback no missing frames");
        for(String row:timing.split("\n"))if(!row.startsWith("frame")) {
            String[] cells=row.split(",");double hold=Double.parseDouble(cells[5])-Double.parseDouble(cells[4]);check(hold>=4.9,"minimum hold after ACK");
        }
        final int sentBeforeCancel=fake.writes.size();
        java.util.concurrent.atomic.AtomicBoolean cancel=new java.util.concurrent.atomic.AtomicBoolean();
        try {
            DpcatPlayback.play(a,s,d,cancel::get,i->a.applyAtFrame(i,s),x->cancel.set(true));
            throw new AssertionError("Playback cancellation must stop the loop");
        } catch(CancellationException expected) {}
        check(fake.writes.size()==sentBeforeCancel+1,"cancel sends no later frames");
        Object nextLease=d.beginDiscreteSequence();d.endDiscreteSequence(nextLease);
        int beforeStale=fake.writes.size();s.getReflector().setPlaneY(-.070);
        try {
            DpcatPlayback.play(a,s,d,()->false,i->a.applyAtFrame(i,s),x->{});
            throw new AssertionError("Stale reflector sequence must be rejected");
        } catch(IllegalStateException expected) {
            check(expected.getMessage().contains("model changed"),"stale rejection reason");
        }
        check(fake.writes.size()==beforeStale,"no hardware frame after reflector change");
        s.getReflector().setPlaneY(-.075);
        Animation invalid=a.createCopy();invalid.keyFrames.getAt(0).getTransPhases().put(s.transducers.get(64),.123f);
        try {
            DpcatPlayback.play(invalid,s,d,()->false,i->invalid.applyAtFrame(i,s),x->{});
            throw new AssertionError("Off-grid phase must be rejected");
        } catch(IllegalStateException expected) {
            check(expected.getMessage().contains("32 optimized states"),"off-grid rejection reason");
        }
        check(fake.writes.size()==beforeStale,"no off-grid pattern sent");
        nextLease=d.beginDiscreteSequence();d.endDiscreteSequence(nextLease);
        d.disconnect();System.out.println("PASS DPCAT reliable queue, ownership, exact ordering, ACK timeout, dwell, cancellation, stale model and off-grid rejection (mock transport)");
    }
}
