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
        super("Simple Window - 音游 Phigros 风格");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 900);
        setLocationRelativeTo(null);

        GamePanel panel = new GamePanel();
        add(panel);
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
        private int combo = 0;
        private float comboPulse = 0f; // used for simple pulse animation on combo
        private int hitFlash = 0; // frames to flash center on hit

        public GamePanel() {
            timer = new Timer(16, (ActionEvent e) -> gameLoop());
            timer.start();

            // Key bindings
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "hitLeft");
            getActionMap().put("hitLeft", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { tryHit(Note.Direction.LEFT); }});
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "hitRight");
            getActionMap().put("hitRight", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { tryHit(Note.Direction.RIGHT); }});
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "hitUp");
            getActionMap().put("hitUp", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { tryHit(Note.Direction.UP); }});
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "hitDown");
            getActionMap().put("hitDown", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { tryHit(Note.Direction.DOWN); }});

            // spawn initial notes
            for (int i = 0; i < 4; i++) spawnRandomNote();
        }

        private void gameLoop() {
            tick++;
            if (tick % 50 == 0) spawnRandomNote(); // every ~0.8s

            Iterator<Note> it = notes.iterator();
            while (it.hasNext()) {
                Note n = it.next();
                n.update();
                // if note passed center without being hit -> miss and remove
                final int passLimit = 30; // pixels beyond center considered miss
                if (n.hasPassedCenter(getWidth(), getHeight(), passLimit)) {
                    it.remove();
                    combo = 0; // reset combo on miss
                    feedback = "Miss";
                    continue;
                }
                // also remove if completely out of screen bounds
                if (n.isOutOfBounds(getWidth(), getHeight())) {
                    it.remove();
                }
            }

            // combo pulse decay
            if (comboPulse > 0f) comboPulse *= 0.92f;
            if (hitFlash > 0) hitFlash--;

            if (!feedback.isEmpty() && tick % 30 == 0) feedback = "";

            repaint();
        }

        private void spawnRandomNote() {
            Note.Direction dir = Note.Direction.values()[rand.nextInt(4)];
            int length = Note.NOTE_LENGTH;
            double speed = 3.5 + rand.nextDouble() * 3.5; // 3.5 - 7
            notes.add(new Note(dir, length, speed, getWidth(), getHeight()));
        }

        private void tryHit(Note.Direction dir) {
            final int tolerance = 24;
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
                score += 100;
                combo++;
                feedback = "Hit";
                comboPulse = 1.2f; // trigger pulse
                hitFlash = 6;
                notes.remove(best);
            } else {
                score = Math.max(0, score - 30);
                combo = 0;
                feedback = "Bad";
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // purple gradient background
                Color darkPurple = new Color(45, 0, 90);
                Color lightPurple = new Color(170, 80, 255);
                GradientPaint gp = new GradientPaint(0, 0, darkPurple, getWidth(), getHeight(), lightPurple);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // center square
                int size = Math.min(getWidth(), getHeight()) / 4;
                int cx = (getWidth() - size) / 2;
                int cy = (getHeight() - size) / 2;

                // draw center frame (slightly glowing when hit)
                if (hitFlash > 0) {
                    g2.setColor(new Color(255, 255, 255, 100));
                    g2.fillRoundRect(cx - 12, cy - 12, size + 24, size + 24, 16, 16);
                } else {
                    g2.setColor(new Color(30, 30, 30, 160));
                    g2.fillRoundRect(cx - 8, cy - 8, size + 16, size + 16, 12, 12);
                }

                g2.setColor(new Color(20, 160, 240));
                g2.fillRoundRect(cx, cy, size, size, 12, 12);
                g2.setStroke(new BasicStroke(4f));
                g2.setColor(Color.BLACK);
                g2.drawRoundRect(cx, cy, size, size, 12, 12);

                // draw notes (phigros style)
                for (Note n : notes) n.drawPhigros(g2, getWidth(), getHeight(), cx, cy, size);

                // draw UI: score
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 20));
                g2.drawString("Score: " + score, 16, 28);

                // feedback
                if (!feedback.isEmpty()) {
                    g2.setFont(new Font("SansSerif", Font.BOLD, 28));
                    FontMetrics fm = g2.getFontMetrics();
                    int fw = fm.stringWidth(feedback);
                    g2.drawString(feedback, getWidth()/2 - fw/2, 40);
                }

                // combo display near center, large when >0
                if (combo > 0) {
                    String cText = String.valueOf(combo);
                    float scale = 1.0f + comboPulse * 0.8f;
                    int baseSize = 36;
                    int fontSize = Math.round(baseSize * scale);
                    g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));
                    FontMetrics fm = g2.getFontMetrics();
                    int tw = fm.stringWidth(cText);
                    g2.setColor(new Color(255, 230, 180));
                    g2.drawString(cText, getWidth()/2 - tw/2, cy - 12);
                }

                // draw direction hints
                g2.setFont(new Font("SansSerif", Font.PLAIN, 22));
                FontMetrics hintFm = g2.getFontMetrics();
                int hintW = hintFm.stringWidth("←");
                g2.setColor(new Color(255,255,255,120));
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

        static final int NOTE_LENGTH = 240; // long dimension
        // THICKNESS no longer used for visual short dimension; short dimension is center square size
        static final int THICKNESS = 36; // kept for movement spawn offsets

        final Direction dir;
        double x, y; // leading edge position
        final int length;
        final double speed;
        // trail positions for simple tail effect
        final List<Point> trail = new ArrayList<>();

        public Note(Direction dir, int length, double speed, int panelW, int panelH) {
            this.dir = dir;
            this.length = length;
            this.speed = speed;
            switch (dir) {
                case LEFT:
                    x = -THICKNESS; y = panelH / 2.0; break;
                case RIGHT:
                    x = panelW + THICKNESS; y = panelH / 2.0; break;
                case UP:
                    y = -THICKNESS; x = panelW / 2.0; break;
                default:
                    y = panelH + THICKNESS; x = panelW / 2.0; break;
            }
            // init trail
            trail.add(new Point((int)x, (int)y));
        }

        public void update() {
            switch (dir) {
                case LEFT: x += speed; break;
                case RIGHT: x -= speed; break;
                case UP: y += speed; break;
                case DOWN: y -= speed; break;
            }
            // add to trail and cap length
            trail.add(0, new Point((int)Math.round(x), (int)Math.round(y)));
            if (trail.size() > 12) trail.remove(trail.size()-1);
        }

        public boolean isOutOfBounds(int panelW, int panelH) {
            return x < -length*2 || x > panelW + length*2 || y < -length*2 || y > panelH + length*2;
        }

        public boolean hasPassedCenter(int panelW, int panelH, int passLimit) {
            int size = Math.min(panelW, panelH) / 4;
            int cx = (panelW - size) / 2;
            int cy = (panelH - size) / 2;
            double d = distanceToCenterEdge(panelW, panelH);
            switch (dir) {
                case LEFT:
                case UP:
                    return d > passLimit; // moved past forward beyond threshold
                case RIGHT:
                case DOWN:
                    return d < -passLimit; // moved past backward beyond threshold
            }
            return false;
        }

        public double distanceToCenterEdge(int panelW, int panelH) {
            int size = Math.min(panelW, panelH) / 4;
            int cx = (panelW - size) / 2;
            int cy = (panelH - size) / 2;
            switch (dir) {
                case LEFT: return x - cx;
                case RIGHT: return x - (cx + size);
                case UP: return y - cy;
                default: return y - (cy + size);
            }
        }

        public void drawPhigros(Graphics2D g2, int panelW, int panelH, int cx, int cy, int size) {
            // glow: draw expanding translucent layers
            int ax, ay, aw, ah;
            Color base = new Color(255, 140, 60); // warm base
            Color accent = new Color(255, 220, 120);

            switch (dir) {
                case LEFT:
                    // vertical long bar moving right; short dimension equals center square width
                    aw = size; ah = length; ax = (int)Math.round(x - aw); ay = (int)Math.round(y - ah/2.0);
                    // trail
                    drawTrail(g2, base);
                    // glow layers (width = center width)
                    for (int i = 5; i >= 1; i--) {
                        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 20 * i));
                        g2.fillRoundRect(ax-6-i*2, ay-6-i*4, aw+12+i*4, ah+12+i*8, 16, 16);
                    }
                    // body gradient
                    GradientPaint gp = new GradientPaint(ax, ay, accent, ax, ay+ah, base);
                    g2.setPaint(gp);
                    g2.fillRoundRect(ax, ay, aw, ah, 12, 12);
                    g2.setColor(new Color(30,30,30,160));
                    g2.drawRoundRect(ax, ay, aw, ah, 12, 12);
                    break;
                case RIGHT:
                    aw = size; ah = length; ax = (int)Math.round(x); ay = (int)Math.round(y - ah/2.0);
                    drawTrail(g2, base);
                    for (int i = 5; i >= 1; i--) {
                        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 20 * i));
                        g2.fillRoundRect(ax-6-i*2, ay-6-i*4, aw+12+i*4, ah+12+i*8, 16, 16);
                    }
                    gp = new GradientPaint(ax, ay, base, ax, ay+ah, accent);
                    g2.setPaint(gp);
                    g2.fillRoundRect(ax, ay, aw, ah, 12, 12);
                    g2.setColor(new Color(30,30,30,160));
                    g2.drawRoundRect(ax, ay, aw, ah, 12, 12);
                    break;
                case UP:
                    aw = length; ah = size; ay = (int)Math.round(y - ah); ax = (int)Math.round(x - aw/2.0);
                    drawTrail(g2, base);
                    for (int i = 5; i >= 1; i--) {
                        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 18 * i));
                        g2.fillRoundRect(ax-6-i*4, ay-6-i*2, aw+12+i*8, ah+12+i*4, 16, 16);
                    }
                    gp = new GradientPaint(ax, ay, accent, ax+aw, ay, base);
                    g2.setPaint(gp);
                    g2.fillRoundRect(ax, ay, aw, ah, 12, 12);
                    g2.setColor(new Color(30,30,30,160));
                    g2.drawRoundRect(ax, ay, aw, ah, 12, 12);
                    break;
                default: // DOWN
                    aw = length; ah = size; ay = (int)Math.round(y); ax = (int)Math.round(x - aw/2.0);
                    drawTrail(g2, base);
                    for (int i = 5; i >= 1; i--) {
                        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 18 * i));
                        g2.fillRoundRect(ax-6-i*4, ay-6-i*2, aw+12+i*8, ah+12+i*4, 16, 16);
                    }
                    gp = new GradientPaint((float)ax, (float)ay, accent, (float)(ax+aw), (float)ay, base);
                    g2.setPaint(gp);
                    g2.fillRoundRect(ax, ay, aw, ah, 12, 12);
                    g2.setColor(new Color(30,30,30,160));
                    g2.drawRoundRect(ax, ay, aw, ah, 12, 12);
                    break;
            }
        }

        private void drawTrail(Graphics2D g2, Color base) {
            int alpha = 90;
            for (int i = 0; i < trail.size(); i++) {
                Point p = trail.get(i);
                float t = 1f - (i / (float)trail.size());
                int r = Math.max(6, (int)(20 * t));
                g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), (int)(alpha * t)));
                g2.fillOval(p.x - r/2, p.y - r/2, r, r);
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
