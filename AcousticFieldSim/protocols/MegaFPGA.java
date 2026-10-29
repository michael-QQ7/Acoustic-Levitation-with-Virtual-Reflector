package acousticfield3d.protocols;

import acousticfield3d.simulation.Transducer;
import acousticfield3d.utils.uartComm.SerialComms;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** MegaCommandSender text protocol. The Mega commits each phases command itself. */
public class MegaFPGA extends DeviceConnection {
    public static final int PC_BAUD = 250000;
    public static final int FPGA_BAUD = 250000;
    public static final int CHANNELS = 256;
    public static final int PHASE_OFF = 32;

    interface Transport {
        void open(int port, SerialComms.Listener listener) throws IOException;
        void write(byte[] data) throws IOException;
        void close();
    }

    private static final class SerialTransport implements Transport {
        private SerialComms connection;
        public void open(int port, SerialComms.Listener listener) throws IOException {
            connection = new SerialComms(port, PC_BAUD, listener);
            if (!connection.isConnected()) throw new IOException("Cannot open Mega serial port");
        }
        public void write(byte[] data) throws IOException {
            if (connection == null || !connection.isConnected()) throw new IOException("Serial disconnected");
            connection.write(data);
            connection.flush();
            if (!connection.isConnected()) throw new IOException("Serial write failed");
        }
        public void close() {
            if (connection != null && connection.isConnected()) connection.disconnect();
            connection = null;
        }
    }

    private final Transport transport;
    private final long resetMillis;
    private final long timeoutMillis;
    // At most one unsent snapshot; rapid GUI updates replace the pending frame.
    private static final class Transmission {
        final byte[] bytes;
        final Object owner;
        final CompletableFuture<Long> acknowledged = new CompletableFuture<>();
        Transmission(byte[] bytes, Object owner) { this.bytes=bytes; this.owner=owner; }
    }
    private final ArrayBlockingQueue<Transmission> pending = new ArrayBlockingQueue<>(1);
    private volatile Transmission inFlight;
    private Object sequenceOwner;
    private final ArrayBlockingQueue<String> replies = new ArrayBlockingQueue<>(32);
    private final StringBuilder line = new StringBuilder();
    private volatile boolean active;
    private volatile boolean ready;
    private volatile String status = "Disconnected";
    private Consumer<String> statusListener = value -> {};
    private Thread worker;

    public MegaFPGA() { this(new SerialTransport(), 2000, 2000); }

    MegaFPGA(Transport transport, long resetMillis, long timeoutMillis) {
        this.transport = transport;
        this.resetMillis = resetMillis;
        this.timeoutMillis = timeoutMillis;
    }

    @Override public int getSpeed() { return PC_BAUD; }
    @Override public int getDivs() { return PHASE_OFF; }
    public boolean isReady() { return ready; }
    public String getStatus() { return status; }
    public void setStatusListener(Consumer<String> listener) { statusListener = listener; }
    private void status(String value) { status = value; statusListener.accept(value); }

    @Override public void connect(int port) {
        if (worker != null) throw new IllegalStateException("Use a new MegaFPGA connection to reconnect");
        active = true;
        status("Waiting for Mega reset...");
        worker = new Thread(() -> runConnection(port), "MegaFPGA serial");
        worker.setDaemon(true);
        worker.start();
    }

    private void runConnection(int port) {
        try {
            transport.open(port, this);
            Thread.sleep(resetMillis);
            // A command/response handshake also supports boards with auto-reset disabled.
            request("version\n".getBytes(StandardCharsets.US_ASCII), null);
            ready = true;
            status("Mega ready (250000 / 250000)");
            while (active) {
                Transmission frame = pending.take();
                inFlight = frame;
                status("Sending phases...");
                request(frame.bytes, "phases ok");
                frame.acknowledged.complete(System.nanoTime());
                inFlight = null;
                status("Mega: phases ok");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            if (active) status("Error: " + ex.getMessage() + "; reconnect");
        } finally {
            ready = false;
            active = false;
            IOException stopped = new IOException("Mega connection stopped: " + status);
            if (inFlight != null) inFlight.acknowledged.completeExceptionally(stopped);
            inFlight = null;
            Transmission queued;
            while ((queued=pending.poll()) != null) queued.acknowledged.completeExceptionally(stopped);
            synchronized (this) { sequenceOwner=null; }
            transport.close();
        }
    }

    private void request(byte[] command, String expected) throws IOException, InterruptedException {
        if (!active) throw new InterruptedException();
        replies.clear();
        transport.write(command);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (active) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            String reply = replies.poll(remaining, TimeUnit.NANOSECONDS);
            if (reply == null) break;
            if (expected == null ? reply.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?") : expected.equals(reply)) return;
            if (reply.equals("unknown cmd") || reply.toLowerCase(java.util.Locale.ROOT).startsWith("error")) {
                throw new IOException("Mega: " + reply);
            }
            // Ignore boot banner and unrelated lines, never treat them as an ACK.
        }
        if (!active) throw new InterruptedException();
        throw new IOException("No Mega " + (expected == null ? "version" : expected) + " reply");
    }

    @Override public synchronized void rxMsg(byte[] data, int len) {
        for (int i = 0; i < len; i++) {
            int c = data[i] & 255;
            if (c == '\n') {
                replies.offer(line.toString().trim());
                line.setLength(0);
            } else if (c != '\r') {
                if (line.length() >= 256) line.setLength(0);
                line.append((char)c);
            }
        }
    }

    /** Order number is the raw FPGA address; unused channels are always OFF. */
    public byte[] encodePattern(List<Transducer> transducers) {
        int[] phases = new int[CHANNELS];
        boolean[] used = new boolean[CHANNELS];
        Arrays.fill(phases, PHASE_OFF);
        for (Transducer t : transducers) {
            int channel = t.getOrderNumber() - number;
            if (channel < 0 || channel >= CHANNELS) continue; // another device's range
            if (used[channel]) throw new IllegalArgumentException("Duplicate FPGA channel: " + channel);
            used[channel] = true;
            if (!Float.isFinite(t.getAmplitude()) || !Float.isFinite(t.getPhase())
                    || !Float.isFinite(t.getPhaseCorrection())) {
                throw new IllegalArgumentException("Non-finite amplitude/phase at channel " + channel);
            }
            phases[channel] = t.getAmplitude() <= 0 ? PHASE_OFF : t.getDiscPhase(PHASE_OFF);
        }
        StringBuilder command = new StringBuilder("phases=");
        for (int i = 0; i < CHANNELS; i++) {
            if (i != 0) command.append(',');
            command.append(phases[i]);
        }
        return command.append('\n').toString().getBytes(StandardCharsets.US_ASCII);
    }

    @Override public synchronized void sendPattern(List<Transducer> transducers) {
        if (!active) return;
        if (sequenceOwner != null) { status("DPCAT playback owns this connection; stop playback before manual send"); return; }
        try {
            byte[] command = encodePattern(transducers); // snapshot before returning to GUI
            Transmission frame=new Transmission(command,null);
            if (!pending.offer(frame)) {
                Transmission replaced=pending.poll();
                if(replaced!=null)replaced.acknowledged.cancel(false);
                pending.offer(frame);
            }
        } catch (IllegalArgumentException ex) {
            status("Invalid pattern: " + ex.getMessage());
        }
    }

    /** Reliable playback uses the SAME encoder, worker, command and Mega ACK parser.
     * A lease prevents unrelated GUI sends from replacing sequence frames.
     */
    public synchronized Object beginDiscreteSequence() {
        if(!active || !ready)throw new IllegalStateException("Connect MegaFPGA and wait for Mega ready");
        if(sequenceOwner!=null)throw new IllegalStateException("A discrete sequence is already playing");
        if(!pending.isEmpty() || inFlight!=null)throw new IllegalStateException("Wait for the current Mega transmission to finish");
        sequenceOwner=new Object();return sequenceOwner;
    }
    public synchronized CompletableFuture<Long> sendDiscretePattern(Object owner,List<Transducer> transducers) {
        if(owner==null || owner!=sequenceOwner || !active || !ready)throw new IllegalStateException("DPCAT connection is no longer ready");
        Transmission frame=new Transmission(encodePattern(transducers),owner);
        if(!pending.offer(frame))throw new IllegalStateException("Wait for the previous frame ACK before sending the next");
        return frame.acknowledged;
    }
    public synchronized void endDiscreteSequence(Object owner) {
        if(owner!=sequenceOwner)return;
        Transmission frame=pending.peek();
        if(frame!=null && frame.owner==owner && pending.remove(frame))frame.acknowledged.cancel(false);
        sequenceOwner=null;
    }

    // Sending "switch" after "phases=" would swap back to the previous FPGA buffer.
    @Override public void switchBuffers() {}

    @Override public void disconnect() {
        active = false;
        ready = false;
        Transmission queued;
        while((queued=pending.poll())!=null)queued.acknowledged.completeExceptionally(new IOException("Disconnected"));
        if (worker != null) {
            worker.interrupt();
            try { worker.join(3000); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }
        status("Disconnected");
    }
}
