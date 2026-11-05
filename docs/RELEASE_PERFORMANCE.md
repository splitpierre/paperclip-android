# Optimized release builds

Enable R8 and resource shrinking for release only. Precise Markwon rules cover absent optional SVG/GIF decoders; bitmap images remain supported.

Verify with `./gradlew :app:assembleRelease`. The APK is unsigned without a release signing configuration. Profile scroll frame times on a physical device with the same signed release configuration before and after the change; JVM tests cannot measure frame-time improvement.
