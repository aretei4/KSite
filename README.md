# Khaga Site — Android App

**Package:** `com.khaga.mobile.ksite`  
**App Name:** Khaga Site  
**Architecture:** MVVM  
**Database:** Room (SQLite)  
**Backup:** jsonbin.io REST API  
**Min SDK:** 26 (Android 8.0)  
**Target SDK:** 34 (Android 14)

---

## Project Structure

```
KhagaSite/
├── app/src/main/java/com/khaga/mobile/ksite/
│   ├── KhagaApp.kt                        # Application class
│   ├── data/
│   │   ├── model/Models.kt                # Room entities: Site, Worker, Payment, Collection, MonthClose
│   │   ├── db/
│   │   │   ├── KhagaDatabase.kt           # Room database singleton
│   │   │   └── Daos.kt                    # DAOs for all entities
│   │   └── repository/
│   │       └── KhagaRepository.kt         # Single source of truth
│   ├── network/
│   │   └── BackupApi.kt                   # Retrofit + jsonbin.io API service
│   ├── viewmodel/
│   │   └── MainViewModel.kt               # Shared AndroidViewModel
│   ├── util/
│   │   └── Util.kt                        # Fmt helpers, constants
│   └── ui/
│       ├── MainActivity.kt                # Bottom nav host
│       ├── give/GiveFragment.kt           # Record payments to workers
│       ├── collect/CollectFragment.kt     # Record collections from clients
│       ├── monthclose/MonthCloseFragment.kt # Month close with wage calculation
│       └── settings/SettingsFragment.kt  # Sites / Workers / Backup tabs
├── res/
│   ├── layout/                            # All XML layouts
│   ├── navigation/nav_graph.xml           # Navigation graph
│   ├── menu/bottom_nav_menu.xml           # Bottom nav menu
│   ├── values/colors.xml, strings.xml, themes.xml
│   └── drawable/                          # Vector icons
└── gradle/libs.versions.toml             # Version catalog
```

---

## MVVM Flow

```
UI (Fragment)
    │ observe LiveData
    ▼
ViewModel (MainViewModel)
    │ calls suspend funs via viewModelScope
    ▼
Repository (KhagaRepository)
    │                    │
    ▼                    ▼
Room DAO           Retrofit API
(SQLite)         (jsonbin.io backup)
```

---

## Features

| Screen | Features |
|--------|----------|
| **Give** | Record payments to workers — head (Wages/Advance/Travel/Maintenance/Bonus/Other), amount, mode (Cash/UPI/Bank Transfer/Cheque), date, note. Edit & delete. |
| **Collect** | Record collections from clients — site, client name, billed vs received, mode, date. Partial/paid/pending status. Edit & delete. |
| **Month Close** | Select worker + site + month, enter Present/Half/Absent days. Auto-calculates wage earned, deducts taken amounts (Advance/Travel/Other), shows net payable. |
| **Settings → Sites** | Add/edit/delete sites with rough estimate. Live gain/loss per site. |
| **Settings → Workers** | Add/edit/delete workers with photo (camera/gallery), wage, mobile, address. |
| **Settings → Backup** | Enter jsonbin.io API key. Save full backup to backend. Restore by Bin ID on any device. |

---

## Setup Instructions

### 1. Open in Android Studio
- Open Android Studio (Hedgehog or later recommended)
- Select **Open** and navigate to the `KhagaSite` folder
- Let Gradle sync complete

### 2. Get a jsonbin.io API Key (for Backup)
1. Go to [https://jsonbin.io](https://jsonbin.io) → Sign up free
2. Go to **API Keys** → copy your **Secret Key**
3. In the app, go to **Settings → Backup** and paste the key

### 3. Run the App
- Connect an Android device (API 26+) or start an emulator
- Click ▶ **Run** in Android Studio

---

## Database Schema

```sql
sites        (id, name, estimate, createdAt)
workers      (id, name, wagePerDay, mobile, address, photoPath, createdAt)
payments     (id, workerId, siteId, head, amount, mode, date, note, createdAt)
collections  (id, siteId, from, amount, received, mode, date, status, createdAt)
month_closes (id, workerId, siteId, month, daysPresent, daysHalf, daysAbsent,
              totalDays, wageEarned, advanceTaken, travelTaken, otherTaken,
              totalTaken, netPayable, closedAt)
```

---

## Backup Format (JSON)

```json
{
  "record": {
    "sites": [...],
    "workers": [...],
    "payments": [...],
    "collections": [...],
    "monthCloses": [...],
    "backupAt": "2025-05-01T10:30:00"
  }
}
```

---

## Notes
- Worker photos are stored locally on-device; backup does not include photos (too large for JSON)
- All monetary values stored as Long (paise or rupees — your choice, display divides as needed)
- The app uses `SharedPreferences` to persist the API key and Bin ID across sessions
- Replace `$2a$10$placeholder` in `BackupApi.kt` with the real key entered in Settings at runtime — the app reads it from `SharedPreferences` dynamically
