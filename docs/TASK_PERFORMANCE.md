# Keep task views populated during background graph updates



Run `./gradlew :app:testDebugUnitTest` with a complete JDK 17+ and Android SDK. Robolectric tests verify storage and Compose behavior, not physical-device frame rates.

Task rollups are computed bottom-up in linear time. The first graph, filtered model, tree, Next up and In flight seed from actual mirror data. Updates keep the previous model while background computation runs. Restored tree entry does not scroll to zero unless focus actually changes. Tests pause background work to verify first-frame content and a saved scroll anchor.

Company changes seed a separate tree immediately and reset focus, so a pending background update cannot retain another company's computed tree. A regression test switches companies with the worker dispatcher paused.

Task list group headers, ordinary task rows, tree rows and focused-tree summary cards declare separate lazy-list content types. This preserves existing keys and scroll anchors while allowing reuse only between matching layouts.
