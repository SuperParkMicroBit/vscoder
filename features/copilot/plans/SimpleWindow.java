import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
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
        private final boolean[] holding = new boolean[Note.Direction.values().length];

        public GamePanel() {
            timer = new Timer(16, (ActionEvent e) -> gameLoop());
            timer.start();

            // Key bindings (press and release to support hold notes)
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "pressLeft");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, true), "releaseLeft");
            getActionMap().put("pressLeft", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { holding[Note.Direction.LEFT.ordinal()] = true; tryHit(Note.Direction.LEFT); }});
            getActionMap().put("releaseLeft", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { holding[Note.Direction.LEFT.ordinal()] = false; }});
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "pressRight");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true), "releaseRight");
            getActionMap().put("pressRight", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { holding[Note.Direction.RIGHT.ordinal()] = true; tryHit(Note.Direction.RIGHT); }});
            getActionMap().put("releaseRight", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { holding[Note.Direction.RIGHT.ordinal()] = false; }});
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false), "pressUp");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, true), "releaseUp");
            getActionMap().put("pressUp", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { holding[Note.Direction.UP.ordinal()] = true; tryHit(Note.Direction.UP); }});
            getActionMap().put("releaseUp", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { holding[Note.Direction.UP.ordinal()] = false; }});
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false), "pressDown");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, true), "releaseDown");
            getActionMap().put("pressDown", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { holding[Note.Direction.DOWN.ordinal()] = true; tryHit(Note.Direction.DOWN); }});
            getActionMap().put("releaseDown", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { holding[Note.Direction.DOWN.ordinal()] = false; }});

            // spawn initial notes
            for (int i = 0; i < 4; i++) spawnRandomNote();
        }

        private void gameLoop() {
            tick++;
            if (tick % 45 == 0) spawnRandomNote(); // spawn rate

            Iterator<Note> it = notes.iterator();
            while (it.hasNext()) {
                Note n = it.next();
                n.update();

                // if note passed center without being hit -> miss and remove
                final int passLimit = 30; // pixels beyond center considered miss
                final int holdTolerance = 48; // larger window for hold progress

                if (n.isHold) {
                    double dist = n.distanceToCenterEdge(getWidth(), getHeight());
                    if (Math.abs(dist) <= holdTolerance) {
                        if (holding[n.dir.ordinal()]) {
                            n.holdProgress++;
                            if (n.holdProgress >= n.holdRequired) {
                                score += 150; // reward for successful hold
                                combo++;
                                feedback = "Hold OK";
                                comboPulse = 1.2f;
                                hitFlash = 6;
                                it.remove();
                                continue;
                            }
                        } else {
                            // slowly decay progress when not holding
                            if (n.holdProgress > 0 && tick % 4 == 0) n.holdProgress = Math.max(0, n.holdProgress - 1);
                        }
                    }

                    if (n.hasPassedCenter(getWidth(), getHeight(), passLimit)) {
                        it.remove();
                        combo = 0; // reset combo on miss
                        feedback = "Miss";
                        continue;
                    }
                } else {
                    if (n.hasPassedCenter(getWidth(), getHeight(), passLimit)) {
                        it.remove();
                        combo = 0; // reset combo on miss
                        feedback = "Miss";
                        continue;
                    }
                }

                // also remove if completely out of screen bounds
                if (n.isOutOfBounds(getWidth(), getHeight())) {
                    it.remove();
                }
            }

            // combo pulse decay
            if (comboPulse > 0f) comboPulse *= 0.88f;
            if (hitFlash > 0) hitFlash--;

            if (!feedback.isEmpty() && tick % 30 == 0) feedback = "";

            repaint();
        }

        private void spawnRandomNote() {
            Note.Direction dir = Note.Direction.values()[rand.nextInt(4)];
            int length = Note.minLength + rand.nextInt(Note.maxLength - Note.minLength + 1);
            double speed = 3.5 + rand.nextDouble() * 3.5; // 3.5 - 7
            // small chance to spawn a hold (long-press) note
            boolean isHold = rand.nextInt(8) == 0; // ~12.5% chance
            int holdFrames = 30 + rand.nextInt(61); // 30-90 frames to hold
            notes.add(new Note(dir, length, speed, getWidth(), getHeight(), isHold, holdFrames));
        }

        private void tryHit(Note.Direction dir) {
            final int tolerance = 24;
            Note best = null;
            double bestDist = Double.MAX_VALUE;
            for (Note n : notes) {
                if (n.dir != dir) continue;
                if (n.isHold) continue; // hold notes must be held, not tap-hit
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

                // combo display near center, phigros style
                if (combo > 0) {
                    String cText = String.valueOf(combo);
                    float scale = 1.0f + comboPulse * 0.9f;
                    int baseSize = 56;
                    int fontSize = Math.round(baseSize * scale);

                    Font comboFont = new Font("SansSerif", Font.BOLD, fontSize);
                    FontRenderContext frc = g2.getFontRenderContext();
                    GlyphVector gv = comboFont.createGlyphVector(frc, cText);
                    Rectangle bounds = gv.getPixelBounds(null, 0, 0);
                    double tx = getWidth()/2.0 - bounds.getWidth()/2.0;
                    double ty = cy - 8; // baseline
                    Shape glyph = gv.getOutline((float)tx, (float)ty);

                    Color comboColor;
                    if (combo >= 50) comboColor = new Color(255, 110, 60);
                    else if (combo >= 30) comboColor = new Color(255, 170, 80);
                    else if (combo >= 10) comboColor = new Color(255, 220, 120);
                    else comboColor = new Color(255, 255, 255, 230);

                    // outer glow (stroke)
                    g2.setColor(new Color(comboColor.getRed(), comboColor.getGreen(), comboColor.getBlue(), 60));
                    g2.setStroke(new BasicStroke(fontSize / 2f));
                    g2.draw(glyph);

                    // fill main
                    g2.setColor(comboColor);
                    g2.fill(glyph);

                    // outline
                    g2.setColor(Color.BLACK);
                    g2.setStroke(new BasicStroke(Math.max(2f, fontSize / 18f)));
                    g2.draw(glyph);

                    // draw label "COMBO" below
                    String label = "COMBO";
                    Font labelFont = new Font("SansSerif", Font.BOLD, Math.max(14, fontSize/4));
                    g2.setFont(labelFont);
                    FontMetrics fmLabel = g2.getFontMetrics();
                    int lw = fmLabel.stringWidth(label);
                    int lx = (int)(getWidth()/2 - lw/2);
                    int ly = cy + Math.max(12, fontSize/6) + 8;
                    g2.setColor(new Color(255,255,255,200));
                    g2.drawString(label, lx, ly);
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

        // allow variable length for visual depth; short side will match center square width
        static final int minLength = 80;
        static final int maxLength = 420;
        static final int DEPTH = 18; // small depth along travel axis (the "short" side)
        static final int SPAWN_OFFSET = 36; // offset for initial spawn outside screen

        final Direction dir;
        double x, y; // leading edge position (the edge facing the center)
        final int length; // visual extension away from center (unused now for facing side)
        final double speed;
        final List<Point> trail = new ArrayList<>();
        boolean isHold;
        int holdRequired;
        int holdProgress;

        public Note(Direction dir, int length, double speed, int panelW, int panelH, boolean isHold, int holdRequired) {
            this.dir = dir;
            this.length = length;
            this.speed = speed;
            this.isHold = isHold;
            this.holdRequired = isHold ? holdRequired : 0;
            this.holdProgress = 0;
            switch (dir) {
                case LEFT:
                    x = -SPAWN_OFFSET; y = panelH / 2.0; break;
                case RIGHT:
                    x = panelW + SPAWN_OFFSET; y = panelH / 2.0; break;
                case UP:
                    y = -SPAWN_OFFSET; x = panelW / 2.0; break;
                default:
                    y = panelH + SPAWN_OFFSET; x = panelW / 2.0; break;
            }
            trail.add(new Point((int)x, (int)y));
        }

        public void update() {
            switch (dir) {
                case LEFT: x += speed; break;
                case RIGHT: x -= speed; break;
                case UP: y += speed; break;
                case DOWN: y -= speed; break;
            }
            trail.add(0, new Point((int)Math.round(x), (int)Math.round(y)));
            if (trail.size() > 12) trail.remove(trail.size()-1);
        }

        public boolean isOutOfBounds(int panelW, int panelH) {
            return x < -maxLength*2 || x > panelW + maxLength*2 || y < -maxLength*2 || y > panelH + maxLength*2;
        }

        public boolean hasPassedCenter(int panelW, int panelH, int passLimit) {
            int size = Math.min(panelW, panelH) / 4;
            int cx = (panelW - size) / 2;
            int cy = (panelH - size) / 2;
            double d = distanceToCenterEdge(panelW, panelH);
            switch (dir) {
                case LEFT:
                case UP:
                    return d > passLimit;
                case RIGHT:
                case DOWN:
                    return d < -passLimit;
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
            int ax, ay, aw, ah;
            Color base = new Color(255, 140, 60);
            Color accent = new Color(255, 220, 120);

            switch (dir) {
                case LEFT:
                    // hold notes: blue rounded rectangle that shrinks as held; otherwise vertical line
                    int xPos = (int)Math.round(x);
                    int y1 = (int)Math.round(y - size/2.0);
                    int y2 = (int)Math.round(y + size/2.0);
                    Color holdBase = new Color(80, 170, 255);
                    Color holdAccent = new Color(170, 210, 255);
                    drawTrail(g2, base);
                    if (isHold) {
                        float p = holdRequired > 0 ? Math.min(1f, (float)holdProgress / holdRequired) : 0f;
                        int maxLen = Math.max(16, length); // visual max length along travel axis
                        int curLen = Math.max(8, (int)(maxLen * (1.0f - p)));
                        // rectangle extends from leading edge (xPos) away from center
                        int rw = curLen; // width along X
                        int rh = Math.max(DEPTH*2, DEPTH);
                        int rx = xPos - rw;
                        int ry = (int)Math.round(y - rh/2.0);
                        // glow
                        for (int i = 5; i >= 1; i--) {
                            g2.setColor(new Color(holdBase.getRed(), holdBase.getGreen(), holdBase.getBlue(), 18 * i));
                            g2.fillRoundRect(rx - i*3, ry - i*3, rw + i*6, rh + i*6, 12, 12);
                        }
                        GradientPaint hgp = new GradientPaint(rx, ry, holdAccent, rx + rw, ry + rh, holdBase);
                        g2.setPaint(hgp);
                        g2.fillRoundRect(rx, ry, rw, rh, 12, 12);
                        g2.setColor(new Color(20,30,40,200));
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawRoundRect(rx, ry, rw, rh, 12, 12);
                    } else {
                        // non-hold: keep previous line look
                        int lineLen = size;
                        int ly1 = (int)Math.round(y - lineLen/2.0);
                        int ly2 = (int)Math.round(y + lineLen/2.0);
                        for (int i = 5; i >= 1; i--) {
                            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 20 * i));
                            g2.setStroke(new BasicStroke(DEPTH + i*6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            g2.drawLine(xPos, ly1, xPos, ly2);
                        }
                        GradientPaint gp2 = new GradientPaint(xPos, ly1, accent, xPos, ly2, base);
                        g2.setPaint(gp2);
                        g2.setStroke(new BasicStroke(DEPTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(xPos, ly1, xPos, ly2);
                        g2.setColor(new Color(30,30,30,160));
                        g2.setStroke(new BasicStroke(Math.max(2f, DEPTH / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(xPos, ly1, xPos, ly2);
                    }
                    break;
                case RIGHT:
                    // hold notes: blue rounded rectangle that shrinks as held; otherwise vertical line
                    xPos = (int)Math.round(x);
                    y1 = (int)Math.round(y - size/2.0);
                    y2 = (int)Math.round(y + size/2.0);
                    Color holdBaseR = new Color(80, 170, 255);
                    Color holdAccentR = new Color(170, 210, 255);
                    drawTrail(g2, base);
                    if (isHold) {
                        float p = holdRequired > 0 ? Math.min(1f, (float)holdProgress / holdRequired) : 0f;
                        int maxLen = Math.max(16, length);
                        int curLen = Math.max(8, (int)(maxLen * (1.0f - p)));
                        int rw = curLen;
                        int rh = Math.max(DEPTH*2, DEPTH);
                        int rx = xPos;
                        int ry = (int)Math.round(y - rh/2.0);
                        for (int i = 5; i >= 1; i--) {
                            g2.setColor(new Color(holdBaseR.getRed(), holdBaseR.getGreen(), holdBaseR.getBlue(), 18 * i));
                            g2.fillRoundRect(rx - i*3, ry - i*3, rw + i*6, rh + i*6, 12, 12);
                        }
                        GradientPaint hgp = new GradientPaint(rx, ry, holdAccentR, rx + rw, ry + rh, holdBaseR);
                        g2.setPaint(hgp);
                        g2.fillRoundRect(rx, ry, rw, rh, 12, 12);
                        g2.setColor(new Color(20,30,40,200));
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawRoundRect(rx, ry, rw, rh, 12, 12);
                    } else {
                        int lineLen2 = size;
                        int ly1 = (int)Math.round(y - lineLen2/2.0);
                        int ly2 = (int)Math.round(y + lineLen2/2.0);
                        for (int i = 5; i >= 1; i--) {
                            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 20 * i));
                            g2.setStroke(new BasicStroke(DEPTH + i*6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            g2.drawLine(xPos, ly1, xPos, ly2);
                        }
                        GradientPaint gp = new GradientPaint(xPos, ly1, base, xPos, ly2, accent);
                        g2.setPaint(gp);
                        g2.setStroke(new BasicStroke(DEPTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(xPos, ly1, xPos, ly2);
                        g2.setColor(new Color(30,30,30,160));
                        g2.setStroke(new BasicStroke(Math.max(2f, DEPTH / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(xPos, ly1, xPos, ly2);
                    }
                    break;
                case UP:
                    // hold notes: blue rounded rectangle that shrinks as held; otherwise horizontal line
                    int yPos = (int)Math.round(y);
                    int x1 = (int)Math.round(x - size/2.0);
                    int x2 = (int)Math.round(x + size/2.0);
                    Color holdBaseU = new Color(80, 170, 255);
                    Color holdAccentU = new Color(170, 210, 255);
                    drawTrail(g2, base);
                    if (isHold) {
                        float p = holdRequired > 0 ? Math.min(1f, (float)holdProgress / holdRequired) : 0f;
                        int maxLen = Math.max(16, length);
                        int curLen = Math.max(8, (int)(maxLen * (1.0f - p)));
                        int rw = Math.max(DEPTH*2, DEPTH);
                        int rh = curLen;
                        int rx = (int)Math.round(x - rw/2.0);
                        int ry = yPos - rh;
                        for (int i = 5; i >= 1; i--) {
                            g2.setColor(new Color(holdBaseU.getRed(), holdBaseU.getGreen(), holdBaseU.getBlue(), 18 * i));
                            g2.fillRoundRect(rx - i*3, ry - i*3, rw + i*6, rh + i*6, 12, 12);
                        }
                        GradientPaint hgp = new GradientPaint(rx, ry, holdAccentU, rx + rw, ry + rh, holdBaseU);
                        g2.setPaint(hgp);
                        g2.fillRoundRect(rx, ry, rw, rh, 12, 12);
                        g2.setColor(new Color(20,30,40,200));
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawRoundRect(rx, ry, rw, rh, 12, 12);
                    } else {
                        int hLineLen2 = size;
                        int xx1 = (int)Math.round(x - hLineLen2/2.0);
                        int xx2 = (int)Math.round(x + hLineLen2/2.0);
                        for (int i = 5; i >= 1; i--) {
                            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 18 * i));
                            g2.setStroke(new BasicStroke(DEPTH + i*6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            g2.drawLine(xx1, yPos, xx2, yPos);
                        }
                        GradientPaint gp = new GradientPaint(xx1, yPos, accent, xx2, yPos, base);
                        g2.setPaint(gp);
                        g2.setStroke(new BasicStroke(DEPTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(xx1, yPos, xx2, yPos);
                        g2.setColor(new Color(30,30,30,160));
                        g2.setStroke(new BasicStroke(Math.max(2f, DEPTH / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(xx1, yPos, xx2, yPos);
                    }
                    // hold progress overlay
                    if (isHold) {
                        float p = holdRequired > 0 ? Math.min(1f, (float)holdProgress / holdRequired) : 0f;
                        if (p > 0f) {
                            g2.setColor(accent);
                            g2.setStroke(new BasicStroke(Math.max(6f, DEPTH/2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            int prog = (int)((x2 - x1) * p);
                            g2.drawLine(x1, yPos, x1 + prog, yPos);
                        }
                    }
                    break;
                default: // DOWN
                    // hold notes: blue rounded rectangle that shrinks as held; otherwise horizontal line
                    int yP = (int)Math.round(y);
                    int xStart = (int)Math.round(x - size/2.0);
                    int xEnd = (int)Math.round(x + size/2.0);
                    Color holdBaseD = new Color(80, 170, 255);
                    Color holdAccentD = new Color(170, 210, 255);
                    drawTrail(g2, base);
                    if (isHold) {
                        float p = holdRequired > 0 ? Math.min(1f, (float)holdProgress / holdRequired) : 0f;
                        int maxLen = Math.max(16, length);
                        int curLen = Math.max(8, (int)(maxLen * (1.0f - p)));
                        int rw = Math.max(DEPTH*2, DEPTH);
                        int rh = curLen;
                        int rx = (int)Math.round(x - rw/2.0);
                        int ry = yP;
                        for (int i = 5; i >= 1; i--) {
                            g2.setColor(new Color(holdBaseD.getRed(), holdBaseD.getGreen(), holdBaseD.getBlue(), 18 * i));
                            g2.fillRoundRect(rx - i*3, ry - i*3, rw + i*6, rh + i*6, 12, 12);
                        }
                        GradientPaint hgp = new GradientPaint(rx, ry, holdAccentD, rx + rw, ry + rh, holdBaseD);
                        g2.setPaint(hgp);
                        g2.fillRoundRect(rx, ry, rw, rh, 12, 12);
                        g2.setColor(new Color(20,30,40,200));
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawRoundRect(rx, ry, rw, rh, 12, 12);
                    } else {
                        int hLen2 = size;
                        int xxStart = (int)Math.round(x - hLen2/2.0);
                        int xxEnd = (int)Math.round(x + hLen2/2.0);
                        for (int i = 5; i >= 1; i--) {
                            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 18 * i));
                            g2.setStroke(new BasicStroke(DEPTH + i*6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            g2.drawLine(xxStart, yP, xxEnd, yP);
                        }
                        GradientPaint gp = new GradientPaint(xxStart, yP, accent, xxEnd, yP, base);
                        g2.setPaint(gp);
                        g2.setStroke(new BasicStroke(DEPTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(xxStart, yP, xxEnd, yP);
                        g2.setColor(new Color(30,30,30,160));
                        g2.setStroke(new BasicStroke(Math.max(2f, DEPTH / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(xxStart, yP, xxEnd, yP);
                    }
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
