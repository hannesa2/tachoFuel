# GitHub Copilot Instructions — TachoFuel

## Project overview

**TachoFuel** is an Android app for tracking odometer readings and fuel expenses for a Vespa scooter (Veglia Borletti analogue odometer). It captures values via ML Kit OCR from the camera, stores them locally in Room, syncs to Firebase Firestore/Storage, and collects detection images for future ML training.

**Real package name**: `info.hannes.vespatacho`  
(Source files use `com.example.vespatacho` — do not change this.)

---

## Tech stack

| Layer | Library / version |
|---|---|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose (BOM 2024.10.00), Material 3 |
| Architecture | AndroidViewModel + StateFlow, no Navigation component |
| Local DB | Room 2.7.1 (KSP 2.1.0-1.0.29) |
| OCR | ML Kit Text Recognition v2 |
| Cloud sync | Firebase Firestore (anonymous auth) |
| Image storage | Firebase Storage |
| Chart | AndroidChart `chartLib:5.2.4` (via `mavenLocal()`) |
| Logging | Timber |
| Compile SDK | 37, Min SDK 26 |

---

## Build quirks — read before changing build files

- **AGP 9.x**: requires `android.builtInKotlin=false` + `android.newDsl=false` in `gradle.properties`
- **Plugin order** in `app/build.gradle.kts`: `org.jetbrains.kotlin.android` **first**, then `com.android.application`
- **KSP version** must exactly match Kotlin prefix: Kotlin 2.1.0 → KSP `2.1.0-1.0.29`
- **kotlin-stdlib** forced to `2.1.0` via `resolutionStrategy.force(...)` (AndroidChart ships 2.4.10)
- **`-Xskip-metadata-version-check`** added to `kotlinOptions.freeCompilerArgs` for the same reason
- **Room 2.7.1** required for Kotlin 2.x (fixes void suspend function signature bug)

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
- `sed` is unreliable for in-place edits — always use the `edit` tool for file changes
- Do not call `detectPrice()` on `FuelDetector` — use `detect()` which returns all three values
