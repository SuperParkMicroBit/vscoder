import javax.swing.*;
import java.awt.*;

public class SimpleWindow extends JFrame {
    public SimpleWindow() {
        super("Simple Window - 音游 背景渐变");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 600);
        setLocationRelativeTo(null);

        // Add the drawing panel that displays a centered square on a purple gradient background
        add(new DrawPanel());
    }

    private static class DrawPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                // Smooth rendering
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Purple gradient background (dark -> light)
                Color darkPurple = new Color(75, 0, 130);   // indigo-like
                Color lightPurple = new Color(180, 100, 255);
                GradientPaint gp = new GradientPaint(0, 0, darkPurple, getWidth(), getHeight(), lightPurple);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Square: now 1/4 of the smaller window dimension (previously 1/2)
                int size = Math.min(getWidth(), getHeight()) / 4;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                // Draw filled square and border
                g2.setColor(new Color(0, 170, 255)); // blue-cyan fill for contrast
                g2.fillRect(x, y, size, size);

                g2.setStroke(new BasicStroke(3f));
                g2.setColor(Color.BLACK);
                g2.drawRect(x, y, size, size);
            } finally {
                g2.dispose();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimpleWindow w = new SimpleWindow();
            w.setVisible(true);
        });
    }
}
