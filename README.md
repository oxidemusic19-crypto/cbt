# CBT — Chords By Thought

Native Android music player built with Kotlin, Jetpack Compose and Media3.

## Open in Android Studio
1. Download this repository as a ZIP and extract it.
2. Open the extracted folder containing `settings.gradle.kts`.
3. Use JDK 17 and Android SDK Platform 35.
4. Let Gradle sync finish, connect an Android device, then Run.

## Included
- MediaStore local music scan
- Search by title, artist and album
- Media3 playback service, notification and playback queue
- Android Equalizer, BassBoost and Virtualizer (device-dependent)
- Online source-separation upload client

## AI Stem Studio limitation
Stem separation requires a separately hosted HTTPS service at `{endpoint}/separate`. The app uploads the selected file as multipart field `file` and displays the server response. No AI model or backend is bundled; returned stems are not yet downloaded, previewed or exported.

Android audio-effect support varies by phone and audio output. Unsupported effects are not simulated.
