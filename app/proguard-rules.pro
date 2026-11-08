## Version: 1
## ProGuard - 南阳虚拟Win

# Gson
-keepattributes Signature,InnerClasses,Annotation
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Data classes (VM profiles)
-keep class com.nanyang.virtualwin.model.** { *; }

# JNI native methods
-keepclasseswithmembers class * {
    native <methods>;
}

# ViewModel
-keep class androidx.lifecycle.* { *; }
-keep class androidx.navigation.* { *; }

# Compose
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
