# Proguard rules for ScanRelay

# ML Kit Barcode Scanning
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ZXing Core
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences { *; }
