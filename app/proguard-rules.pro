# Keep the BLE engine entry points reachable from the foreground service.
-keep class com.komugi.attacker.ble.** { *; }
