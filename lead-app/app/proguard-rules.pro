# Add project specific ProGuard rules here.
# For more details, see https://developer.android.com/build/shrink-code

# EMDK is provided by the device, so its classes are absent at build time.
-dontwarn com.symbol.emdk.**

# EMDK calls back into these interfaces reflectively; renaming their methods
# causes AbstractMethodError at runtime.
-keep class com.symbol.** { *; }
-keep public interface com.symbol.emdk.EMDKManager$EMDKListener { public *; }
-keep public interface com.symbol.emdk.EMDKManager$StatusListener { public *; }
-keep public interface com.symbol.emdk.ProfileManager$DataListener { public *; }
-keep class * implements com.symbol.emdk.EMDKManager$EMDKListener
-keep class * implements com.symbol.emdk.EMDKManager$StatusListener
-keep class * implements com.symbol.emdk.ProfileManager$DataListener
-keepattributes Signature
