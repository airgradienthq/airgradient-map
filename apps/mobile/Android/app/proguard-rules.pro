# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep generic type signatures and runtime annotations so Gson can
# discover collection element types (avoids Class→ParameterizedType
# cast crashes in release builds).
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Gson-backed models (data classes under data.models). Keep everything so
# field names, annotations, and nested generics survive shrinking.
-keep class com.airgradient.android.data.models.** { *; }

# Retrofit DTOs + service interfaces also rely on reflection for query
# annotations and collection generics. Keep both the classes (DTOs declared
# next to services) and the service interfaces themselves.
-keep class com.airgradient.android.data.services.** { *; }
-keep interface com.airgradient.android.data.services.** { *; }

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

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
