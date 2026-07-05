package com.datazuul.euroworks.apps.euroradio;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An InputStream that buffers network stream data in a LinkedBlockingQueue.
 * Handles "pre-buffering" on start to avoid audio stuttering.
 */
public class BufferedRadioInputStream extends InputStream {
    private final LinkedBlockingQueue<byte[]> queue;
    private final int targetPrebufferChunks;

    private byte[] currentChunk = null;
    private int currentIdx = 0;

    private volatile boolean finished = false;
    private volatile boolean closed = false;
    private volatile boolean prebuffering = true;

    public BufferedRadioInputStream(int targetPrebufferChunks) {
        this.queue = new LinkedBlockingQueue<>(128); // Cap buffer to avoid memory issues (e.g. 128 * 4KB = 512KB ~ 32
                                                     // seconds)
        this.targetPrebufferChunks = targetPrebufferChunks;
    }

    /**
     * Push a raw chunk of audio data from the network downloader.
     */
    public boolean push(byte[] chunk) {
        if (closed)
            return false;
        try {
            // Offer blocks if queue is full
            return queue.offer(chunk, 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Inform the stream that the downloader is finished (e.g. EOF or disconnect).
     */
    public void markFinished() {
        this.finished = true;
    }

    /**
     * Returns the current fill level of the buffer (0.0 to 1.0).
     */
    public double getBufferFillLevel() {
        return (double) queue.size() / 128.0;
    }

    public boolean isPrebuffering() {
        return prebuffering;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b, 0, 1);
        if (n <= 0)
            return -1;
        return b[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed)
            throw new IOException("Stream closed");

        try {
            if (prebuffering) {
                // Wait until we have enough chunks or downloader finished
                while (queue.size() < targetPrebufferChunks && !finished && !closed) {
                    Thread.sleep(50);
                }
                prebuffering = false;
            }

            if (currentChunk == null || currentIdx >= currentChunk.length) {
                currentChunk = null;
                currentIdx = 0;

                while (currentChunk == null) {
                    if (finished && queue.isEmpty()) {
                        return -1; // EOF
                    }
                    if (closed) {
                        return -1;
                    }
                    // Poll queue with timeout to check for finished flag frequently
                    currentChunk = queue.poll(100, TimeUnit.MILLISECONDS);
                }
            }

            int available = currentChunk.length - currentIdx;
            int toCopy = Math.min(len, available);
            System.arraycopy(currentChunk, currentIdx, b, off, toCopy);
            currentIdx += toCopy;
            return toCopy;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Playback reading interrupted", ex);
        }
    }

    @Override
    public void close() throws IOException {
        this.closed = true;
        this.queue.clear();
    }
}
