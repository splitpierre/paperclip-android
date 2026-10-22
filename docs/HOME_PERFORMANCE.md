# Preserve Home scroll anchors and cache chart drawing

- The Home feed uses namespaced event keys to retain the visible item across refreshes and layout-specific content types for lazy composition reuse. Timestamp ticks only update activity timestamps. The chart caches bar paths by data, size, density and theme instead of allocating paths and segment lists while drawing.

Run `./gradlew :app:testDebugUnitTest` with a complete JDK 17+ and Android SDK. Robolectric tests verify storage and Compose behavior, not physical-device frame rates.

The production Home feed test uses 200 synthetic activities, scrolls in both directions and verifies the same event stays at the same pixel offset when new activity, live runs and the paused banner change. It also verifies chart selection/data/empty states.

Mixed activity/search, cost, decision, inbox and project lists also declare layout-specific row content types, allowing Compose to reuse matching compositions without recycling unrelated headers/cards into rows. Avatar initials are remembered by name rather than rebuilt on each row update.
