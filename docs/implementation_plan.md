# MusicBrainz CD Lookup + Winamp-Style Playlist Panel

Add track metadata lookup via MusicBrainz API and display track titles/artists in a docked playlist panel below the CD player, styled after the classic Winamp playlist.

## Proposed Changes

### [MODIFY] [EuroCDPlayer.java](file:///c:/Users/ralfe/PROJECTS/github.com/datazuul/euroworks/src/main/java/com/datazuul/euroworks/apps/EuroCDPlayer.java)

#### 1. New Fields
- `String[] trackTitles` — track title from MusicBrainz (fallback: "Track 01")
- `String albumTitle` — album name
- `String albumArtist` — artist name
- `PlaylistPanel playlistPanel` — the docked playlist

#### 2. MusicBrainz Disc ID Calculation (`computeDiscId()`)
- Read TOC: first track, last track, lead-out LBA, per-track start LBAs
- Lead-out = `trackStartLbas[last] + trackSectorCounts[last]`
- All offsets +150 for lead-in
- Format as hex → SHA-1 → custom Base64 (`.` for `+`, `_` for `/`, no `=`)
- Pure Java: `java.security.MessageDigest` + `java.util.Base64`

#### 3. MusicBrainz API Lookup (`lookupMusicBrainz()`)
- GET `https://musicbrainz.org/ws/2/discid/{discid}?inc=recordings+artists&fmt=json`
- Set `User-Agent: EuroWorks/1.0 (https://github.com/datazuul/euroworks)` (MusicBrainz requires this)
- Parse JSON manually (no external library) — extract `releases[0].title`, `releases[0].artist-credit[0].name`, `releases[0].media[0].tracks[*].recording.title`
- Run on background thread to avoid blocking UI
- Fallback gracefully if no match or offline

#### 4. Winamp-Style Playlist Panel (inner class `PlaylistPanel`)
- Dark background (`Color(29, 33, 40)`) similar to Winamp
- Green text for track entries (`Color(0, 220, 0)`)
- White highlighted text for the currently playing track
- Each row: `"01. Artist - Track Title    4:32"` format
- Scrollable via `JScrollPane`
- Click a track → `selectTrack(i)` + start playback
- Docked below the CD player controls

#### 5. Layout Changes
- Increase frame HEIGHT to ~520 to accommodate the playlist
- Add playlist panel to `BorderLayout.SOUTH` of the content pane
- Playlist height ~200px with scroll

## Verification Plan

### Automated Tests
- `.\mvnw.cmd clean package` — verify compilation

### Manual Verification
- Insert a CD and launch the CD Player
- Verify track titles appear in the playlist panel
- Verify clicking a track starts playback of that track
