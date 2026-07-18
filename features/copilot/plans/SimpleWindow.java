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
        private float comboPulse = 0f; // 用于连击放大脉冲动画
        private int hitFlash = 0; // 命中时中心短暂高亮帧计数

        public GamePanel() {
            timer = new Timer(16, (ActionEvent e) -> gameLoop());
            timer.start();

            // 方向键映射
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "hitLeft");
            getActionMap().put("hitLeft", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { tryHit(Note.Direction.LEFT); }});
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "hitRight");
            getActionMap().put("hitRight", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { tryHit(Note.Direction.RIGHT); }});
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "hitUp");
            getActionMap().put("hitUp", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { tryHit(Note.Direction.UP); }});
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "hitDown");
            getActionMap().put("hitDown", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { tryHit(Note.Direction.DOWN); }});

            // 初始音符
            for (int i = 0; i < 4; i++) spawnRandomNote();
        }

        private void gameLoop() {
            tick++;
            if (tick % 45 == 0) spawnRandomNote(); // 生成速率，可调

            Iterator<Note> it = notes.iterator();
            while (it.hasNext()) {
                Note n = it.next();
                n.update();

                // 如果音符越过中心判定区 -> Miss 并移除
                final int passLimit = 30; // 超过中心多少像素视为 Miss
                if (n.hasPassedCenter(getWidth(), getHeight(), passLimit)) {
                    it.remove();
                    combo = 0;
                    feedback = "Miss";
                    continue;
                }
                // 如果完全移出画面也移除
                if (n.isOutOfBounds(getWidth(), getHeight())) {
                    it.remove();
                }
            }

            if (comboPulse > 0f) comboPulse *= 0.88f;
            if (hitFlash > 0) hitFlash--;

            if (!feedback.isEmpty() && tick % 30 == 0) feedback = "";

            repaint();
        }

        private void spawnRandomNote() {
            Note.Direction dir = Note.Direction.values()[rand.nextInt(4)];
            int length = Note.minLength + rand.nextInt(Note.maxLength - Note.minLength + 1);
            double speed = 3.5 + rand.nextDouble() * 3.5; // 速度范围，可调
            notes.add(new Note(dir, length, speed, getWidth(), getHeight()));
        }

        private void tryHit(Note.Direction dir) {
            final int tolerance = 24; // 判定容差像素
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
                comboPulse = 1.2f;
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

                // 紫色渐变背景
                Color darkPurple = new Color(45, 0, 90);
                Color lightPurple = new Color(170, 80, 255);
                GradientPaint gp = new GradientPaint(0, 0, darkPurple, getWidth(), getHeight(), lightPurple);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // 中心正方形
                int size = Math.min(getWidth(), getHeight()) / 4;
                int cx = (getWidth() - size) / 2;
                int cy = (getHeight() - size) / 2;

                // 中心边框（命中时高亮）
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

                // 绘制音符
                for (Note n : notes) n.drawPhigros(g2, getWidth(), getHeight(), cx, cy, size);

                // 分数显示
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 20));
                g2.drawString("Score: " + score, 16, 28);

                // 命中/失误反馈
                if (!feedback.isEmpty()) {
                    g2.setFont(new Font("SansSerif", Font.BOLD, 28));
                    FontMetrics fm = g2.getFontMetrics();
                    int fw = fm.stringWidth(feedback);
                    g2.drawString(feedback, getWidth()/2 - fw/2, 40);
                }

                // 连击显示（Phigros 风格）
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

                    // 外发光（stroke）
                    g2.setColor(new Color(comboColor.getRed(), comboColor.getGreen(), comboColor.getBlue(), 60));
                    g2.setStroke(new BasicStroke(fontSize / 2f));
                    g2.draw(glyph);

                    // 填充与描边
                    g2.setColor(comboColor);
                    g2.fill(glyph);
                    g2.setColor(Color.BLACK);
                    g2.setStroke(new BasicStroke(Math.max(2f, fontSize / 18f)));
                    g2.draw(glyph);

                    // 下方小字 COMBO
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

                // 方向提示
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

        // 沿移动方向的可变长度，短边在绘制时等于中心正方形宽度
        static final int minLength = 80;
        static final int maxLength = 420;
        static final int THICKNESS = 36; // 用于初始生成时的偏移，不用于渲染短边

        final Direction dir;
        double x, y; // 前端位置
        final int length;
        final double speed;
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
                    aw = size; ah = length; ax = (int)Math.round(x - aw); ay = (int)Math.round(y - ah/2.0);
                    drawTrail(g2, base);
                    for (int i = 5; i >= 1; i--) {
                        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 20 * i));
                        g2.fillRoundRect(ax-6-i*2, ay-6-i*4, aw+12+i*4, ah+12+i*8, 16, 16);
                    }
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
