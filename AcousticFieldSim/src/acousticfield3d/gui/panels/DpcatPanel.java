package acousticfield3d.gui.panels;

import acousticfield3d.algorithms.dpcat.*;
import acousticfield3d.gui.MainForm;
import acousticfield3d.math.Vector3f;
import acousticfield3d.protocols.MegaFPGA;
import acousticfield3d.scene.*;
import acousticfield3d.simulation.*;
import acousticfield3d.workers.DpcatPlayback;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import javax.swing.*;

/** Independent programmatic Swing UI. Existing NetBeans-generated forms remain intact. */
public final class DpcatPanel extends JPanel {
    private final MainForm mf;
    private final JComboBox<String> objective=new JComboBox<>(new String[]{"C1 Complex Pressure","C2 Paper","C2 Normalized"});
    private final JComboBox<String> initialization=new JComboBox<>(new String[]{"Current phase","Random phase","Previous frame"});
    private final JComboBox<String> mode=new JComboBox<>(new String[]{"A: Two Point Traps","B: Two Wide Traps","Single Wide Trap","C: Point -> Wide -> Single"});
    private final JComboBox<String> axis=new JComboBox<>(new String[]{"X","Z"}), slicePlane=new JComboBox<>(new String[]{"XY","XZ"});
    private final JTextField sweeps=text("10"), restarts=text("1"), alpha=text("1"), beta=text("1"), lambda=text("0"), seed=text("192"), scale=text("1");
    private final JTextField cx=text("0"),cy=text("0"),cz=text("0"),initial=text("14"),last=text("8.8"),steps=text("8"),width=text("8"),spacing=text("1"),dwell=text("50"),transition=text("4");
    private final JTextArea separations=new JTextArea(DpcatTargets.DEFAULT_SEPARATIONS,2,32),log=new JTextArea(10,80);
    private final JLabel reflector=new JLabel(),state=new JLabel("Ready. Load your array and set the target center.");
    private final JComboBox<String> frameSelector=new JComboBox<>();
    private final List<JButton> actions=new ArrayList<>();
    private final List<MeshEntity> markers=new ArrayList<>();
    private MeshEntity slice;
    private SwingWorker<?,String> worker;
    private volatile boolean stopRequested;
    private DpcatSequence sequence;
    private int[] previous;
    private String previousFingerprint;
    private boolean loadingFrames;
    private String timing="";

    public DpcatPanel(MainForm mf) {
        this.mf=mf;setLayout(new BorderLayout(8,8));setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        JPanel optimization=new JPanel(new GridLayout(0,2,6,5));optimization.setBorder(BorderFactory.createTitledBorder("32-state direct search"));
        row(optimization,"Objective",objective);row(optimization,"Phase levels",new JLabel("32 (fixed)"));row(optimization,"Max sweeps",sweeps);
        row(optimization,"Restarts (first frame)",restarts);row(optimization,"Initialization",initialization);row(optimization,"Random seed",seed);
        row(optimization,"Alpha (C2 Paper)",alpha);row(optimization,"Beta (C2 Normalized)",beta);row(optimization,"Phase continuity lambda",lambda);
        row(optimization,"C1 reference amplitude scale",scale);row(optimization,"C1 reference",new JLabel("First current complex field; fixed reference"));
        JPanel trajectory=new JPanel(new GridLayout(0,2,6,5));trajectory.setBorder(BorderFactory.createTitledBorder("Target geometry / trajectory (mm)"));
        row(trajectory,"Mode",mode);row(trajectory,"Merge axis",axis);row(trajectory,"Center X / Y / Z (mm)",triple(cx,cy,cz));
        button(trajectory,"Use selected point",()->useCenter(true));button(trajectory,"Use array/plate midpoint",()->useCenter(false));
        row(trajectory,"Initial / final separation (mm)",triple(initial,last,null));row(trajectory,"Steps",steps);
        button(trajectory,"Build linear separation list",()->separations.setText(DpcatTargets.linearList(number(initial),number(last),integer(steps))));
        button(trajectory,"Restore reference list",()->separations.setText(DpcatTargets.DEFAULT_SEPARATIONS));
        row(trajectory,"Wide width / target spacing (mm)",triple(width,spacing,null));row(trajectory,"Mode C transition steps",transition);
        row(trajectory,"Frame dwell after ACK (ms)",dwell);row(trajectory,"Preview slice",slicePlane);
        JPanel columns=new JPanel(new GridLayout(1,2,8,0));columns.add(optimization);columns.add(trajectory);
        JPanel north=new JPanel(new BorderLayout(6,6));north.add(columns,BorderLayout.CENTER);
        JPanel context=new JPanel(new BorderLayout(3,3));context.add(reflector,BorderLayout.NORTH);
        separations.setLineWrap(true);separations.setWrapStyleWord(true);
        JPanel list=new JPanel(new BorderLayout());list.add(new JLabel("Separation list (mm; authoritative for Generate Sequence):"),BorderLayout.NORTH);
        list.add(new JScrollPane(separations),BorderLayout.CENTER);context.add(list,BorderLayout.CENTER);north.add(context,BorderLayout.SOUTH);add(north,BorderLayout.NORTH);
        JPanel controls=new JPanel(new GridLayout(0,3,5,5));
        button(controls,"Optimize Current",()->optimize(false));button(controls,"Generate Coalescence Sequence",()->optimize(true));button(controls,"Add Sequence To Animation",this::addAnimation);
        button(controls,"Preview Field",()->preview(true));button(controls,"Preview Discrete Sequence",()->playGenerated(false));button(controls,"Send Current Frame",this::sendCurrent);
        button(controls,"Play Sequence To Mega / FPGA",()->playGenerated(true));button(controls,"Export DPCAT Phase CSV",()->export(false));button(controls,"Export DPCAT Sequence CSV",()->export(true));
        button(controls,"Export Playback Timing CSV",this::exportTiming);button(controls,"Refresh Reflector Status",this::refreshStatus);
        JButton cancel=new JButton("Stop / Cancel");controls.add(cancel);cancel.addActionListener(e->stopRequested=true);
        JPanel middle=new JPanel(new BorderLayout(5,5));middle.add(controls,BorderLayout.NORTH);
        JPanel select=new JPanel(new BorderLayout(4,4));select.add(new JLabel("Frame"),BorderLayout.WEST);select.add(frameSelector,BorderLayout.CENTER);select.add(state,BorderLayout.SOUTH);middle.add(select,BorderLayout.CENTER);
        JLabel note=new JLabel("<html>Pressure targets do not certify stable droplet traps. Verify force / stability and the field before experiments.<br>"
            +"Discrete playback: no interpolation; one full pattern per Mega ACK. Actual periods include serial/ACK time.</html>");middle.add(note,BorderLayout.SOUTH);
        JPanel lower=new JPanel(new BorderLayout(5,5));lower.add(middle,BorderLayout.NORTH);log.setEditable(false);log.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));lower.add(new JScrollPane(log),BorderLayout.CENTER);
        add(lower,BorderLayout.CENTER);frameSelector.addActionListener(e->{if(!loadingFrames&&sequence!=null&&!isBusy())preview(false);});refreshStatus();
    }
    private static JTextField text(String s){return new JTextField(s,7);}
    private static void row(JPanel p,String label,JComponent input){p.add(new JLabel(label));p.add(input);}
    private static JPanel triple(JComponent a,JComponent b,JComponent c){JPanel p=new JPanel(new GridLayout(1,c==null?2:3,3,0));p.add(a);p.add(b);if(c!=null)p.add(c);return p;}
    private void button(JPanel p,String title,Runnable action){JButton b=new JButton(title);actions.add(b);p.add(b);b.addActionListener(e->attempt(action));}
    private void attempt(Runnable r){try{r.run();}catch(Exception e){error(e);}}
    private void error(Throwable e){String m=e.getMessage()==null?e.toString():e.getMessage();state.setText("Stopped: "+m);append("ERROR: "+m);if(!GraphicsEnvironment.isHeadless())JOptionPane.showMessageDialog(this,m,"DPCAT",JOptionPane.ERROR_MESSAGE);}
    private void append(String s){log.append(s+"\n");log.setCaretPosition(log.getDocument().getLength());}
    private static double number(JTextField f){double v=Double.parseDouble(f.getText().trim());DpcatConfig.finite(v,"Parameter");return v;}
    private static int integer(JTextField f){return Integer.parseInt(f.getText().trim());}
    public boolean isBusy(){return worker!=null;}
    public DpcatSequence getSequence(){return sequence;}
    public void requestStop(){stopRequested=true;}
    private void busy(boolean b){for(JButton button:actions)button.setEnabled(!b);frameSelector.setEnabled(!b);if(!b)worker=null;}
    public void refreshStatus(){
        ReflectorSettings r=mf.simulation.getReflector();double sum=0;int n=0;for(Transducer t:mf.simulation.transducers)if(t.getAmplitude()>0){sum+=t.getTransform().getTranslation().y;n++;}
        reflector.setText(String.format(Locale.US,"Reflector: %s | Y %.3f mm | gap %.3f mm | |R| %.3f | phase %.2f deg | active %d",
            r.isEnabled()?"ON":"OFF",r.getPlaneY()*1000,n==0?0:Math.abs(sum/n-r.getPlaneY())*1000,r.getMagnitude(),r.getPhaseDegrees(),n));
    }
    private void useCenter(boolean selected){
        Vector3f p=new Vector3f();int n=0;
        if(selected){for(Entity e:mf.selection)if((e.getTag()&Entity.TAG_CONTROL_POINT)!=0){p.addLocal(e.getTransform().getTranslation());n++;}if(n!=1)throw new IllegalArgumentException("Select exactly one control point as the merge center");}
        else {for(Transducer t:mf.simulation.transducers)if(t.getAmplitude()>0){p.addLocal(t.getTransform().getTranslation());n++;}if(n==0)throw new IllegalArgumentException("Load active array first");p.divideLocal(n);p.y=(float)((p.y+mf.simulation.getReflector().getPlaneY())/2);}
        cx.setText(Double.toString(p.x*1000.0));cy.setText(Double.toString(p.y*1000.0));cz.setText(Double.toString(p.z*1000.0));
    }
    public DpcatConfig readConfig(){
        DpcatConfig c=new DpcatConfig();c.objective=DpcatConfig.Objective.values()[objective.getSelectedIndex()];c.initialization=DpcatConfig.Initialization.values()[initialization.getSelectedIndex()];
        c.maxSweeps=integer(sweeps);c.restarts=integer(restarts);c.seed=Long.parseLong(seed.getText().trim());c.alpha=number(alpha);c.beta=number(beta);c.lambdaPhase=number(lambda);c.targetScale=number(scale);c.validate();return c;
    }
    public void optimize(boolean multiple){
        if(isBusy())throw new IllegalStateException("A DPCAT operation is running");
        if(mf.animPanel!=null && mf.animPanel.isPlaying())throw new IllegalStateException("Stop the existing animation first");
        final DpcatConfig cfg=readConfig();final DpcatSnapshot snapshot=new DpcatSnapshot(mf.simulation);
        final Vector3f center=new Vector3f((float)(number(cx)/1000),(float)(number(cy)/1000),(float)(number(cz)/1000));
        DpcatTargets.Mode m=DpcatTargets.Mode.values()[mode.getSelectedIndex()];DpcatTargets.Axis ax=DpcatTargets.Axis.values()[axis.getSelectedIndex()];
        double[] distances=multiple?DpcatTargets.parseMillimetres(separations.getText()):new double[]{number(initial)/1000};
        List<DpcatTargets.Layout> all=DpcatTargets.trajectory(m,center,ax,distances,number(width)/1000,number(spacing)/1000,integer(transition));
        final List<DpcatTargets.Layout> layouts=multiple?all:Collections.singletonList(all.get(0));
        final double hold=number(dwell);DpcatConfig.positive(hold,"Dwell ms");
        final int[] warm=snapshot.fingerprint.equals(previousFingerprint)?previous:null;
        if(cfg.initialization==DpcatConfig.Initialization.PREVIOUS_FRAME&&warm==null)throw new IllegalArgumentException("No previous result for this array/reflector state");
        refreshStatus();stopRequested=false;state.setText("Optimizing...");busy(true);
        worker=new SwingWorker<DpcatSequence,String>(){
            protected DpcatSequence doInBackground(){return DpcatSequence.generate(snapshot,cfg,layouts,hold,warm,()->stopRequested,this::publish);}
            protected void process(List<String> messages){for(String s:messages)append(s);}
            protected void done(){try{
                DpcatSequence result=get();snapshot.checkCurrent(mf.simulation);sequence=result;previous=result.frames.get(result.frames.size()-1).result.codes.clone();previousFingerprint=snapshot.fingerprint;
                loadingFrames=true;frameSelector.removeAllItems();for(DpcatSequence.Frame f:result.frames)frameSelector.addItem(f.index+": "+f.layout.stage+" / "+String.format(Locale.US,"%.3f mm",f.layout.separation*1000));
                frameSelector.setSelectedIndex(0);loadingFrames=false;state.setText("Ready: "+result.frames.size()+" optimized frame(s)");busy(false);preview(false);
            }catch(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();if(cause instanceof CancellationException)state.setText("Cancelled; previous result retained");else error(cause);}finally{loadingFrames=false;busy(false);}}
        };worker.execute();
    }
    private DpcatSequence.Frame current(){if(sequence==null)throw new IllegalStateException("Optimize or generate a sequence first");sequence.snapshot.checkCurrent(mf.simulation);return sequence.frames.get(Math.max(0,frameSelector.getSelectedIndex()));}
    public void preview(boolean field){
        DpcatSequence.Frame f=current();synchronized(mf){sequence.snapshot.apply(f.result.codes,mf.simulation);showMarkers(f.layout.positions);
            if(field){
                if(mf.miscPanel.isPhaseDiscretizer()&&(mf.miscPanel.getPhaseDiscretization()<=0||mf.miscPanel.getPhaseDiscretization()%16!=0))
                    throw new IllegalStateException("For an exact DPCAT preview, disable Misc phase discretization or use a multiple of 16 divisions per pi");
                if(mf.miscPanel.isAmpDiscretizer())throw new IllegalStateException("Disable Misc amplitude discretization for an exact DPCAT preview");
                if(slice==null || !mf.scene.getEntities().contains(slice)){slice=mf.rtSlicePanel.createSlice();mf.scene.getEntities().add(slice);mf.simulation.slices.add(slice);}
                slice.setShader(Resources.SHADER_SLICE_RT_AMP);slice.getTransform().getTranslation().set(f.layout.center);
                slice.getTransform().getRotation().fromAngles(slicePlane.getSelectedIndex()==1?(float)(Math.PI/2):0,0,0);
                double extent=Math.max(.025,f.layout.separation+f.layout.width+.015);slice.getTransform().getScale().set((float)extent,.025f,1);
            }
        }mf.needUpdate();
    }
    private void showMarkers(List<Vector3f> positions){
        mf.scene.getEntities().removeAll(markers);markers.clear();
        for(Vector3f p:positions){MeshEntity e=new MeshEntity(Resources.MESH_SPHERE,null,Resources.SHADER_SOLID_SPEC);e.setColor(acousticfield3d.utils.Color.create(0,220,255,255));
            e.getTransform().getTranslation().set(p);e.getTransform().getScale().set(.0006f);mf.scene.getEntities().add(e);markers.add(e);}
    }
    private void addAnimation(){current();Animation a=sequence.toAnimation(mf.simulation);mf.animPanel.addDpcatAnimation(a);append("Added "+a.keyFrames.getSize()+" discrete frames to Anim; save Simulation .xml.gz to persist.");}
    private void playGenerated(boolean send){current();playAnimation(sequence.toAnimation(mf.simulation),send);}
    private void sendCurrent(){DpcatSequence.Frame f=current();Animation a=sequence.toAnimation(mf.simulation);AnimKeyFrame key=a.keyFrames.getAt(f.index);a.keyFrames.clear();a.keyFrames.add(key);playAnimation(a,true);}
    public void playAnimation(Animation animation,boolean send){
        if(isBusy())throw new IllegalStateException("A DPCAT operation is running");
        final Simulation model=mf.simulation;final MegaFPGA device=send?mf.transControlPanel.getDpcatDevice():null;
        if(device!=null&&!device.isReady())throw new IllegalStateException("Wait for Mega ready in Devs panel");
        final Animation frozen=animation.createCopy(); // GUI edits cannot delete/reorder in-flight frames.
        timing=""; // An interrupted/failed new run must not export a previous run's timestamps.
        if(mf.animPanel!=null)mf.animPanel.setPlaying(false);stopRequested=false;busy(true);state.setText(send?"Discrete playback to Mega...":"Discrete preview...");
        worker=new SwingWorker<String,String>(){
            protected String doInBackground()throws Exception{return DpcatPlayback.play(frozen,model,device,()->stopRequested,i->{
                try{SwingUtilities.invokeAndWait(()->{if(mf.simulation!=model)throw new IllegalStateException("Simulation changed during playback");
                    synchronized(mf){frozen.applyAtFrame(i,model);}mf.needUpdate();});}catch(Exception e){throw new IllegalStateException("Could not apply frame: "+e.getMessage(),e);}
            },this::publish);}
            protected void process(List<String> messages){for(String s:messages)append(s);}
            protected void done(){try{timing=get();state.setText("Discrete playback complete");}catch(Exception e){Throwable c=e.getCause()==null?e:e.getCause();if(c instanceof CancellationException)state.setText("Playback stopped");else error(c);}finally{busy(false);}}
        };worker.execute();
    }
    private Path choose(String name){JFileChooser fc=new JFileChooser();fc.setSelectedFile(new java.io.File(name));if(fc.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return null;
        Path p=fc.getSelectedFile().toPath();if(Files.exists(p)&&JOptionPane.showConfirmDialog(this,"Replace "+p.getFileName()+"?","Export",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return null;return p;}
    private void export(boolean all){DpcatSequence.Frame f=current();Path p=choose(all?"dpcat_sequence.csv":"dpcat_phases.csv");if(p==null)return;try{if(all)DpcatExport.sequence(p,sequence);else DpcatExport.phases(p,sequence.snapshot,f.result.codes);append("Saved "+p);}catch(Exception e){error(e);}}
    private void exportTiming(){if(timing.isEmpty())throw new IllegalStateException("Complete playback before exporting timing");Path p=choose("dpcat_playback_timing.csv");if(p==null)return;try{Files.write(p,timing.getBytes(StandardCharsets.UTF_8));}catch(Exception e){error(e);}}
}
