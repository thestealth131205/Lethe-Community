# Tor (Guardian Project tor-android + jtorctl)
# Die TorService-Bibliothek lädt net.freehaven.tor.control-Klassen teils via Reflection.
# Ohne diese Keep-Regel streicht R8 sie und verursacht NoClassDefFoundError zur Laufzeit.
-keep class net.freehaven.tor.** { *; }
-keep interface net.freehaven.tor.** { *; }
-keep class org.torproject.** { *; }
-keep interface org.torproject.** { *; }

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile