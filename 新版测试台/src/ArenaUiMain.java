import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ArenaUiMain {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception exception) {
            System.out.println("Using default Swing look and feel.");
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                GameEngine engine = ArenaFactory.createDefaultEngine();
                ArenaFrame frame = new ArenaFrame(engine);
                frame.setVisible(true);
            }
        });
    }
}
