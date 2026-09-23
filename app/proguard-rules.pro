# Keep Ktor client classes
-keep class io.ktor.** { *; }
-keep class io.ktor.client.** { *; }

# Keep kotlinx serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

# Keep model classes
-keep class com.dialect.voice.domain.** { *; }
-keepclassmembers class com.dialect.voice.domain.** { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }

# Keep API client classes
-keep class com.dialect.voice.api.** { *; }

# Keep ViewModel
-keep class androidx.lifecycle.** { *; }

# Ktor/SLF4J reference a few JVM-only classes (IntelliJ debugger detection, an optional SLF4J
# binder) that don't exist on Android and are never actually reached at runtime there - R8
# just needs to be told not to warn about them.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn org.slf4j.impl.StaticLoggerBinder
