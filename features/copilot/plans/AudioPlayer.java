import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Simple audio player using javax.sound.sampled. Supports WAV files via Clip.
 * Usage:
 *   AudioPlayer p = new AudioPlayer();
 *   p.load(new File("sound.wav"));
 *   p.play();
 */
public class AudioPlayer {
    private Clip clip;
    private FloatControl gainControl;

    public void load(File file) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            loadFromStream(ais);
        }
    }

    public void load(URL resourceUrl) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(resourceUrl)) {
            loadFromStream(ais);
        }
    }

    private void loadFromStream(AudioInputStream ais) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        AudioFormat baseFormat = ais.getFormat();
        AudioFormat decodedFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(), 16, baseFormat.getChannels(),
                baseFormat.getChannels() * 2, baseFormat.getSampleRate(), false);
        try (AudioInputStream din = AudioSystem.getAudioInputStream(decodedFormat, ais)) {
            if (clip != null && clip.isOpen()) clip.close();
            clip = AudioSystem.getClip();
            clip.open(din);
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            } else {
                gainControl = null;
            }
        }
    }

    public void play() {
        if (clip == null) return;
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    public void loop(int count) {
        if (clip == null) return;
        clip.loop(count);
    }

    public void stop() {
        if (clip == null) return;
        clip.stop();
        clip.setFramePosition(0);
    }

    public boolean isPlaying() {
        return clip != null && clip.isRunning();
    }

    /**
     * Set volume in range [0.0, 1.0]. If control not supported, this is a no-op.
     */
    public void setVolume(float volume) {
        if (gainControl == null) return;
        float min = gainControl.getMinimum();
        float max = gainControl.getMaximum();
        float gain = min + (max - min) * Math.max(0f, Math.min(1f, volume));
        gainControl.setValue(gain);
    }

    public void close() {
        if (clip != null) {
            clip.close();
            clip = null;
            gainControl = null;
        }
    }
}
