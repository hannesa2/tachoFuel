# GitHub Copilot Instructions — TachoFuel

## Project overview

**Vespa Tacho** is an Android app for tracking odometer readings and fuel expenses for a Vespa scooter (Veglia Borletti analogue odometer). It captures values via ML Kit OCR from the camera, stores them locally in Room, syncs to Firebase Firestore/Storage, and collects detection images for future ML training.

**Real package name**: `info.hannes.vespatacho`  
(Source files use `com.example.vespatacho` — do not change this.)

---

## Tech stack

| Layer | Library / version |
|---|---|
| Language | Kotlin 2.3.20 |
| KSP | 2.3.11 |
| UI | Jetpack Compose (BOM 2024.10.00), Material 3 |
| Architecture | AndroidViewModel + StateFlow, no Navigation component |
| Local DB | Room 2.7.1 |
| OCR | ML Kit Text Recognition v2 |
| Cloud sync | Firebase Firestore (anonymous auth) |
| Image storage | Firebase Storage |
| Chart | AndroidChart `chartLib:5.2.4` (via `mavenLocal()`) |
| Logging | Timber |
| Compile SDK | 37, Min SDK 26 |

---

## Build quirks — read before changing build files

- **AGP 9.0+** has built-in Kotlin support — do **not** add `org.jetbrains.kotlin.android` to the app module; it's only declared in root `build.gradle.kts` to set the version for the Compose plugin
- **Plugin order** in `app/build.gradle.kts`: `com.android.application` first, then compose + KSP plugins
- **KSP version must match Kotlin**: Kotlin 2.3.20 → KSP `2.3.11`. Check latest at https://github.com/google/ksp/releases before bumping Kotlin — if no matching KSP exists, do not upgrade Kotlin.
- **kotlin-stdlib** forced to match Kotlin version via `resolutionStrategy.force(...)` (AndroidChart ships a different stdlib version)
- **`-Xskip-metadata-version-check`** added to `kotlinOptions.freeCompilerArgs` for AndroidChart compatibility
- **Room 2.7.1** required for Kotlin 2.x (fixes void suspend function signature bug)
- **Dependabot** may bump Kotlin to versions without a KSP release — always verify KSP compatibility after a Dependabot Kotlin upgrade

---

## Architecture

```
Activity
  └── setContent { Screen() }           // Compose UI
        └── ViewModel (AndroidViewModel) // StateFlow state
              └── GasReadingRepository   // single data access point
                    ├── GasReadingDao    // Room
                    └── FirestoreRepository  // Firestore (fire-and-forget)
```

- **ViewModels** must never call DAOs directly — always go through `GasReadingRepository`
- **VespaTachoApp** is the app class; exposes `repository` and `detectionSampleRepository` lazily
- All ViewModels access repos via `(app as VespaTachoApp).repository`
- `SavedStateHandle` provides `vehicleId` and `readingId` from Activity intent extras automatically

---

## Database

Current version: **7**  
Migrations: 1→2, 2→3, 3→4, 4→5, 5→6, 6→7 (all in `AppDatabase.kt`)

### Entities

**`gas_readings`**
```
id, vehicleId, km (nullable), price (nullable), liter (nullable),
rawOcrTextKm (nullable), rawOcrTextFuel (nullable), timestamp
```

**`vehicles`**
```
id, name
```

**`detection_samples`** (ML training data)
```
id, type ("ODOMETER"|"FUEL"), imageJpeg (BLOB), rawOcrText,
detectedKm, detectedPrice, detectedLiter, storageUrl, vehicleId, timestamp
```

When adding columns, **always write a new Migration** — never bump version without one.  
SQLite on Android < 3.25 does not support `RENAME COLUMN` — recreate the table instead.

---

## Firebase structure

```
Firestore:
  users/{uid}/gasReadings/{vehicleId}_{localId}
  users/{uid}/vehicles/{vehicleId}
  users/{uid}/detectionSamples/{localId}

Storage:
  detectionSamples/{uid}/ODOMETER/{timestamp}_{id}.jpg
  detectionSamples/{uid}/FUEL/{timestamp}_{id}.jpg
```

- Auth: anonymous sign-in (stable per device/install)
- Writes: fire-and-forget background coroutine; Room is source of truth
- `syncFromCloud()` runs on startup to pull missing records
- Firebase Storage bucket must be enabled in Firebase Console before images can upload

---

## OCR detectors

### OdometerDetector
- Looks for the string `VEGLIA` as an anchor, then extracts the km number from nearby lines
- Returns `OdometerResult(km: Int, rawOcrTextKm: String)`

### FuelDetector
- German pump format: number printed **above** the label (e.g. number line, then `Liter` below)
- Multi-pass detection: label-on-next-line → label-on-same-line → magnitude/spatial fallback
- `normaliseLcd()` fixes 7-segment LCD misread: `(\d)c(\d)` → `${1}2,${2}` (e.g. `0002c46` → `0002,46`)
- Returns `FuelResult(price: String, liter: String, rawOcrTextFuel: String)`
- Do not call `detectPrice()` — always use `detect()` which returns all three values
- Logs raw OCR text with `Timber.d("rawOcrTextFuel:\n$rawText")`

---

## DetectionSampleRepository

Saves compressed images for ML training:
- Max 800px longest side, JPEG 70% quality (~50–100 KB)
- Inserts into Room immediately; uploads to Firebase Storage in background
- `storageUrl` is null until upload succeeds → used as retry flag (`getPendingUpload()`)
- `retryPendingUploads()` called on app start from `VespaTachoApp.onCreate()`

---

## Key files

| File | Purpose |
|---|---|
| `VespaTachoApp.kt` | App class; lazy `repository` + `detectionSampleRepository`; startup sync |
| `data/AppDatabase.kt` | Room DB, all migrations |
| `data/GasReading.kt` | Core entity |
| `data/DetectionSample.kt` | ML training entity (image + OCR metadata) |
| `data/GasReadingRepository.kt` | Write-through repo (Room + Firestore) |
| `data/DetectionSampleRepository.kt` | Image compress + Room + Storage + Firestore |
| `data/FirestoreRepository.kt` | All Firestore CRUD |
| `camera/OdometerDetector.kt` | VEGLIA-anchored km OCR |
| `camera/FuelDetector.kt` | Multi-pass fuel price/liter OCR |
| `ui/HomeScreen.kt` | Main list; single-tap → edit, long-press → options dialog |
| `ui/EditKmReadingScreen.kt` | Edit form; shows scan images + OCR text below Save/Cancel |
| `ui/FuelConsumptionChart.kt` | AndroidChart line chart; X = absolute km, Y = l/100km |
| `app/google-services.json` | **Replace with real Firebase file** (current is placeholder) |

---

## Conventions

- German strings in UI (this is a German-language app)
- Nullable fields in `GasReading` because odometer and fuel are captured in separate sessions
- `sed` is unreliable for in-place edits in this project — always use the `edit` tool for file changes
- Do not call `detectPrice()` on `FuelDetector` — use `detect()` which returns all three values
