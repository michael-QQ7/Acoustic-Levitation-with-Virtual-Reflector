package acousticfield3d.gui.panels;

import acousticfield3d.Config;
import acousticfield3d.gui.MainForm;
import acousticfield3d.simulation.Animation;
import javax.swing.*;

/** Optional real desktop startup check; no serial connection and no visible work window. */
public class DpcatDesktopSmoke {
    public static void main(String[] args)throws Exception {
        final MainForm[] holder=new MainForm[1];
        try {
            SwingUtilities.invokeAndWait(()->{
                MainForm mf=new MainForm(new Config());holder[0]=mf;
                mf.loadSimulation(args[0]);
                if(mf.simulation.transducers.size()!=192)throw new AssertionError("real 192-source example loading");
                Animation a=mf.simulation.animations.getAt(0);a.applyAtFrame(0,mf.simulation);
                mf.setLocation(-5000,-5000);mf.setSize(1000,750);mf.setVisible(true);
                mf.gljpanel.display();
                System.out.println("PASS real desktop: MainForm + JOGL context, DPCAT initialization, existing .gz loader and discrete frame");
            });
            Thread.sleep(500);
            SwingUtilities.invokeAndWait(()->{for(java.awt.Window w:java.awt.Window.getWindows())w.dispose();});
            System.exit(0);
        }catch(Throwable e){e.printStackTrace();System.exit(1);}
    }
}
