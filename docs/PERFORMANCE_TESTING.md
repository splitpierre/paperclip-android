# Repeatable network simulation

Start the normal demo server from the project directory:

```bash
python3 tools/demo-server/demo_server.py --port 3100 --trace-requests
adb reverse tcp:3100 tcp:3100
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.bu.paperclip/.MainActivity --es setupUrl http://localhost:3100
```

Use any demo email/password if sign-in is needed. Use only demo data for screenshots and diagnostics.

For latency, bandwidth and disconnected-WebSocket testing, restart the demo server at the same address:

```bash
python3 tools/demo-server/demo_server.py --port 3100 --delay-ms 600 --bytes-per-second 65536 --disable-websocket --trace-requests
```

This injects server-response latency and per-response bandwidth limits, not real packet loss or a WireGuard tunnel. `adb reverse` may bypass emulator modem shaping; use these explicit server controls for repeatable application-layer delay tests.

Verify:

1. Cold start and Home/Inbox/Tasks/Agents navigation on the normal server.
2. Open an issue, type a comment draft, scroll its Markdown, inspect a run and switch task filters/views.
3. Repeat with the impaired server. Navigate back to previously visited screens and verify saved content appears before fresh data.
4. Stop the demo server, force-stop/reopen the app without clearing data, and verify cached startup/navigation. Resume the server and verify revalidation.
5. Background the app for more than 20 seconds. Confirm request logs do not show continued foreground polling; separately scheduled notifications may still run.
6. Verify an empty company shows an empty state, and reconnects recover updates.
7. Reset `adb shell dumpsys gfxinfo com.bu.paperclip reset`, exercise scrolling/typing, then capture `adb shell dumpsys gfxinfo com.bu.paperclip framestats`. Use Perfetto or Macrobenchmark and a release/profileable build for credible frame-time comparisons. Emulator timing is not a physical-phone benchmark.

The debug APK is locally signed. The optimized release APK is unsigned unless a release signing configuration is supplied; do not publish it as an installable production release without signing and device verification.


These controls simulate application-layer response delay and bandwidth, not WireGuard packet loss. Cached-startup scenarios require the separate cache/refresh contribution.
