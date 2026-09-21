# DeepL Data Transfer Objects for Gson
-keep class com.antigravity.translator.data.api.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ML Kit Text Recognition
-keep class com.google.mlkit.vision.text.** { *; }
