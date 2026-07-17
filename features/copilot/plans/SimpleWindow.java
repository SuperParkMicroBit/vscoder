import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class SimpleWindow extends JFrame {
    public SimpleWindow() {
        super("Simple Window - 音游 背景渐变");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 800);
        setLocationRelativeTo(null);

        GamePanel panel = new GamePanel();
        add(panel);
        // Focus to receive key events
        panel.setFocusable(true);
        panel.requestFocusInWindow();
    }

    private static class GamePanel extends JPanel {
        private final List<Note> notes = new ArrayList<>();
        private final Random rand = new Random();
        private final Timer timer;
        private int tick = 0;
        private int score = 0;
        private String feedback = "";

        public GamePanel() {
            // spawn/move at ~60fps
            timer = new Timer(16, (ActionEvent e) -> gameLoop());
            timer.start();

            // Key bindings for arrow keys
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "hitLeft");
            getActionMap().put("hitLeft", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { tryHit(Note.Direction.LEFT); }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "hitRight");
            getActionMap().put("hitRight", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { tryHit(Note.Direction.RIGHT); }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "hitUp");
            getActionMap().put("hitUp", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { tryHit(Note.Direction.UP); }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "hitDown");
            getActionMap().put("hitDown", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { tryHit(Note.Direction.DOWN); }
            });

            // spawn some initial notes
            for (int i = 0; i < 3; i++) spawnRandomNote();
        }

        private void gameLoop() {
            tick++;
            // spawn a new note every ~60 frames (about 1 second)
            if (tick % 60 == 0) spawnRandomNote();

            // update notes
            Iterator<Note> it = notes.iterator();
            while (it.hasNext()) {
                Note n = it.next();
                n.update();
                // remove notes that passed the center by some margin
                if (n.isOutOfBounds(getWidth(), getHeight())) {
                    it.remove();
                    feedback = "Miss";
                }
            }

            // fade feedback
            if (!feedback.isEmpty() && tick % 30 == 0) feedback = "";

            repaint();
        }

        private void spawnRandomNote() {
            Note.Direction dir = Note.Direction.values()[rand.nextInt(4)];
            // fixed length for all notes
            int length = Note.NOTE_LENGTH;
            // speed: pixels per tick (higher moves faster)
            double speed = 4 + rand.nextDouble() * 3; // 4-7 px per tick
            notes.add(new Note(dir, length, speed, getWidth(), getHeight()));
        }

        private void tryHit(Note.Direction dir) {
            // hit window tolerance in pixels: how close leading edge should be to center border
            final int tolerance = 20;
            // find the closest note of that direction whose leading edge is within tolerance
            Note best = null;
            double bestDist = Double.MAX_VALUE;
            for (Note n : notes) {
                if (n.dir != dir) continue;
                double dist = n.distanceToCenterEdge(getWidth(), getHeight());
                if (Math.abs(dist) <= tolerance && Math.abs(dist) < Math.abs(bestDist)) {
                    best = n;
                    bestDist = dist;
                }
            }
            if (best != null) {
                // successful hit
                score += 100;
                feedback = "Hit +100";
                notes.remove(best);
            } else {
                // bad hit (no note)
                score = Math.max(0, score - 50);
                feedback = "Bad";
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Purple gradient background (dark -> light)
                Color darkPurple = new Color(75, 0, 130);
                Color lightPurple = new Color(180, 100, 255);
                GradientPaint gp = new GradientPaint(0, 0, darkPurple, getWidth(), getHeight(), lightPurple);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // center square: 1/4 of smaller dimension
                int size = Math.min(getWidth(), getHeight()) / 4;
                int cx = (getWidth() - size) / 2;
                int cy = (getHeight() - size) / 2;

                // draw center square border (hit areas are its four edges)
                g2.setColor(new Color(50, 50, 50, 200));
                g2.fillRect(cx - 6, cy - 6, size + 12, size + 12); // subtle frame behind

                g2.setColor(new Color(0, 170, 255));
                g2.fillRect(cx, cy, size, size);

                g2.setStroke(new BasicStroke(4f));
                g2.setColor(Color.BLACK);
                g2.drawRect(cx, cy, size, size);

                // draw notes
                for (Note n : notes) n.draw(g2, getWidth(), getHeight(), cx, cy, size);

                // draw UI: score and feedback
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 20));
                g2.drawString("Score: " + score, 16, 28);

                if (!feedback.isEmpty()) {
                    g2.setFont(new Font("SansSerif", Font.BOLD, 28));
                    FontMetrics fm = g2.getFontMetrics();
                    int fw = fm.stringWidth(feedback);
                    g2.drawString(feedback, getWidth()/2 - fw/2, 40);
                }

                // draw direction hints
                g2.setFont(new Font("SansSerif", Font.PLAIN, 24));
                FontMetrics hintFm = g2.getFontMetrics();
                int hintW = hintFm.stringWidth("←");
                g2.drawString("←", 10, getHeight()/2 + hintFm.getAscent()/2 - 4);
                g2.drawString("→", getWidth()-10-hintW, getHeight()/2 + hintFm.getAscent()/2 - 4);
                int upW = hintFm.stringWidth("↑");
                g2.drawString("↑", getWidth()/2 - upW/2, 30);
                g2.drawString("↓", getWidth()/2 - upW/2, getHeight()-10);

            } finally {
                g2.dispose();
            }
        }
    }

    private static class Note {
        enum Direction {LEFT, RIGHT, UP, DOWN}

        // fixed note length (long dimension) and thickness (short dimension)
        static final int NOTE_LENGTH = 200;
        static final int THICKNESS = 30;

        final Direction dir;
        double x, y; // leading edge position along travel axis (for LEFT/RIGHT: x is leading; for UP/DOWN: y is leading)
        final int length; // long dimension in pixels (NOTE_LENGTH)
        final double speed; // movement per tick (pixels)

        public Note(Direction dir, int length, double speed, int panelW, int panelH) {
            this.dir = dir;
            this.length = length;
            this.speed = speed;
            // initialize position so note starts just outside the panel
            switch (dir) {
                case LEFT:
                    // come from left moving right: leading edge x = -THICKNESS (right edge is leading)
                    x = -THICKNESS;
                    y = panelH / 2.0; // center vertically
                    break;
                case RIGHT:
                    // come from right moving left: leading edge x = panelW + THICKNESS (left edge is leading)
                    x = panelW + THICKNESS;
                    y = panelH / 2.0;
                    break;
                case UP:
                    // come from top moving down: leading edge y = -THICKNESS (bottom edge is leading)
                    y = -THICKNESS;
                    x = panelW / 2.0;
                    break;
                default: // DOWN
                    // come from bottom moving up: leading edge y = panelH + THICKNESS (top edge is leading)
                    y = panelH + THICKNESS;
                    x = panelW / 2.0;
                    break;
            }
        }

        public void update() {
            switch (dir) {
                case LEFT: x += speed; break; // moving right
                case RIGHT: x -= speed; break; // moving left
                case UP: y += speed; break; // moving down
                case DOWN: y -= speed; break; // moving up
            }
        }

        public boolean isOutOfBounds(int panelW, int panelH) {
            // if passed beyond panel by margin
            return x < -length*2 && x < -THICKNESS*2 || x > panelW + length*2 || y < -length*2 || y > panelH + length*2;
        }

        // distance from leading edge to the center square edge along travel axis
        public double distanceToCenterEdge(int panelW, int panelH) {
            int size = Math.min(panelW, panelH) / 4;
            int cx = (panelW - size) / 2;
            int cy = (panelH - size) / 2;
            switch (dir) {
                case LEFT:
                    // leading x should match left edge (cx)
                    return x - cx;
                case RIGHT:
                    // leading x should match right edge (cx+size)
                    return x - (cx + size);
                case UP:
                    return y - cy;
                case DOWN:
                    return y - (cy + size);
            }
            return Double.MAX_VALUE;
        }

        public void draw(Graphics2D g2, int panelW, int panelH, int cx, int cy, int size) {
            g2.setColor(new Color(255, 220, 80));
            int ax, ay, aw, ah;
            switch (dir) {
                case LEFT:
                    // vertical bar (tall) moving right: thickness horizontally, length vertically, right edge at x
                    aw = THICKNESS;
                    ah = length;
                    ax = (int) Math.round(x - aw); // left x = leading right edge - thickness
                    ay = (int) Math.round(y - ah / 2.0);
                    g2.fillRoundRect(ax, ay, aw, ah, 8, 8);
                    g2.setColor(Color.BLACK);
                    g2.drawRoundRect(ax, ay, aw, ah, 8, 8);
                    break;
                case RIGHT:
                    // vertical bar moving left: left edge at x
                    aw = THICKNESS;
                    ah = length;
                    ax = (int) Math.round(x);
                    ay = (int) Math.round(y - ah / 2.0);
                    g2.fillRoundRect(ax, ay, aw, ah, 8, 8);
                    g2.setColor(Color.BLACK);
                    g2.drawRoundRect(ax, ay, aw, ah, 8, 8);
                    break;
                case UP:
                    // horizontal bar moving down: bottom edge at y
                    aw = length;
                    ah = THICKNESS;
                    ay = (int) Math.round(y - ah);
                    ax = (int) Math.round(x - aw / 2.0);
                    g2.fillRoundRect(ax, ay, aw, ah, 8, 8);
                    g2.setColor(Color.BLACK);
                    g2.drawRoundRect(ax, ay, aw, ah, 8, 8);
                    break;
                case DOWN:
                    // horizontal bar moving up: top edge at y
                    aw = length;
                    ah = THICKNESS;
                    ay = (int) Math.round(y);
                    ax = (int) Math.round(x - aw / 2.0);
                    g2.fillRoundRect(ax, ay, aw, ah, 8, 8);
                    g2.setColor(Color.BLACK);
                    g2.drawRoundRect(ax, ay, aw, ah, 8, 8);
                    break;
            }

            // Optionally draw a small marker when the leading edge is close to the hit edge
            double dist = distanceToCenterEdge(panelW, panelH);
            if (Math.abs(dist) < 40) {
                g2.setColor(new Color(255, 255, 255, 120));
                switch (dir) {
                    case LEFT:
                        g2.fillOval((int)Math.round(x)-10, (int)Math.round(y)-10, 20, 20);
                        break;
                    case RIGHT:
                        g2.fillOval((int)Math.round(x)+10, (int)Math.round(y)-10, 20, 20);
                        break;
                    case UP:
                        g2.fillOval((int)Math.round(x)-10, (int)Math.round(y)-10, 20, 20);
                        break;
                    case DOWN:
                        g2.fillOval((int)Math.round(x)-10, (int)Math.round(y)+10, 20, 20);
                        break;
                }
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
