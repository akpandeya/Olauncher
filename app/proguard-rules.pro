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
# androidTest runner (AndroidJUnitRunner) loads these at start; R8 on the debug variant strips them.
# AndroidJUnitRunner calls androidx.tracing.Trace at onCreate.
-keep class androidx.tracing.** { *; }
# Test runner and its helpers are reached by reflection from the instrumentation.
-keep class androidx.test.** { *; }
-dontwarn androidx.test.**

# The androidTest runner pulls in much of its dependency graph at start-up; R8 strips it
# piecemeal (tracing, kotlin.LazyKt, ...), so keep the whole graph for the debug variant.
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class androidx.** { *; }
-keep class org.junit.** { *; }
-keep class org.hamcrest.** { *; }
