# FaceLog — Latest Changes

## Photo Saving Robustness (Bug Fix)

**Problem:** Attendance photos sometimes didn't appear in the gallery. `Bitmap.copy()` can return null (especially for HARDWARE bitmaps), causing silent failures. No file validation after write.

**Fixes:**

- **`Bitmap.safeCopy()` extension** (`util/Extensions.kt`) — Falls back to `ARGB_8888` if bitmap config is null, wraps in try/catch, returns null on failure instead of crashing.

- **Early bitmap copy in `AttendanceViewModel.onFrameAvailable()`** — Bitmap is now copied *before* launching the coroutine to avoid race conditions with the camera buffer. The working copy is recycled in a `finally` block.

- **`saveAttendancePhoto()` hardened** — Uses `safeCopy(mutable = true)` with null check. Validates that the JPEG file exists and has non-zero size after write. Deletes empty/corrupt files.

- **`GroupScanViewModel.onPreviewFrame()`** — Replaced raw `bitmap.copy()` with `safeCopy()`.

## Group Scan Photo Capture (New Feature)

**Problem:** Photos were saved during individual check-in/check-out but *not* during group scan. `confirmTrack()` was passing `photoPath = null`.

**Fix:**

- Frame bitmap is now threaded through `processFrame()` → `updateTrackWithResult()` → `confirmTrack()`.
- `confirmTrack()` saves a date-stamped photo synchronously (while the bitmap is still alive) before launching the attendance recording coroutine.
- Photos are cleaned up (deleted) if attendance recording fails (already checked in, not checked in, error).
- Same timestamp-stamping logic as individual scan (shadow + white text, bottom-right corner).

## Reverse Geocoding (New Feature)

**Problem:** Location showed only raw coordinates (e.g. `12.9352, 77.6245`). User wanted human-readable names.

**Fix:**

- **`LocationProvider` rewritten** — Returns `LocationInfo(latitude, longitude, locationName)` instead of `Pair<Double, Double>`.
- **Reverse geocoding** via Android's built-in `Geocoder` — no new dependencies. Uses async API on Android 33+, sync on older versions.
- **Format:** `"subLocality, locality, adminArea"` (e.g. "Koramangala, Bengaluru, Karnataka").
- **Graceful fallback** — If geocoding fails (no network, unavailable), `locationName` is null and UI falls back to raw coordinates.

- **DB schema updated** — Added `location_name` column to `AttendanceEntity`, bumped DB version 4 → 5 (destructive migration).
- **`RecordAttendanceUseCase`** — Added `locationName` parameter, passed through to entity.
- **Both ViewModels** updated to use `LocationInfo` fields.
- **History screen** — Shows location name with fallback to `"lat, lng"` format.
- **Photo Gallery screen** — Same pattern, with text overflow ellipsis for long names.

## Files Modified

| File | Changes |
|------|---------|
| `util/Extensions.kt` | Added `Bitmap.safeCopy()` extension |
| `data/location/LocationProvider.kt` | Rewritten: `LocationInfo` data class, reverse geocoding |
| `data/local/entity/AttendanceEntity.kt` | Added `location_name` column |
| `data/local/FaceLogDatabase.kt` | Version 4 → 5 |
| `domain/usecase/RecordAttendanceUseCase.kt` | Added `locationName` param |
| `ui/screen/attendance/AttendanceViewModel.kt` | Early bitmap copy, `safeCopy`, `LocationInfo`, file validation |
| `ui/screen/groupscan/GroupScanViewModel.kt` | `safeCopy`, `LocationInfo`, photo saving on confirmation |
| `ui/screen/history/HistoryScreen.kt` | `locationName` field, display with fallback |
| `ui/screen/history/HistoryViewModel.kt` | Map `locationName` from record |
| `ui/screen/gallery/PhotoGalleryViewModel.kt` | `locationName` field in `PhotoItem` |
| `ui/screen/gallery/PhotoGalleryScreen.kt` | Display location name with fallback + ellipsis |
