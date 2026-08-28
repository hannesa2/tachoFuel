# TachoFuel

Android app for tracking odometer readings and fuel expenses, with OCR-based data capture and Firebase cloud sync.

---

## Setup: Enable Firebase Storage

Before the app can upload images or you can download them, Firebase Storage must be
activated once in the Firebase Console:

1. Open <https://console.firebase.google.com/project/vespatacho/storage>
2. Click **Get started** → **Start in test mode** → pick a region (e.g. `europe-west1`) → **Done**
3. Verify the bucket exists:
   ```bash
   gcloud storage buckets list --project vespatacho
   ```
   You should now see your bucket (e.g. `vespatacho.firebasestorage.app`).

---

## Download ML training data from Firebase (macOS)

Detection sample images and OCR texts are stored in Firebase Storage and Firestore under the anonymous user's UID.

### Prerequisites

Install the Firebase CLI:

```bash
brew install firebase-cli
# or
npm install -g firebase-tools
```

Log in:

```bash
firebase login
```

No project directory needed — pass `--project vespatacho` directly to each command below.

---

### 1 — Download all images from Firebase Storage

> **Prerequisite**: Firebase Storage must be enabled first.
> Go to [Firebase Console](https://console.firebase.google.com/project/vespatacho/storage) →
> Storage → **Get started** → choose a region → Done.
> The bucket name shown there (e.g. `vespatacho.firebasestorage.app`) is what you use below.

Find your exact bucket name:

```bash
gcloud storage buckets list --project vespatacho
```

All captured images are stored at:

```
gs://{bucket}/detectionSamples/{uid}/{type}/{timestamp}_{id}.jpg
```

List available images:

```bash
gcloud storage ls --recursive "gs://vespatacho.firebasestorage.app/detectionSamples/"
```

Download everything into a local folder:

```bash
mkdir -p ~/tachofuel-samples/images
gcloud storage cp --recursive \
  "gs://vespatacho.firebasestorage.app/detectionSamples/**" \
  ~/tachofuel-samples/images/
```

---

### 2 — Export OCR metadata from Firestore

All detection metadata (OCR text, detected values, storage URL) is stored at:

```
users/{uid}/detectionSamples/{localId}
```

Export every user's samples to JSON using the Firebase CLI:

```bash
mkdir -p ~/tachofuel-samples/metadata

# List all user UIDs
firebase firestore:query "users" --project vespatacho --shallow 2>/dev/null \
  | grep '"id"' | awk -F'"' '{print $4}' > /tmp/uids.txt

# Export samples for each UID
while read uid; do
  firebase firestore:query "users/$uid/detectionSamples" --project vespatacho \
    > ~/tachofuel-samples/metadata/${uid}.json
  echo "Exported UID: $uid"
done < /tmp/uids.txt
```

Or export the entire Firestore database at once (requires Blaze plan):

```bash
gcloud firestore export gs://vespatacho.firebasestorage.app/firestore-export \
  --project vespatacho
```

Then download the export locally:

```bash
gsutil -m cp -r "gs://vespatacho.firebasestorage.app/firestore-export" ~/tachofuel-samples/
```

---

### 3 — Quick combined download script

Save as `download_samples.sh` and run with `bash download_samples.sh`:

```bash
#!/usr/bin/env bash
set -e

PROJECT="vespatacho"
BUCKET="gs://vespatacho.firebasestorage.app"
OUT=~/tachofuel-samples

mkdir -p "$OUT/images" "$OUT/metadata"

echo "==> Downloading images from Storage..."
gcloud storage cp --recursive "$BUCKET/detectionSamples/" "$OUT/images/"

echo "==> Exporting Firestore metadata..."
# Requires Blaze plan for managed export:
# gcloud firestore export "$BUCKET/firestore-export" --project "$PROJECT"
# gsutil -m cp -r "$BUCKET/firestore-export" "$OUT/"

# Alternative: use firebase-admin Python SDK for a per-collection export:
pip3 install firebase-admin --quiet
python3 - <<'PYEOF'
import firebase_admin
from firebase_admin import credentials, firestore
import json, os, pathlib

app = firebase_admin.initialize_app()   # uses GOOGLE_APPLICATION_CREDENTIALS or gcloud ADC
db = firestore.client()

out = pathlib.Path(os.path.expanduser("~/tachofuel-samples/metadata"))
out.mkdir(parents=True, exist_ok=True)

for user_doc in db.collection("users").stream():
    uid = user_doc.id
    samples = []
    for s in db.collection("users").document(uid).collection("detectionSamples").stream():
        samples.append({"id": s.id, **s.to_dict()})
    dest = out / f"{uid}.json"
    dest.write_text(json.dumps(samples, indent=2, default=str))
    print(f"  {uid}: {len(samples)} samples → {dest}")
PYEOF

echo "==> Done. Files in $OUT"
```

> **Authentication**: the script uses [Application Default Credentials](https://cloud.google.com/docs/authentication/application-default-credentials).
> If `gcloud` fails with a `collections.Mapping` error, reinstall it:
> ```bash
> brew uninstall --cask google-cloud-sdk && brew install --cask google-cloud-sdk
> source "$(brew --prefix)/share/google-cloud-sdk/path.bash.inc"
> ```
> Then run `gcloud auth application-default login` once before executing the script.

---

### File layout after download

```
~/tachofuel-samples/
├── images/
│   └── detectionSamples/
│       └── {uid}/
│           ├── ODOMETER/
│           │   └── {timestamp}_{id}.jpg
│           └── FUEL/
│               └── {timestamp}_{id}.jpg
└── metadata/
    └── {uid}.json          ← array of detection sample objects
```

Each metadata entry contains:

| Field | Description |
|---|---|
| `id` | Local Room ID |
| `type` | `ODOMETER` or `FUEL` |
| `rawOcrText` | Full ML Kit OCR output |
| `detectedKm` | Parsed odometer value (km) |
| `detectedPrice` | Parsed fuel price (€) |
| `detectedLiter` | Parsed fuel volume (l) |
| `storageUrl` | Firebase Storage download URL |
| `vehicleId` | Vehicle ID |
| `timestamp` | Unix timestamp (ms) |
