package acousticfield3d.protocols;

import acousticfield3d.simulation.Transducer;
import acousticfield3d.utils.uartComm.SerialComms;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** No physical serial port is opened. Exercises the real worker and ACK parser. */
public class MegaFPGATest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        check(condition.getAsBoolean(), message);
    }
    static Transducer source(int channel, float phase, float amplitude) {
        Transducer t = new Transducer();
        t.setOrderNumber(channel); t.setPhase(phase); t.setAmplitude(amplitude);
        return t;
    }
    static String text(byte[] data) { return new String(data, StandardCharsets.US_ASCII); }
    static int[] values(byte[] data) {
        String command = text(data);
        check(command.startsWith("phases=") && command.endsWith("\n"), "line framing");
        return Arrays.stream(command.substring(7).trim().split(",")).mapToInt(Integer::parseInt).toArray();
    }
    static class Fake implements MegaFPGA.Transport {
        final List<String> writes = new CopyOnWriteArrayList<>();
        volatile SerialComms.Listener listener;
        volatile boolean closed;
        volatile boolean autoAck = true;
        volatile boolean versionReply = true;
        public void open(int port, SerialComms.Listener listener) { this.listener = listener; }
        void reply(String value) {
            // Deliver each byte separately, including CRLF.
            for (byte b : value.getBytes(StandardCharsets.US_ASCII)) listener.rxMsg(new byte[]{b}, 1);
        }
        public void write(byte[] data) {
            String command = text(data); writes.add(command);
            if (command.equals("version\n") && versionReply) reply("MegaCommandSender ready\r\n1.1\r\n");
            else if (command.startsWith("phases=") && autoAck) reply("phases ok\r\n");
        }
        public void close() { closed = true; }
    }
    public static void main(String[] args) throws Exception {
        MegaFPGA encoder = new MegaFPGA();
        check(encoder.getSpeed() == 250000 && encoder.getDivs() == 32 && MegaFPGA.FPGA_BAUD == 250000, "baud/divisions");
        List<Transducer> sources = new ArrayList<>();
        for (int i = 64; i < 256; i++) sources.add(source(i, (i % 32) / 16f, 1));
        int[] v = values(encoder.encodePattern(sources));
        check(v.length == 256, "fixed channel count");
        for (int i = 0; i < 256; i++) check(v[i] == (i < 64 ? 32 : i % 32), "192 active/64 off at " + i);
        Transducer t = source(255, -.125f, 1); t.setPhaseCorrection(.0625f);
        v = values(encoder.encodePattern(Arrays.asList(t, source(3, 1, 0))));
        check(v[255] == 31 && v[3] == 32 && v[0] == 32, "negative phase, correction and off");
        encoder.setNumber(256);
        check(values(encoder.encodePattern(Arrays.asList(source(256, 1, .5f))))[0] == 16, "offset and binary amplitude");
        encoder.setNumber(0);
        try { encoder.encodePattern(Arrays.asList(source(0, 0, 1), source(0, 1, 1))); throw new AssertionError("duplicate accepted"); }
        catch (IllegalArgumentException expected) {}
        try { encoder.encodePattern(Arrays.asList(source(0, Float.NaN, 1))); throw new AssertionError("NaN accepted"); }
        catch (IllegalArgumentException expected) {}

        Fake fake = new Fake();
        MegaFPGA device = new MegaFPGA(fake, 10, 1000);
        device.connect(1);
        await(device::isReady, "version handshake");
        device.sendPattern(sources); device.switchBuffers();
        await(() -> device.getStatus().equals("Mega: phases ok"), "fragmented ACK");
        check(fake.writes.size() == 2 && !fake.writes.contains("switch\n"), "one commit per frame");
        fake.autoAck = false;
        device.sendPattern(Collections.singletonList(source(0, 0, 1)));
        await(() -> fake.writes.size() == 3, "first in-flight frame");
        for (int i = 1; i <= 20; i++) device.sendPattern(Collections.singletonList(source(0, i / 16f, 1)));
        check(fake.writes.size() == 3, "no new frame before ACK");
        fake.reply("phases ok\n");
        await(() -> fake.writes.size() == 4, "pending frame");
        check(values(fake.writes.get(3).getBytes(StandardCharsets.US_ASCII))[0] == 20, "latest snapshot wins");
        fake.reply("phases ok\n");
        device.disconnect();
        check(fake.closed && !device.isReady(), "disconnect closes worker/port");

        Fake silent = new Fake(); silent.autoAck = false;
        MegaFPGA timedOut = new MegaFPGA(silent, 0, 80);
        timedOut.connect(1); await(timedOut::isReady, "timeout fixture connected");
        timedOut.sendPattern(sources);
        await(() -> silent.closed, "timeout closes port");
        check(timedOut.getStatus().contains("No Mega phases ok"), "timeout visible");
        timedOut.sendPattern(sources);
        check(silent.writes.size() == 2, "no sends after lost ACK");
        timedOut.disconnect();

        Fake wrong = new Fake(); wrong.versionReply = false;
        MegaFPGA wrongProtocol = new MegaFPGA(wrong, 0, 80);
        wrongProtocol.connect(1); wrongProtocol.sendPattern(sources);
        await(() -> wrong.closed, "missing handshake closes port");
        check(wrong.writes.size() == 1 && !wrongProtocol.isReady(), "no phase data without handshake");
        wrongProtocol.disconnect();

        Fake boot = new Fake();
        MegaFPGA duringReset = new MegaFPGA(boot, 500, 100);
        duringReset.connect(1); duringReset.sendPattern(sources); duringReset.disconnect();
        check(boot.writes.isEmpty() && boot.closed, "disconnect during reset cancels pending output");
        System.out.println("ALL PASS: MegaFPGA encoding, handshake, ACK flow, coalescing, timeout and disconnect");
    }
}
