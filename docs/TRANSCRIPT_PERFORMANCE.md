# Bound transcript rendering and preserve formatted Markdown height

- Transcript rendering retains at most 5,000 entries/raw lines and a bounded recent sequence history; a notice appears if earlier rendered history was trimmed. The server log is unchanged. Buffered live chunks during catch-up are capped, with HTTP recovery on overflow.

Run `./gradlew :app:testDebugUnitTest` with a complete JDK 17+ and Android SDK. Robolectric tests verify storage and Compose behavior, not physical-device frame rates.

Transcript parsing and snapshots run off Main behind a mutex. First Markdown display renders formatted spans synchronously to avoid a raw-text height swap; later edits keep previous spans while rendering off Main. This trades initial parse cost for stable initial height. The run timer only updates the header. Tests cover concurrent chunks, retention, first formatted display and pending edits.

Live records remain buffered while suspending catch-up parsing drains earlier records. If the queue overflows, HTTP recovery fills the gap before later live records are replayed. Regression tests inject arrivals during a paused replay and during overflow recovery to verify ordering.

Markdown opts into native TextView reuse in lazy lists. Reset/release clears old Markwon spans and image subscriptions; binding restores the new row’s selectable state and formatted content. Transcript and raw-log layouts use separate content types. Run output pauses periodic publication during a swipe/fling, and auto-follow stops when scrolling starts. Monotonic transcript/raw row positions preserve identities when the front of the 5,000-row window is trimmed. Tests verify native-view reuse with correct content/selection and stable retained-row identities after trimming.
