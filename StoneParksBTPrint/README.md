# StoneParks BT Print (Android)
Print CPCL tickets to a Zebra ZQ511 over classic Bluetooth (SPP). Works offline.

## Build
1. Open this folder in **Android Studio** → *Open an Existing Project*.
2. Let it sync Gradle (Kotlin 1.9.24, AGP 8.5.2, compileSdk 34).
3. Pair ZQ511 in Android Bluetooth settings.
4. Run on device.

## Use
- Tap **Select ZQ511** (picks a bonded device; prefers names containing "ZQ511").
- Enter ticket fields, tap **Print Ticket**.

## Customize
- Edit `buildCpcl()` in `MainActivity.kt` to change label layout or switch to ZPL.
