# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ─── Strip verbose Android Log calls from release builds ─────────────────────
# This prevents debug/verbose log tags and messages from appearing in the APK
# and leaking internal class/file names to reverse engineers.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
