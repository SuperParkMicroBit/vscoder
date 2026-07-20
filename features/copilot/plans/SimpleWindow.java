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
import javax.sound.midi.*;
import javax.sound.sampled.*;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;

public class SimpleWindow extends JFrame {
    public SimpleWindow() {
            super("Source:TeleX");
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
        private final List<ScheduledNote> scheduled = new ArrayList<>();
        private final Random rand = new Random();
        private final Timer timer;
        private int tick = 0;
        private int score = 0;
        private String feedback = "";
        private int combo = 0;
        private float comboPulse = 0f; // used for simple pulse animation on combo
        private int hitFlash = 0; // frames to flash center on hit
        private final boolean[] holding = new boolean[Note.Direction.values().length];
        private long songStartTime = -1;
        private Sequencer sequencer = null;
        private final int leadMs = 1200; // how early to spawn notes before hit time

        public GamePanel() {
            timer = new Timer(16, (ActionEvent e) -> gameLoop());
            timer.start();

            // Key bindings (press and release to support hold notes)
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "pressLeft");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, true), "releaseLeft");
            getActionMap().put("pressLeft", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { handlePress(Note.Direction.LEFT); }});
            getActionMap().put("releaseLeft", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { holding[Note.Direction.LEFT.ordinal()] = false; }});
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "pressRight");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true), "releaseRight");
            getActionMap().put("pressRight", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { handlePress(Note.Direction.RIGHT); }});
            getActionMap().put("releaseRight", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { holding[Note.Direction.RIGHT.ordinal()] = false; }});
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false), "pressUp");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, true), "releaseUp");
            getActionMap().put("pressUp", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { handlePress(Note.Direction.UP); }});
            getActionMap().put("releaseUp", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { holding[Note.Direction.UP.ordinal()] = false; }});
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false), "pressDown");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, true), "releaseDown");
            getActionMap().put("pressDown", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { handlePress(Note.Direction.DOWN); }});
            getActionMap().put("releaseDown", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { holding[Note.Direction.DOWN.ordinal()] = false; }});

            // load song MIDI if present
            loadMidi("..\\..\\52925.mid");
            songStartTime = System.currentTimeMillis();

            // spawn initial notes (only when no scheduled song)
            if (scheduled.isEmpty()) for (int i = 0; i < 4; i++) spawnRandomNote();
        }

        private void gameLoop() {
            tick++;
            // spawn from scheduled song if present, otherwise random spawns
            if (!scheduled.isEmpty()) {
                long now = System.currentTimeMillis() - songStartTime;
                while (!scheduled.isEmpty() && scheduled.get(0).timeMs - leadMs <= now) {
                    ScheduledNote s = scheduled.remove(0);
                    spawnScheduledNote(s);
                }
            } else if (tick % 45 == 0) spawnRandomNote(); // spawn rate


            Iterator<Note> it = notes.iterator();
            while (it.hasNext()) {
                Note n = it.next();

                // if note passed center without being hit -> miss and remove
                final int passLimit = 30; // pixels beyond center considered miss
                final int holdTolerance = 48; // larger window for hold progress

                double dist = n.distanceToCenterEdge(getWidth(), getHeight());
                boolean inHoldWindow = Math.abs(dist) <= holdTolerance;

                // If this is a hold note and the player is holding while it's within the window,
                // freeze its movement and only advance hold progress (note shortens visually).
                if (n.isHold && inHoldWindow && holding[n.dir.ordinal()]) {
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
                    // otherwise regular update (movement)
                    n.update();
                    // decay hold progress when within window but not holding
                    if (n.isHold && inHoldWindow && n.holdProgress > 0 && tick % 4 == 0 && !holding[n.dir.ordinal()]) {
                        n.holdProgress = Math.max(0, n.holdProgress - 1);
                    }
                }

                // After movement/hold handling, check pass/miss
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
            if (comboPulse > 0f) comboPulse *= 0.88f;
            if (hitFlash > 0) hitFlash--;

            if (!feedback.isEmpty() && tick % 30 == 0) feedback = "";

            repaint();
        }

        private void spawnRandomNote() {
            // pick a direction that is not currently being held (try a few times)
            Note.Direction[] dirs = Note.Direction.values();
            Note.Direction dir = null;
            for (int tries = 0; tries < 8; tries++) {
                Note.Direction cand = dirs[rand.nextInt(dirs.length)];
                if (!holding[cand.ordinal()]) { dir = cand; break; }
            }
            if (dir == null) return; // all directions held - skip spawn

            int length = Note.minLength + rand.nextInt(Note.maxLength - Note.minLength + 1);
            double speed = 3.5 + rand.nextDouble() * 3.5; // 3.5 - 7
            // small chance to spawn a hold (long-press) note
            boolean isHold = rand.nextInt(8) == 0; // ~12.5% chance
            int holdFrames = 30 + rand.nextInt(61); // 30-90 frames to hold
            // double-check direction not being held right now before adding
            if (holding[dir.ordinal()]) return;
            // compute spawn offset override to avoid overlapping existing notes in same dir
            int gap = 60;
            int spawnOverride = Note.SPAWN_OFFSET;
            if (!notes.isEmpty()) {
                if (dir == Note.Direction.LEFT) {
                    double minX = Double.POSITIVE_INFINITY;
                    for (Note n : notes) if (n.dir == dir) minX = Math.min(minX, n.x);
                    if (minX != Double.POSITIVE_INFINITY) {
                        double desiredX = minX - gap; // more negative
                        spawnOverride = Math.max(spawnOverride, (int)Math.ceil(-desiredX));
                    }
                } else if (dir == Note.Direction.RIGHT) {
                    double maxX = Double.NEGATIVE_INFINITY;
                    for (Note n : notes) if (n.dir == dir) maxX = Math.max(maxX, n.x);
                    if (maxX != Double.NEGATIVE_INFINITY) {
                        double desiredX = maxX + gap;
                        spawnOverride = Math.max(spawnOverride, (int)Math.ceil(desiredX - getWidth()));
                    }
                } else if (dir == Note.Direction.UP) {
                    double minY = Double.POSITIVE_INFINITY;
                    for (Note n : notes) if (n.dir == dir) minY = Math.min(minY, n.y);
                    if (minY != Double.POSITIVE_INFINITY) {
                        double desiredY = minY - gap;
                        spawnOverride = Math.max(spawnOverride, (int)Math.ceil(-desiredY));
                    }
                } else { // DOWN
                    double maxY = Double.NEGATIVE_INFINITY;
                    for (Note n : notes) if (n.dir == dir) maxY = Math.max(maxY, n.y);
                    if (maxY != Double.NEGATIVE_INFINITY) {
                        double desiredY = maxY + gap;
                        spawnOverride = Math.max(spawnOverride, (int)Math.ceil(desiredY - getHeight()));
                    }
                }
            }
            notes.add(new Note(dir, length, speed, getWidth(), getHeight(), isHold, holdFrames, spawnOverride));
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

        // scheduled note structure for song playback
        private static class ScheduledNote {
            long timeMs; // when the note should be hit
            Note.Direction dir;
            int length;
            boolean isHold;
            int holdRequired;
            ScheduledNote(long t, Note.Direction d, int len, boolean hold, int hr) { timeMs = t; dir = d; length = len; isHold = hold; holdRequired = hr; }
        }

        private void spawnScheduledNote(ScheduledNote s) {
            int panelW = getWidth();
            int panelH = getHeight();
            int size = Math.min(panelW, panelH) / 4;
            int cx = (panelW - size) / 2;
            int cy = (panelH - size) / 2;
            double distance = 0;
            switch (s.dir) {
                case LEFT: distance = cx - (-Note.SPAWN_OFFSET); break;
                case RIGHT: distance = (panelW + Note.SPAWN_OFFSET) - (cx + size); break;
                case UP: distance = cy - (-Note.SPAWN_OFFSET); break;
                default: distance = (panelH + Note.SPAWN_OFFSET) - (cy + size); break;
            }
            double speedPerMs = distance / Math.max(1, leadMs);
            double speedPerFrame = speedPerMs * 16.0; // since timer is ~16ms
            // compute spawnOverride similar to random spawn to avoid overlaps
            int gap = 60;
            int spawnOverride = Note.SPAWN_OFFSET;
            if (!notes.isEmpty()) {
                if (s.dir == Note.Direction.LEFT) {
                    double minX = Double.POSITIVE_INFINITY;
                    for (Note n : notes) if (n.dir == s.dir) minX = Math.min(minX, n.x);
                    if (minX != Double.POSITIVE_INFINITY) {
                        double desiredX = minX - gap;
                        spawnOverride = Math.max(spawnOverride, (int)Math.ceil(-desiredX));
                    }
                } else if (s.dir == Note.Direction.RIGHT) {
                    double maxX = Double.NEGATIVE_INFINITY;
                    for (Note n : notes) if (n.dir == s.dir) maxX = Math.max(maxX, n.x);
                    if (maxX != Double.NEGATIVE_INFINITY) {
                        double desiredX = maxX + gap;
                        spawnOverride = Math.max(spawnOverride, (int)Math.ceil(desiredX - panelW));
                    }
                } else if (s.dir == Note.Direction.UP) {
                    double minY = Double.POSITIVE_INFINITY;
                    for (Note n : notes) if (n.dir == s.dir) minY = Math.min(minY, n.y);
                    if (minY != Double.POSITIVE_INFINITY) {
                        double desiredY = minY - gap;
                        spawnOverride = Math.max(spawnOverride, (int)Math.ceil(-desiredY));
                    }
                } else {
                    double maxY = Double.NEGATIVE_INFINITY;
                    for (Note n : notes) if (n.dir == s.dir) maxY = Math.max(maxY, n.y);
                    if (maxY != Double.NEGATIVE_INFINITY) {
                        double desiredY = maxY + gap;
                        spawnOverride = Math.max(spawnOverride, (int)Math.ceil(desiredY - panelH));
                    }
                }
            }
            notes.add(new Note(s.dir, s.length, speedPerFrame, panelW, panelH, s.isHold, s.holdRequired, spawnOverride));
        }

        private void loadMidi(String path) {
            try {
                File f = new File(path);
                if (!f.exists()) return;
                Sequence seq = MidiSystem.getSequence(f);
                int resolution = seq.getResolution();

                // collect tempo changes
                List<Tempo> tempos = new ArrayList<>();
                tempos.add(new Tempo(0, 500000));
                Track[] tracks = seq.getTracks();
                for (Track t : tracks) {
                    for (int i = 0; i < t.size(); i++) {
                        MidiEvent ev = t.get(i);
                        MidiMessage msg = ev.getMessage();
                        if (msg instanceof MetaMessage) {
                            MetaMessage mm = (MetaMessage) msg;
                            if (mm.getType() == 0x51) {
                                byte[] data = mm.getData();
                                int mpq = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                                tempos.add(new Tempo(ev.getTick(), mpq));
                            }
                        }
                    }
                }
                Collections.sort(tempos, new Comparator<Tempo>() { public int compare(Tempo a, Tempo b) { return Long.compare(a.tick, b.tick); } });

                // merge all events across tracks so ordering is global
                class Ev { long tick; MidiMessage msg; }
                List<Ev> events = new ArrayList<>();
                for (Track t : tracks) {
                    for (int i = 0; i < t.size(); i++) {
                        MidiEvent ev = t.get(i);
                        Ev e = new Ev(); e.tick = ev.getTick(); e.msg = ev.getMessage();
                        events.add(e);
                    }
                }
                Collections.sort(events, new Comparator<Ev>() { public int compare(Ev a, Ev b) { return Long.compare(a.tick, b.tick); } });

                // pair note-on and note-off to get durations
                java.util.Map<Integer, Long> active = new java.util.HashMap<>();
                // collect raw scheduled notes with pitch info
                class RawNote { long timeMs; int pitch; boolean isHold; int holdFrames; int len; long startTick; }
                List<RawNote> raw = new ArrayList<>();
                for (Ev e : events) {
                    if (!(e.msg instanceof ShortMessage)) continue;
                    ShortMessage sm = (ShortMessage) e.msg;
                    int cmd = sm.getCommand();
                    int pitch = sm.getData1();
                    int vel = sm.getData2();
                    if (cmd == ShortMessage.NOTE_ON && vel > 0) {
                        active.put(pitch, e.tick);
                    } else if ((cmd == ShortMessage.NOTE_OFF) || (cmd == ShortMessage.NOTE_ON && vel == 0)) {
                        if (active.containsKey(pitch)) {
                            long startTick = active.remove(pitch);
                            long endTick = e.tick;
                            long quant = Math.max(1, resolution / 4);
                            long qStart = Math.round((double)startTick / quant) * quant;
                            final long startMs = ticksToMs(qStart, tempos, resolution);
                            final long endMs = ticksToMs(endTick, tempos, resolution);
                            long durMs = Math.max(0, endMs - startMs);
                            boolean isHold = durMs >= 300;
                            int holdFrames = Math.max(8, (int)(durMs / 16));
                            RawNote rn = new RawNote();
                            rn.timeMs = startMs;
                            rn.pitch = pitch;
                            rn.isHold = isHold;
                            rn.holdFrames = holdFrames;
                            rn.len = Note.minLength;
                            rn.startTick = qStart;
                            raw.add(rn);
                        }
                    }
                }

                // group by quantized start (simultaneous notes), and distribute to lanes more musically
                Collections.sort(raw, new Comparator<RawNote>() { public int compare(RawNote a, RawNote b) { int c = Long.compare(a.timeMs, b.timeMs); if (c != 0) return c; return Integer.compare(a.pitch, b.pitch); } });

                // compute global pitch range for mapping
                int pmin = Integer.MAX_VALUE, pmax = Integer.MIN_VALUE;
                for (RawNote r : raw) { pmin = Math.min(pmin, r.pitch); pmax = Math.max(pmax, r.pitch); }
                if (pmin == Integer.MAX_VALUE) pmin = 60; if (pmax == Integer.MIN_VALUE) pmax = 72;

                long[] lastTime = new long[4];
                for (int i = 0; i < 4; i++) lastTime[i] = Long.MIN_VALUE/2;
                long minGap = 160; // ms minimal gap between notes in same lane
                int maxPerBeat = 2; // cap density per lane per beat

                int i = 0;
                while (i < raw.size()) {
                    long t = raw.get(i).timeMs;
                    // collect group of simultaneous notes (within 1ms after quantization)
                    List<RawNote> group = new ArrayList<>();
                    while (i < raw.size() && raw.get(i).timeMs == t) { group.add(raw.get(i)); i++; }

                    if (group.size() == 1) {
                        RawNote r = group.get(0);
                        // map pitch to lane by relative pitch within range
                        double rel = (double)(r.pitch - pmin) / Math.max(1, (pmax - pmin));
                        int lane = (int)Math.floor(rel * 4.0);
                        if (lane < 0) lane = 0; if (lane > 3) lane = 3;
                        Note.Direction dir = Note.Direction.values()[lane];

                        // check minGap and try to shift to nearby free lane if crowded
                        if (r.timeMs - lastTime[lane] < minGap) {
                            boolean placed = false;
                            // try other lanes preferring same pitch direction (up/down bias)
                            for (int off = 1; off < 4 && !placed; off++) {
                                int l1 = lane - off; if (l1 >= 0 && r.timeMs - lastTime[l1] >= minGap) { lane = l1; placed = true; }
                                int l2 = lane + off; if (!placed && l2 < 4 && r.timeMs - lastTime[l2] >= minGap) { lane = l2; placed = true; }
                            }
                            if (!placed) {
                                // if it's a hold try to replace previous short note
                                if (r.isHold) {
                                    for (int j = scheduled.size()-1; j >=0; j--) {
                                        if (scheduled.get(j).timeMs == r.timeMs && !scheduled.get(j).isHold) {
                                            scheduled.set(j, new ScheduledNote(r.timeMs, Note.Direction.values()[lane], r.len, r.isHold, r.holdFrames));
                                            lastTime[lane] = r.timeMs; continue;
                                        }
                                    }
                                }
                                // skip if completely crowded
                                continue;
                            }
                        }

                        scheduled.add(new ScheduledNote(r.timeMs, Note.Direction.values()[lane], r.len, r.isHold, r.holdFrames));
                        lastTime[lane] = r.timeMs;

                    } else {
                        // chord or simultaneous notes: sort by pitch and distribute across lanes
                        Collections.sort(group, new Comparator<RawNote>() { public int compare(RawNote a, RawNote b) { return Integer.compare(a.pitch, b.pitch); } });
                        int assigned = 0;
                        for (RawNote r : group) {
                            int lane = assigned % 4;
                            // try to find a lane that satisfies minGap
                            boolean placed = false;
                            for (int shift = 0; shift < 4 && !placed; shift++) {
                                int l = (lane + shift) % 4;
                                if (r.timeMs - lastTime[l] >= minGap) {
                                    scheduled.add(new ScheduledNote(r.timeMs, Note.Direction.values()[l], r.len, r.isHold, r.holdFrames));
                                    lastTime[l] = r.timeMs;
                                    placed = true;
                                }
                            }
                            assigned++;
                            // if can't place, skip this note
                        }
                    }
                }

                // start sequencer for timing (audio may be silent without synth)
                try {
                    sequencer = MidiSystem.getSequencer(false);
                    if (sequencer != null) {
                        sequencer.open();
                        sequencer.setSequence(seq);
                        // attempt to open a Synthesizer and route sequencer to it so audio plays even without system synth
                        try {
                            Synthesizer synth = MidiSystem.getSynthesizer();
                            if (synth != null) {
                                synth.open();
                                try {
                                    sequencer.getTransmitter().setReceiver(synth.getReceiver());
                                } catch (Exception exx) {
                                    // some sequencers may not support transmitter/receiver routing; ignore
                                }
                            }
                        } catch (Exception ex3) {
                            // ignore if synthesizer unavailable
                        }
                        sequencer.start();
                        songStartTime = System.currentTimeMillis();
                    }
                } catch (Exception ex2) {
                    ex2.printStackTrace();
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // helper: convert ticks to ms using tempo map
        private static class Tempo { long tick; int mpq; Tempo(long t, int m) { tick = t; mpq = m; } }

        private long ticksToMs(long tick, List<Tempo> tempos, int resolution) {
            long prevTick = 0;
            long us = 0;
            int currentMpq = 500000;
            for (Tempo tt : tempos) {
                if (tick <= tt.tick) break;
                long dt = Math.min(tick, tt.tick) - prevTick;
                us += (dt * (long)currentMpq) / resolution;
                prevTick = tt.tick;
                currentMpq = tt.mpq;
            }
            if (prevTick < tick) {
                long dt = tick - prevTick;
                us += (dt * (long)currentMpq) / resolution;
            }
            return us / 1000;
        }

        // handles press with hold detection: if a hold note is within window start holding instead of penalizing
        private void handlePress(Note.Direction dir) {
            final int tolerance = 24;
            final int holdTolerance = 48;
            // first check for a non-hold tappable note
            for (Note n : notes) {
                if (n.dir != dir) continue;
                double dist = n.distanceToCenterEdge(getWidth(), getHeight());
                if (!n.isHold && Math.abs(dist) <= tolerance) {
                    tryHit(dir);
                    return;
                }
            }
            // next check for a hold note in range -> start holding (no Bad)
            for (Note n : notes) {
                if (n.dir != dir) continue;
                double dist = n.distanceToCenterEdge(getWidth(), getHeight());
                if (n.isHold && Math.abs(dist) <= holdTolerance) {
                    holding[dir.ordinal()] = true;
                    // do not call tryHit; freeze/muting handled in game loop
                    return;
                }
            }
            // otherwise treat as a normal tap (may be a Bad)
            tryHit(dir);
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
                int hintW = hintFm.stringWidth("\u2190");
                g2.setColor(new Color(255,255,255,120));
                g2.drawString("\u2190", 10, getHeight()/2 + hintFm.getAscent()/2 - 4);
                g2.drawString("\u2192", getWidth()-10-hintW, getHeight()/2 + hintFm.getAscent()/2 - 4);
                int upW = hintFm.stringWidth("\u2191");
                g2.drawString("\u2191", getWidth()/2 - upW/2, 30);
                g2.drawString("\u2193", getWidth()/2 - upW/2, getHeight()-10);

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
        double speed;
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

        // overloaded constructor to allow custom spawn offset to avoid overlaps
        public Note(Direction dir, int length, double speed, int panelW, int panelH, boolean isHold, int holdRequired, int spawnOffsetOverride) {
            this.dir = dir;
            this.length = length;
            this.speed = speed;
            this.isHold = isHold;
            this.holdRequired = isHold ? holdRequired : 0;
            this.holdProgress = 0;
            switch (dir) {
                case LEFT:
                    x = -spawnOffsetOverride; y = panelH / 2.0; break;
                case RIGHT:
                    x = panelW + spawnOffsetOverride; y = panelH / 2.0; break;
                case UP:
                    y = -spawnOffsetOverride; x = panelW / 2.0; break;
                default:
                    y = panelH + spawnOffsetOverride; x = panelW / 2.0; break;
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
