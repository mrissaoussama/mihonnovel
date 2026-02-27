# Fix Summary

## 1. LibraryScreenModel Fix (UI Not Updating)

**Problem:** `collectLatest` was cancelling grouping/sorting operations when rapid StateFlow emissions occurred (e.g. from MangaScreen where multiple source types are active). This caused the UI to not update even though data was available.

**Fix:** Changed `collectLatest` to `collect`. This ensures that every emission is processed fully and the UI is updated for each one, preventing race conditions or cancellations.

**Correction:** Also fixed a compilation error where `sumOf` was used incorrectly on a Map. Used `values.sumOf { it.size }` instead. And ensured `grouped` variable is correctly defined.

## 2. FictionZone Fix (Download Crash)

**Problem:** `getChapterUrl` was not overridden, defaulting to `pageListRequest` which threw `UnsupportedOperationException`.

**Fix:** Overridden `getChapterUrl` to return `baseUrl + chapter.url`.

## 3. Verification Test

Added `testChapterUrl` to `NovelExtensionTests.kt` and included `fictionzone` in the test targets. This allows running the extension test suite to verify that `getChapterUrl` no longer throws an exception for FictionZone.

To run the test:
```bash
./gradlew :app:test --tests "eu.kanade.tachiyomi.extension.NovelExtensionTests"
```

