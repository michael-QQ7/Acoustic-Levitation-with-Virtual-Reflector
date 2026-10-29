package acousticfield3d.gui.panels;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import javax.swing.*;

public class MegaFPGAPanelTest {
    static <T> T field(Object object, String name, Class<T> type) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return type.cast(f.get(object));
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                TransControlPanel panel = new TransControlPanel(null);
                JComboBox combo = field(panel, "deviceCombo", JComboBox.class);
                check(combo.getSelectedItem().equals("MegaFPGA"), "default device");
                check(panel.stepsToPhase(16) == 1f, "32 phase divisions before connection");
                for (String name : new String[]{"switchButton", "sendAnimButton", "multiplexButton", "durationsButton"})
                    check(!field(panel, name, JButton.class).isEnabled(), "unsupported control " + name);
                check(field(panel, "linkInfoLabel", JLabel.class).getText().contains("250000"), "PC baud visible");
                combo.setSelectedIndex(1);
                check(field(panel, "switchButton", JButton.class).isEnabled(), "legacy controls restored");
                combo.setSelectedIndex(7);
                panel.setSize(270, 490); panel.doLayout();
                BufferedImage image = new BufferedImage(270, 490, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics(); panel.printAll(g); g.dispose();
                ImageIO.write(image, "png", new File(args[0]));
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
        System.out.println("ALL PASS: MegaFPGA GUI selection, baud labels and supported controls");
    }
}
