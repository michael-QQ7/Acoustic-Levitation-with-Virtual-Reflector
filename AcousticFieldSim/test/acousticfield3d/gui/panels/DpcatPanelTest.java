package acousticfield3d.gui.panels;

import acousticfield3d.gui.MainForm;
import acousticfield3d.algorithms.dpcat.*;
import acousticfield3d.simulation.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.*;

public class DpcatPanelTest {
    static <T>T field(Object o,String n,Class<T> c)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return c.cast(f.get(o));}
    static void layout(Container c){c.doLayout();for(Component v:c.getComponents())if(v instanceof Container)layout((Container)v);}
    static void check(boolean c,String m){if(!c)throw new AssertionError(m);}
    static JButton button(Container c,String name){for(Component v:c.getComponents()){if(v instanceof JButton&&((JButton)v).getText().equals(name))return(JButton)v;
        if(v instanceof Container){JButton b=button((Container)v,name);if(b!=null)return b;}}return null;}
    static void await(DpcatPanel p)throws Exception{long end=System.nanoTime()+20_000_000_000L;AtomicBoolean busy=new AtomicBoolean(true);
        while(busy.get()&&System.nanoTime()<end){SwingUtilities.invokeAndWait(()->busy.set(p.isBusy()));Thread.sleep(10);}check(!busy.get(),"worker completed");}
    public static void main(String[] args)throws Exception {
        Method fixture=Class.forName("acousticfield3d.algorithms.ReflectorIntegrationTest").getDeclaredMethod("fixture");fixture.setAccessible(true);
        MainForm mf=(MainForm)fixture.invoke(null);mf.simulation=DpcatCoreTest.fixture();
        final DpcatPanel[] panels=new DpcatPanel[1];
        SwingUtilities.invokeAndWait(()->{try{
            DpcatPanel p=new DpcatPanel(mf);panels[0]=p;field(p,"cy",JTextField.class).setText("-37.5");
            field(p,"objective",JComboBox.class).setSelectedIndex(1);field(p,"alpha",JTextField.class).setText("100");
            field(p,"sweeps",JTextField.class).setText("3");button(p,"Generate Coalescence Sequence").doClick();
        }catch(Exception e){throw new RuntimeException(e);}});
        DpcatPanel p=panels[0];await(p);check(p.getSequence()!=null&&p.getSequence().frames.size()==8,"GUI generates 8 frames");
        SwingUtilities.invokeAndWait(()->{try{
            field(p,"frameSelector",JComboBox.class).setSelectedIndex(2);button(p,"Preview Field").doClick();
            check(mf.simulation.slices.size()==1,"preview reuses slice renderer");
            int[] expected=p.getSequence().frames.get(2).result.codes;
            for(int i=0;i<192;i++)check(mf.simulation.transducers.get(i+64).getPhase()==DpcatSnapshot.internal(expected[i]),"selected frame applied");
            DpcatSequence.Frame frame=p.getSequence().frames.get(2);
            for(int n=0;n<frame.layout.positions.size();n++) {
                acousticfield3d.math.Vector2f actual=acousticfield3d.algorithms.CalcField.calcFieldAt(frame.layout.positions.get(n),mf);
                check(Math.abs(actual.x-frame.result.pressure[0][n])<.005&&Math.abs(actual.y-frame.result.pressure[1][n])<.005,"DPCAT matrix agrees with existing field renderer CPU path");
            }
            p.setSize(1120,800);layout(p);BufferedImage image=new BufferedImage(1120,800,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=image.createGraphics();p.printAll(g);g.dispose();Files.createDirectories(Paths.get("verification"));ImageIO.write(image,"png",Paths.get("verification/dpcat-panel.png").toFile());
            button(p,"Preview Discrete Sequence").doClick();
        }catch(Exception e){throw new RuntimeException(e);}});
        await(p);
        check(!field(p,"timing",String.class).isEmpty(),"GUI preview timing available");
        SwingUtilities.invokeAndWait(()->{
            Animation invalid=p.getSequence().toAnimation(mf.simulation);
            invalid.keyFrames.getAt(0).getTransPhases().put(mf.simulation.transducers.get(64),.123f);
            p.playAnimation(invalid,false);
        });
        await(p);
        check(field(p,"timing",String.class).isEmpty(),"failed playback cannot export stale timing from a previous run");
        SwingUtilities.invokeAndWait(()->{try{
            field(p,"sweeps",JTextField.class).setText("0");button(p,"Optimize Current").doClick();
            check(field(p,"state",JLabel.class).getText().contains("Sweeps"),"invalid GUI parameters rejected");
        }catch(Exception e){throw new RuntimeException(e);}});
        System.out.println("PASS DPCAT Swing controls, background sequence, selected-frame application, slice preview, timing and validation");
    }
}
