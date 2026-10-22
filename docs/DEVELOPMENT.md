# Development

## Stack

- Kotlin 2.1, Jetpack Compose (BOM 2024.12) with Material 3, Navigation Compose
- OkHttp 4 for HTTP and the WebSocket, kotlinx.serialization for JSON
- Coil for images, Markwon for Markdown (tables, task lists, strikethrough, links, images)
- WorkManager for background notification checks, DataStore for settings, bounded private SQLite snapshots for cached reads
- `minSdk` 26 (Android 8.0), `targetSdk` and `compileSdk` 35

## Layout

```
app/src/main/java/com/bu/paperclip/
├── MainActivity.kt, PaperclipApp.kt
├── net/
│   ├── ApiClient.kt      HTTP client, cookie bridge to the WebView, auth state, sign-in
│   └── LiveEvents.kt     company WebSocket with reconnect backoff
├── data/
│   ├── Models.kt         @Serializable API models
│   ├── Repo.kt           one function per endpoint
│   ├── AppState.kt       signed-in user, company, agents, people, projects, badges
│   ├── IssueStore.kt     foreground task mirror, paged deltas and full reconciliation
│   ├── ReadCache.kt      account-scoped durable read snapshots and shared in-flight GETs
│   ├── RefreshQueue.kt   non-cancelling, conflated refresh requests
│   ├── Paging.kt         keyset pagination with stalled-cursor protection
│   ├── TaskGraph.kt      filters, trees, rollups and Next up over the mirror
│   ├── Transcript.kt     run log → transcript entries, per adapter
│   └── Settings.kt       DataStore-backed settings
├── notify/NotifyWorker.kt  background check of the attention feed
├── ui/
│   ├── AppRoot.kt        boot, sign-in phases and the tab shell
│   ├── Nav.kt            routes, tabs, in-app link handling and back-to-tab
│   ├── components/       shared rows, chips, bars, pills, avatars, Markdown, task views
│   ├── screens/          one file (or a few) per screen
│   └── theme/            colours, type scale, shapes, fonts
└── util/
    ├── Time.kt           timestamp parsing and relative times
    └── Schedule.kt       routine cron presets: parse, describe, validate
```

## How it talks to Paperclip

The app uses the same `/api` endpoints as the web UI. [`Repo.kt`](../app/src/main/java/com/bu/paperclip/data/Repo.kt) lists every call it makes.

**Requests.** Every request carries an `Origin` header matching the server, which Paperclip requires for cookie-authenticated writes. Redirects aren't followed. A redirect, an HTML response or a `401` means a session or a sign-in proxy got in the way, so the app returns to sign-in.

**Request bodies.** `jsonOf(...)` leaves out Kotlin `null`s, because the server rejects nulls in most optional fields. To clear a field (for example, to unassign a task), pass `JsonNull` explicitly.

**Sign-in.** `Api.gateHost()` probes `/api/health`. If a proxy such as Cloudflare Access answers instead, a WebView opens on that URL so you can pass the proxy. After that, `POST /api/auth/sign-in/email` signs in to Paperclip natively. OkHttp's cookie jar is backed by the WebView's `CookieManager`, so both share the same proxy and Paperclip cookies. With a board API key, the app sends it as a bearer token and skips cookies.

**Live updates.** `LiveEvents` keeps one WebSocket open to `/api/companies/{id}/events/ws` while the app is in the foreground and reconnects with backoff. It bumps an `epoch` on every reconnect so screens can refetch what they missed. While the socket is down, `LiveRefresh` polls active screens every 20 seconds. RefreshQueue lets a slow request finish and coalesces invalidations into one follow-up. Network availability retries a disconnected socket and leaves healthy connections alone; event-buffer overflow requests reconciliation. The socket only accepts cookie sessions, so it's skipped in API-key mode.

**The task mirror.** `IssueStore` loads every task once using keyset paging (`sortField=id`, `afterId`, `limit=500`, `includeBlockedBy=true`). After that it drains all `updatedSince` pages using a conservative server-time watermark. Events, reconnects and disconnected polling request deltas; deletion events and a five-minute reconciliation interval request full snapshots. Interactive syncing stops in the background. Cached tasks appear before network sync, and first-use pages publish incrementally. Quick edits are applied locally first and rolled back if the server rejects them. `TaskGraph` builds the list groups, the tree (with ancestors kept as dimmed context), rollups, Next up and In flight from that map in memory.

**Decisions.** The Decisions tab reads the same attention feed as the web UI's Decisions page (`/companies/{id}/attention?includeDismissed=true&all=true`) and groups it client-side the way the web does: decide-by due today or overdue (in UTC days, like the server's badge count), created today, then the rest. Open agent decisions come from `/companies/{id}/decisions?status=open`; choosing an option posts `/decisions/{id}/decide` with a fresh idempotency key. Decide-by goes through `PUT /companies/{id}/decision-triage/{sourceKind}/{sourceId}`, which is shared; dismiss and snooze use the per-user `inbox-dismissals`. The tab badge is the feed's `deskBadgeCount`.

**Markdown tables.** `Markdown()` splits top-level GFM tables out of the text (`splitMarkdownTables`, which skips code fences) and draws them with `MarkdownTable`: columns sized to their content up to 260 dp, wrapping beyond that, inside a horizontal scroll. The prose around them still goes through Markwon. Tables nested in lists or quotes fall back to Markwon's own table rendering.

**Transcripts.** Run logs are NDJSON records of `{ts, stream, chunk, seq}`. `TranscriptBuilder` reassembles stdout lines and parses them by adapter: Claude stream-json, Codex `item.*` events, ACP `acpx.*` events and Antigravity `step_update`s. Unknown JSON events are hidden. Anything else shows as plain log lines.

## Design

The look follows Paperclip's web UI: neutral, monochrome chrome, with colour reserved for meaning (status, priority, live work, errors) so it stands out wherever it appears.

- **Type.** Inter for the interface and JetBrains Mono for code, logs, commands and model names, bundled in `res/font` and subset to Latin, Greek, Cyrillic and common symbols. Other scripts fall back to the system font. Use the `MaterialTheme.typography` scale rather than ad-hoc sizes, and `TABULAR` figures for numbers that change or line up.
- **Colour.** `Theme.kt` defines the light and dark schemes and `LocalStatusColors` for semantic colours. Selected and primary elements invert (light on dark, dark on light) instead of using an accent colour.
- **Shared pieces** live in `ui/components`:
  - `AppFilterChip` for filters and choices, `PropertyChip` for a task's editable properties
  - `AppBottomBar` for the tab bar, `BarTitle` for two-line top bar titles
  - `SectionHeader` and `SectionAction` for list sections, `Card` for grouped content
  - `SkeletonList`, `EmptyState` and `ErrorState` for loading, empty and failed screens
  - `BrandMark` for the logo tile

  Use these instead of the stock Material components, so new screens match the rest.
- **Launch screen and icon.** `res/drawable/launch_screen.xml` (with light and dark colours in `values` and `values-night`) shows the mark while the app starts. The launcher icon is an adaptive icon with a monochrome layer.

## Building

```bash
echo "sdk.dir=/path/to/android/sdk" > local.properties
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Tests

```bash
./gradlew testDebugUnitTest
```

- `TaskGraphTest` covers filters, trees, rollups and Next up on small hand-made graphs.
- `FixtureDecodeTest` and the snapshot cases in `TaskGraphTest` run against captured API responses. Captures contain private data, so they aren't committed. Set `PAPERCLIP_FIXTURES` to a directory of captured JSON to run them; without it, they're skipped.

## Running against the demo server

[`tools/demo-server`](../tools/demo-server) serves a made-up company with no setup:

```bash
python3 tools/demo-server/demo_server.py --port 3100
adb reverse tcp:3100 tcp:3100
```

Debug builds accept a `setupUrl` intent extra (and an optional `setupKey` board key), so you can skip the setup screen:

```bash
adb shell am start -n com.bu.paperclip/.MainActivity --es setupUrl http://localhost:3100
```

The demo server accepts any sign-in and keeps writes in memory until it restarts. It includes an open agent decision (with a comparison table), a review comment with a wide table on LUM-148, and editable routines, so Decisions, tables and the routine editor can all be tried. It also accepts the live WebSocket, so the connection dot goes green, but it never pushes events.

## Regenerating the screenshots

The screenshots in `docs/screenshots` come from an Android 11 emulator (1080×2340) pointed at the demo server.

1. Start the demo server. To show agent avatars instead of initials, add `--avatars-from http://<a Paperclip server>`; avatars are Paperclip's artwork and aren't bundled.
2. Clear the app's data (`adb shell pm clear com.bu.paperclip`) and launch it with `setupUrl` as above.
3. Put the status bar in demo mode for a clean clock and icons:

   ```bash
   adb shell settings put global sysui_demo_allowed 1
   demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@"; }
   demo enter
   demo clock -e hhmm 0941
   demo battery -e level 100 -e plugged false
   demo network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4
   demo notifications -e visible false
   ```

   Switching the system theme (`adb shell cmd uimode night yes|no`) restarts the status bar, so run these again afterwards.
4. Capture with `adb exec-out screencap -p > shot.png`, then scale to 540 px wide.

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`. A higher `versionCode` lets the new APK install over the old one.
2. Build: `./gradlew assembleDebug`.
3. Tag and publish the APK:

   ```bash
   cp app/build/outputs/apk/debug/app-debug.apk paperclip-android-<version>-debug.apk
   gh release create v<version> paperclip-android-<version>-debug.apk --title "v<version>" --notes "…"
   ```

Debug builds share the same debug signing key on one machine. Build from a different machine and the APK won't install over an existing one until you uninstall it first.

## Keeping private data out

The repo is public. Don't commit server URLs, API keys, captured API responses or screenshots of a real company. Use the demo server for anything that ends up in the repo.

## Performance verification

See [CACHE_REFRESH.md](CACHE_REFRESH.md) for the cache boundaries and refresh regression tests. Robolectric UI/storage tests complement emulator testing; they do not measure physical-device frame times.
