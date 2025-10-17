# StoneParks BT Print (Android) — v1.1
- Direct Bluetooth SPP printing to Zebra ZQ511 (offline).
- Optional webhook: queued via WorkManager and posted when online.

## Build
Open in Android Studio, let Gradle sync, pair ZQ511, run.

## Webhook
Enter URL in the field and tap **Save Webhook**. After printing, a job will POST the ticket JSON to your URL when network is available (auto-retry).
