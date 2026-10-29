package acousticfield3d.algorithms;

import acousticfield3d.Config;
import acousticfield3d.gui.MainForm;
import acousticfield3d.gui.panels.*;
import acousticfield3d.math.Vector2f;
import acousticfield3d.math.Vector3f;
import acousticfield3d.renderer.Renderer;
import acousticfield3d.scene.*;
import acousticfield3d.simulation.*;
import acousticfield3d.utils.Color;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Headless fixture only: bypasses JFrame construction; production never uses Unsafe. */
public class ReflectorIntegrationTest {
    static void set(Object object,String name,Object value) throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);
    }
    static <T> T get(Object object,String name,Class<T> type) throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return type.cast(f.get(object));
    }
    static MainForm fixture() throws Exception {
        Field u=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);
        MainForm mf=(MainForm)((sun.misc.Unsafe)u.get(null)).allocateInstance(MainForm.class);
        mf.simulation=new Simulation();mf.simulation.setMediumSpeed(343);
        mf.simulation.getReflector().setEnabled(true);mf.simulation.getReflector().setPlaneY(-.04);
        for(int i=0;i<256;i++) {
            Transducer t=ReflectorPhysicsTest.source((i%16-7.5f)*.01f,0,(i/16-7.5f)*.01f);
            t.setOrderNumber(i);t.setDriverPinNumber(i);t.setPhaseCorrection((i%32)/16f);
            t.setAmplitude(i<64?0:1);t.setPhase((float)Math.sin(i));mf.simulation.transducers.add(t);
        }
        set(mf,"config",new Config());set(mf,"panel",new JPanel());set(mf,"scene",new Scene());
        set(mf,"selection",new ArrayList<Entity>());set(mf,"miscPanel",new MiscPanel(mf));
        set(mf,"renderer",new Renderer(mf.scene,mf));set(mf,"rtSlicePanel",new RtSlicePanel(mf));
        mf.renderer.updateTransducersBuffers(mf.simulation);return mf;
    }
    static void layout(Component component) {
        if(component instanceof Container) {
            ((Container)component).doLayout();for(Component child:((Container)component).getComponents())layout(child);
        }
    }
    static JButton button(Container c,String name) {
        for(Component child:c.getComponents()) {
            if(child instanceof JButton && ((JButton)child).getText().equals(name))return (JButton)child;
            if(child instanceof Container) {JButton b=button((Container)child,name);if(b!=null)return b;}
        }
        return null;
    }
    public static void main(String[] args) throws Exception {
        MainForm mf=fixture();
        Vector3f pos=new Vector3f(.003f,-.021f,.009f);
        for(double height:new double[]{-.04,-.046}) {
            mf.simulation.getReflector().setPlaneY(height);
            CachedPointFieldCalc cp=CachedPointFieldCalc.create(pos,mf);cp.allocateAndInit(mf);
            cp.updatePressure(mf.simulation.getTransPhasesAsArray());Vector2f cached=new Vector2f();cp.evalField(cached);
            Vector2f direct=CalcField.calcFieldAt(pos,mf);
            ReflectorPhysicsTest.near(cached.x,direct.x,.002,"actual cache vs CPU Re");
            ReflectorPhysicsTest.near(cached.y,direct.y,.002,"actual cache vs CPU Im");
            for(int i=0;i<64;i++)ReflectorPhysicsTest.near(cp.ka[i],0,0,"disabled cache entry");
            cp.updateGorkov(mf.simulation.getTransPhasesAsArray());
            double u=CalcField.calcGorkovAt(pos.x,pos.y,pos.z,mf.scene.getParticleRadious(),mf);
            ReflectorPhysicsTest.near(cp.evalGorkov(),u,Math.max(1e-14,Math.abs(u)*.0003),"actual Gorkov cache vs CPU");
        }
        Entity target=new Entity();target.setColor(Color.WHITE);target.setTag(Entity.TAG_CONTROL_POINT);
        target.getTransform().getTranslation().set(pos);mf.selection.add(target);
        BFGSOptimization opt=new BFGSOptimization(null);ArrayList<Entity> points=new ArrayList<>();points.add(target);
        BFGSOptimization.MultiplePressureAdaptor objective=opt.new MultiplePressureAdaptor(points,mf);
        double[] phases=mf.simulation.getTransPhasesAsArray();double initial=objective.evaluate(phases);
        acousticfield3d.algorithms.bfgs.BFGS optimizer=new acousticfield3d.algorithms.bfgs.BFGS();
        optimizer.setcMaxIterations(60);optimizer.setAlpha(.2);
        optimizer.minimize(objective,phases,phases);
        double after=objective.evaluate(phases);
        ReflectorPhysicsTest.check(after < initial,"BFGS improves reflected pressure objective");
        System.out.println("PASS: production cache/CPU pressure and Gorkov agreement; BFGS |p| "+(-initial)+" -> "+(-after));
        SwingUtilities.invokeAndWait(()->{
            try {
                mf.simulation.getReflector().setPlaneY(-.04);
                ReflectorPanel panel=new ReflectorPanel(mf);
                get(panel,"gap",JSpinner.class).setValue(45.0);
                button(panel,"Place below (-Y)").doClick();
                ReflectorPhysicsTest.near(mf.simulation.getReflector().getPlaneY(),-.045,1e-12,"gap below mm conversion");
                button(panel,"Place above (+Y)").doClick();
                ReflectorPhysicsTest.near(mf.simulation.getReflector().getPlaneY(),.045,1e-12,"gap above mm conversion");
                get(panel,"planeY",JSpinner.class).setValue(-40.0);
                ReflectorPhysicsTest.near(mf.simulation.getReflector().getPlaneY(),-.04,1e-12,"live height updates");
                button(panel,"Add XY slice").doClick();
                ReflectorPhysicsTest.check(mf.scene.getFirstWithTag(Entity.TAG_SLICE)!=null,"slice button adds scene entity");
                button(panel,"Focus selected point (pressure)").doClick();
                for(int i=0;i<256;i++) {
                    ReflectorPhysicsTest.near(mf.simulation.transducers.get(i).getPhaseCorrection(),(i%32)/16f,0,"UI correction preserved");
                    ReflectorPhysicsTest.near(mf.simulation.transducers.get(i).getAmplitude(),i<64?0:1,0,"UI OFF preserved");
                }
                panel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));panel.setSize(470,480);layout(panel);
                BufferedImage image=new BufferedImage(470,480,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
                panel.printAll(g);g.dispose();ImageIO.write(image,"png",new File(args.length>0?args[0]:"reflector-panel.png"));
            }catch(Exception e){throw new RuntimeException(e);}
        });
        System.out.println("PASS: Swing height/gap controls, slice creation, pressure focus and calibration preservation");
    }
}
