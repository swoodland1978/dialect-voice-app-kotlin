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
