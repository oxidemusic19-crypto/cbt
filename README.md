# CBT — Chords By Thought (Android)

A native Kotlin + Jetpack Compose music-player starter project for Android Studio.

## Included in this source project

- Local audio library scan through Android MediaStore
- Search by song title and artist
- Media3 ExoPlayer queue, previous/next, shuffle and repeat
- Background playback through MediaSessionService and Android media notification integration
- Mini-player and full now-playing screen with seek control
- Device audio effects integration: Equalizer, BassBoost and Virtualizer (actual support varies by phone/output)
- Online stem-separation upload client with HTTPS endpoint and optional Bearer token

## Important: Online AI Stem Studio needs a real server

The Android app does not bundle a Demucs model or pretend to separate stems locally. The Studio screen uploads the selected audio to the configured server at `POST {endpoint}/separate` as multipart form data, field name `file`. It sends `Authorization: Bearer <API_KEY>` when a key is provided.

The endpoint should return HTTP 200 with JSON, for example:

```json
{
  "job_id": "job-123",
  "status": "completed",
  "stems": {
    "vocals": "https://your-server.example/files/job-123/vocals.wav",
    "drums": "https://your-server.example/files/job-123/drums.wav",
    "bass": "https://your-server.example/files/job-123/bass.wav",
    "other": "https://your-server.example/files/job-123/other.wav"
  }
}
```

A compatible backend should validate uploads, enforce file-size/time limits, run a real source-separation model (for example Demucs/HTDemucs), store output files privately, and return HTTPS download URLs. The current app displays the server response; it does not yet download, audition, edit or export returned stems. A backend is not included because a pretrained separation model and GPU/CPU hosting are substantial separate dependencies and there is no user-provided server endpoint or credential.

## Open and build

1. Extract the ZIP.
2. In Android Studio choose **Open** and select the extracted `CBT-Complete` folder (the folder containing `settings.gradle.kts`).
3. Allow Gradle sync. Use JDK 17 and install Android SDK Platform 35 if Android Studio asks.
4. Connect the Android phone with USB debugging enabled, select it in the device dropdown, then press **Run**.
5. Grant the audio permission when asked. Open Library and tap **SCAN MY MUSIC**.
6. To make an APK: **Build → Build Bundle(s) / APK(s) → Build APK(s)**. Android Studio will show the output location when finished.

## Project details

- Application ID: `com.cbt.musicplayer`
- Minimum Android: 8.0 (API 26)
- Compile/Target SDK: 35
- Kotlin: 2.0.21
- Android Gradle Plugin: 8.7.3
- Media3: 1.5.1

## Current limitations (not hidden)

- Favorites are not yet wired to a dedicated screen or persisted as a full library feature.
- User-created persistent playlists and folder/album/artist browsing are not implemented yet.
- The EQ uses Android platform audio effects; the number of bands and availability depend on the device. The app does not fake unavailable effects.
- Album artwork retrieval, sleep timer, per-track stem playback/editor, and stem export are not implemented in this source version.
- The online Stem Studio needs the compatible HTTPS backend described above. The app cannot produce stems until that server is configured.

This source is provided as a buildable project baseline, not a claim that the external AI service has already been deployed or tested.
