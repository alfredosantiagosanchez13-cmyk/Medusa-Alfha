# Keep rules for Medusa Alfha - Cloud Firestore & Models
-keep class com.example.medusaalfha.data.model.** { *; }
-keepclassmembers class com.example.medusaalfha.data.model.** { *; }

# Keep Room entities and DAOs
-keep class com.example.medusaalfha.data.local.entity.** { *; }
-keepclassmembers class com.example.medusaalfha.data.local.entity.** { *; }
-keep class com.example.medusaalfha.data.local.dao.** { *; }
-keepclassmembers class com.example.medusaalfha.data.local.dao.** { *; }

# Firebase Firestore field and method preservation
-keepattributes *Annotation*
-keepattributes Signature
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
}
-keep class com.google.firebase.firestore.** { *; }
