# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Moshi reflection / Kotlin reflection
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
