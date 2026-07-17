import javax.swing.*;
import java.awt.*;

public class SimpleWindow extends JFrame {
    public SimpleWindow() {
        super("Simple Window");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 400);
        setLocationRelativeTo(null);

        // Add the drawing panel that displays a square
        add(new DrawPanel());
    }

    private static class DrawPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            // Draw a centered square that is half the size of the smaller window dimension
            int size = Math.min(getWidth(), getHeight()) / 2;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            g.setColor(Color.BLUE);
            g.fillRect(x, y, size, size);

            g.setColor(Color.BLACK);
            g.drawRect(x, y, size, size);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimpleWindow w = new SimpleWindow();
            w.setVisible(true);
        });
    }
}
