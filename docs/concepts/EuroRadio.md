# EuroRadio Design Concept

This document describes the design and implementation of the `EuroRadio` internet radio streaming client application within EuroWorks.

## Architectural Overview

EuroRadio is a lightweight internet radio stream player. It fetches worldwide station lists from the public, community-maintained Radio-Browser API, plays MP3 and AAC/AAC+ audio streams, and stores local user channel configurations.

### Key Components

1. **Audio Decoding SPI Stack**:
   - Standard Java Sound API (`javax.sound.sampled`) combined with:
     - `jlayer`: A pure-Java MP3 decoder.
     - `mp3spi` & `tritonus-share`: Service Provider Interface (SPI) implementations that register MP3 audio stream format conversion with the standard Java `AudioSystem`.
     - `javasound-aac`: Service Provider Interface (SPI) implementation that registers AAC/HE-AAC (AAC+) audio stream format conversion with the standard Java `AudioSystem`.

2. **Autopanning / Buffering System**:
   - A dedicated `DownloaderThread` pulls stream chunks from network sockets.
   - Pushes raw stream data into a bounded `LinkedBlockingQueue<byte[]>` buffer (cap ~256KB).
   - A custom `InputStream` reads from the queue. If empty, it blocks during play.
   - A buffering state holds playback on startup (or when network lag occurs) until the queue contains a minimum of 64KB (25% capacity).

3. **Radio-Browser API Client**:
   - Dynamically resolves API servers by querying the dynamic DNS A records of `all.api.radio-browser.info`.
   - Resolves the first server to its canonical host name for HTTPS queries.
   - Integrates `/json/stations/search` and `/json/stations/topclick`.
   - Sends a custom, descriptive `User-Agent` (e.g. `EuroWorks-EuroRadio/1.0`) as required by the API terms.

4. **UI Layout**:
   - Follows the EuroWorks retro design theme.
   - LCD-style display showing playback details (Title, status, codec details, buffer level).
   - Volume slider controlling master line gain.
   - Custom Station manager with add/delete buttons, saving to `~/.euroworks/euroradio/channels.json` via Jackson.

## Detailed Audio Flow

```
[Radio Stream URL]
        │
        ▼ (Downloader Thread)
 [Read 4KB chunks]
        │
        ▼ (Pushed into)
┌────────────────────────────────┐
│ LinkedBlockingQueue<byte[]>    │  <-- Buffering Cushion (max 64 chunks = 256KB)
└────────────────────────────────┘
        │
        ▼ (Read on demand by decoder)
 [Custom InputStream]
        │
        ▼
 [AudioSystem.getAudioInputStream]  <-- MP3/AAC Decoders (MP3SPI / Javasound-AAC)
        │
        ▼ (PCM Raw Bytes)
 [SourceDataLine Write]             <-- Speaker Playback
```
