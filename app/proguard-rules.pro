# The page calls the bridge by NAME (window.SPNative.*): R8 must not rename or strip it.
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keep class com.sixthpath.game.** { *; }
-keepattributes *Annotation*,SourceFile,LineNumberTable
