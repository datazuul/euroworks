package com.datazuul.euroworks.apps.euroradio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

/**
 * Playback thread that downloads raw stream data, cushions it via
 * BufferedRadioInputStream,
 * decodes MP3 using SPI, and plays back raw PCM.
 */
public class RadioPlaybackThread extends Thread {
    public enum PlaybackState {
        CONNECTING,
        BUFFERING,
        PLAYING,
        STOPPED,
        ERROR
    }

    private final RadioStation station;
    private final BiConsumer<PlaybackState, String> stateListener;

    private volatile boolean running = true;
    private volatile PlaybackState state = PlaybackState.CONNECTING;
    private volatile float volume = 0.8f; // Default 80%
    private volatile int bitrate = 0;
    private volatile int sampleRate = 0;

    private HttpURLConnection connection = null;
    private BufferedRadioInputStream bufferedStream = null;
    private SourceDataLine audioLine = null;

    public RadioPlaybackThread(RadioStation station, BiConsumer<PlaybackState, String> stateListener) {
        this.station = station;
        this.stateListener = stateListener;
        setName("RadioPlayback-" + station.getName());
    }

    public synchronized void setVolume(float vol) {
        this.volume = vol;
        updateLineVolume();
    }

    public synchronized float getVolume() {
        return volume;
    }

    public PlaybackState getPlaybackState() {
        return state;
    }

    public int getBitrate() {
        return bitrate;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void stopPlayback() {
        running = false;
        if (bufferedStream != null) {
            try {
                bufferedStream.close();
            } catch (IOException ignored) {
            }
        }
        if (connection != null) {
            connection.disconnect();
        }
        if (audioLine != null) {
            audioLine.stop();
            audioLine.close();
        }
        updateState(PlaybackState.STOPPED, null);
    }

    private void updateState(PlaybackState newState, String errorMsg) {
        this.state = newState;
        if (stateListener != null) {
            stateListener.accept(newState, errorMsg);
        }
    }

    @Override
    public void run() {
        updateState(PlaybackState.CONNECTING, null);

        // Resolve stream URL in case it's a playlist (.m3u/.pls)
        String streamUrlStr = station.getUrl();
        try {
            if (streamUrlStr.toLowerCase().contains(".m3u") || streamUrlStr.toLowerCase().contains(".pls")) {
                URL playlistUrl = URI.create(streamUrlStr).toURL();
                var urls = PlaylistParser.parse(playlistUrl);
                if (!urls.isEmpty()) {
                    streamUrlStr = urls.get(0).toString();
                }
            }
        } catch (Exception ex) {
            System.err.println("EuroRadio: Playlist resolution failed: " + ex.getMessage());
        }

        // 1. Start downloader thread
        bufferedStream = new BufferedRadioInputStream(16); // 16 chunks pre-buffered
        final String targetUrl = streamUrlStr;

        Thread downloaderThread = Thread.startVirtualThread(() -> {
            int retries = 0;
            final int maxRetries = 5;

            while (running && retries < maxRetries) {
                InputStream is = null;
                HttpURLConnection conn = null;
                try {
                    URL url = URI.create(targetUrl).toURL();
                    conn = (HttpURLConnection) url.openConnection();
                    connection = conn;
                    conn.setRequestProperty("User-Agent", "EuroWorks-EuroRadio/1.0");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(20000); // 20 seconds read timeout
                    conn.connect();

                    int code = conn.getResponseCode();
                    if (code >= 400) {
                        throw new IOException("HTTP Error " + code);
                    }

                    is = new BufferedInputStream(conn.getInputStream());
                    byte[] buffer = new byte[4096];
                    int read;

                    // Reset retries once we successfully connected and started reading
                    retries = 0;

                    while (running && (read = is.read(buffer)) != -1) {
                        byte[] chunk = new byte[read];
                        System.arraycopy(buffer, 0, chunk, 0, read);
                        if (!bufferedStream.push(chunk)) {
                            break; // Stream was closed
                        }
                    }

                    if (running) {
                        throw new IOException("Server closed connection");
                    }
                } catch (Exception ex) {
                    if (running) {
                        retries++;
                        System.err.println("EuroRadio: Downloader thread error (attempt " + retries + "/" + maxRetries + "): " + ex.getMessage());
                        if (retries >= maxRetries) {
                            updateState(PlaybackState.ERROR, "Connection failed: " + ex.getMessage());
                        } else {
                            try {
                                Thread.sleep(2000); // wait 2 seconds before retrying
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ignored) {
                        }
                    }
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
            bufferedStream.markFinished();
        });

        // 2. Main Playback loop (Decoding and Playing)
        AudioInputStream mp3Stream = null;
        AudioInputStream decodedStream = null;

        try {
            // Wait briefly for pre-buffering to transition to BUFFERING status
            updateState(PlaybackState.BUFFERING, null);

            // Wrap in BufferedInputStream to support mark() and reset() which is required
            // by AudioSystem SPI readers.
            InputStream markedStream = new BufferedInputStream(bufferedStream);
            mp3Stream = AudioSystem.getAudioInputStream(markedStream);
            AudioFormat baseFormat = mp3Stream.getFormat();

            // Extract metadata (bitrate, sample rate) from format properties
            Map<String, Object> properties = baseFormat.properties();
            if (properties != null) {
                Object br = properties.get("bitrate");
                if (br instanceof Integer) {
                    bitrate = (Integer) br / 1000; // kbps
                }
            }
            sampleRate = (int) baseFormat.getSampleRate();

            // Set up target decoding format to PCM Signed
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16, // bit depth
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false // little-endian
            );

            decodedStream = AudioSystem.getAudioInputStream(targetFormat, mp3Stream);

            // Get audio line
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, targetFormat);
            if (!AudioSystem.isLineSupported(info)) {
                throw new IOException("PCM Audio output line format not supported");
            }

            audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(targetFormat, 16384 * 2);
            updateLineVolume();
            audioLine.start();

            updateState(PlaybackState.PLAYING, null);

            byte[] buffer = new byte[4096];
            int read;

            while (running && (read = decodedStream.read(buffer)) != -1) {
                int written = 0;
                while (running && written < read) {
                    int n = audioLine.write(buffer, written, read - written);
                    if (n <= 0)
                        break;
                    written += n;
                }
            }

            if (running) {
                audioLine.drain();
            }

        } catch (Exception ex) {
            if (running) {
                System.err.println("EuroRadio: Playback error: " + ex.getMessage());
                updateState(PlaybackState.ERROR, ex.getMessage());
            }
        } finally {
            running = false;
            if (audioLine != null) {
                try {
                    audioLine.stop();
                } catch (Exception ignored) {
                }
                try {
                    audioLine.close();
                } catch (Exception ignored) {
                }
            }
            if (decodedStream != null) {
                try {
                    decodedStream.close();
                } catch (Exception ignored) {
                }
            }
            if (mp3Stream != null) {
                try {
                    mp3Stream.close();
                } catch (Exception ignored) {
                }
            }
            if (bufferedStream != null) {
                try {
                    bufferedStream.close();
                } catch (Exception ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }

            if (state != PlaybackState.ERROR) {
                updateState(PlaybackState.STOPPED, null);
            }
        }
    }

    private void updateLineVolume() {
        if (audioLine == null)
            return;
        try {
            if (audioLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) audioLine.getControl(FloatControl.Type.MASTER_GAIN);
                float vol = Math.max(0.0f, Math.min(1.0f, volume));
                // Convert linear scale (0.0 to 1.0) to decibels: dB = 20 * log10(vol)
                float dB;
                if (vol == 0.0f) {
                    dB = gainControl.getMinimum(); // Mute
                } else {
                    dB = (float) (Math.log10(vol) * 20.0);
                    dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
                }
                gainControl.setValue(dB);
            }
        } catch (Exception ex) {
            System.err.println("EuroRadio: Volume control not supported: " + ex.getMessage());
        }
    }
}
