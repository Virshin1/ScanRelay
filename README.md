# ScanRelay ⚡

**ScanRelay** is a personal-use, open-source Android app designed for multi-device workflows where you have:
- **Phone A (Scanner)**: A secondary Android phone with a working camera (often on Phone B's hotspot) but no registered SIM or banking apps.
- **Phone B (Receiver)**: Your primary phone containing your real SIM card and official UPI banking apps (Google Pay, PhonePe, Paytm, BHIM, etc.), but with a non-functioning camera.

ScanRelay bridges the gap: **Phone A scans the merchant's UPI QR code offline, validates it for security, and securely pushes the raw payment link to Phone B via an [ntfy](https://ntfy.sh) relay.** Tapping the high-priority heads-up notification (or having "Auto-open" enabled) instantly launches your UPI app on Phone B with payment details pre-filled.

---

## 🛡️ Strict Privacy & Security Guarantee

> **ScanRelay NEVER handles, asks for, accesses, or stores bank accounts, card details, credentials, or UPI PINs.**
> It strictly acts as a text relay for standard `upi://pay` URIs. All financial authentication and payment execution happens exclusively inside your official banking/UPI apps.

### Key Security Guardrails
1. **Strict Scheme & Authority Allowlist**: Accepts ONLY URIs whose scheme is `upi` and host/authority is strictly `pay`. Rejects malicious exploits such as `upi://pay.evil.com`, `upi://pay@attacker.com`, `intent://`, `http(s)://`, and `file://`.
2. **Offline Barcode Scanning**: Uses bundled ML Kit barcode models (`com.google.mlkit:barcode-scanning:17.3.0`) embedded directly inside the APK. No network requests are made during scanning, and Google Play Services is not required.
3. **No Auto-Send on Scanner**: Phone A always presents a clear confirmation screen displaying Payee Name, UPI ID (VPA), Amount, and heuristic risk warnings before sending.
4. **Zero Trust on Receiver**: Phone B re-verifies that every incoming payload from the relay strictly matches the UPI specification and discards any stale messages older than 2 minutes.
5. **Hardware Keystore Encryption**: Optional server tokens are encrypted via AES-256-GCM backed by `AndroidKeyStore`.
6. **No Trackers or Ads**: Zero third-party telemetry, analytics, or background trackers. Plaintext HTTP traffic is rejected via `network_security_config.xml`. Android cloud backup is disabled (`android:allowBackup="false"`).

---

## 🏗️ Architecture & Tech Stack

- **Language & Runtime**: Kotlin 1.9+, minSdk 26 (Android 8.0), targetSdk 35 (Android 15)
- **UI Toolkit**: Jetpack Compose with Material 3 (Dynamic Color, Dark & Light theme support)
- **Architecture**: Single-Activity, MVVM, Unidirectional Data Flow (StateFlow / Coroutines)
- **Camera & Vision**: CameraX + ML Kit Barcode Scanning (Bundled offline model)
- **Networking**: OkHttp 4 (streaming SSE/JSON subscriber, line-by-line parsing, exponential backoff)
- **Local Storage**: Jetpack Preferences DataStore + Android KeyStore AES-GCM
- **Relay Mechanism**: [ntfy.sh](https://ntfy.sh) (or any self-hosted ntfy instance)

---

## 📱 Pairing Walkthrough

### Step 1: Install ScanRelay on Both Phones
Build the APK (see instructions below) and sideload it onto both devices.

### Step 2: Configure Roles
- On Phone A: Choose **"Scanner (this phone has the camera)"**.
- On Phone B: Choose **"Receiver (this phone has my SIM)"**.

### Step 3: Pair the Devices
1. On **Phone B (Receiver)**, tap **"Pair Phone"**.
   - A cryptographically random, 24+ character URL-safe topic is generated automatically.
   - The screen displays a Pairing QR code containing `{ "server": "https://ntfy.sh", "topic": "..." }`.
2. On **Phone A (Scanner)**, tap the QR icon at the top right (**"Pair with Receiver"**).
3. Aim Phone A's camera at Phone B's screen.
4. The scanner reads the JSON configuration and saves it instantly. Both phones are now paired!
   *(Note: Manual entry is also available if you prefer typing the topic).*

---

## 🚀 How to Use

1. **Activate Receiver**:
   - On Phone B, turn on the **"Background Listening"** switch.
   - A persistent low-priority notification will confirm: *"Listening for scanned payments..."*.
2. **Scan at the Merchant**:
   - On Phone A, open ScanRelay and point the camera at any UPI QR code.
   - Touch anywhere to focus; toggle the flash with the torch icon in low light.
   - Non-UPI QR codes are flagged with "Not a UPI QR" and ignored.
3. **Confirm & Send**:
   - Review the payee name, UPI ID, and amount.
   - Tap **"Send to Main Phone"**.
4. **Pay on Phone B**:
   - A high-priority heads-up notification appears on Phone B showing the payee and amount.
   - Tap the notification: Android opens your default UPI app (or the UPI app chooser) with the amount and payee pre-filled.
   - Enter your UPI PIN inside your official banking app to complete payment.

---

## ⚙️ Advanced Features

### Auto-Open on Arrival
Want to bypass tapping the notification?
1. On Phone B, open **Settings → Receiver Automation → Auto-Open on Arrival**.
2. Tap **"Permission & Battery Guide"**.
3. Grant the **"Display over other apps"** permission.
4. When Phone A scans a QR, Phone B immediately launches your UPI payment screen!

### In-App Self-Updater 🚀
ScanRelay includes built-in update checking directly from GitHub Releases:
1. Open **Settings → App Updates**.
2. Tap **"Check"** to query for new versions.
3. If a new release is available, tap **"Download & Install"** to automatically download the APK with a progress bar and trigger Android's native in-place installer. No manual redownloading or cable connection required.

### Local Scan History
- Phone B maintains a local record of the last 20 scans (timestamp, payee name, UPI ID, amount, status).
- You can review recent transactions or clear history at any time. History is stored locally on the device and never uploaded.

---

## 🔧 Troubleshooting Guide

### 1. Hotspot & Network Connectivity
- **Scenario**: Phone A is connected to Phone B's portable hotspot.
- **Verification**: Ensure mobile data is enabled on Phone B and Phone A can browse the internet.
- **Relay Status**: Phone A will display *"Relay Ready"* in green. If sending fails due to a temporary signal drop, the payment link is queued on Phone A with a **"Retry"** button (stale links older than 2 minutes are automatically expired for safety).

### 2. Notifications Not Showing (Android 13+)
- Android 13+ requires explicit notification permission. When prompted on first launch of Receiver mode, tap **"Allow"**. If you missed it, open **Settings → Apps → ScanRelay → Notifications** and enable them.

### 3. Background Service Killed by OEM (MIUI, ColorOS, OxygenOS, Samsung)
Some aggressive battery managers kill background foreground services:
- **Battery Optimization**: Open **Settings → Permission & Battery Guide** in ScanRelay and tap **"Exclude from Battery Saver"**.
- **Xiaomi / MIUI**: Go to **App Info → Permissions** and enable **"Display pop-up windows while running in background"** and set Battery saver to **"No restrictions"**.
- **OnePlus / ColorOS**: Allow **"Auto-start"** and **"Run in background"**.

---

## 🛠️ Build and Sideload Instructions

### Prerequisites
- JDK 17
- Android SDK with Platforms 34 / 35 and Build-Tools installed

### Build Debug APK
```bash
# Clone or navigate to the project directory
cd /Users/virshin/Projects/scanner

# Run automated tests
./gradlew testDebugUnitTest

# Assemble the debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install via ADB
```bash
# Install on Phone A
adb -s <phone_a_serial> install -r app/build/outputs/apk/debug/app-debug.apk

# Install on Phone B
adb -s <phone_b_serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
Personal-use open-source software. Distributed as-is under the MIT License.
