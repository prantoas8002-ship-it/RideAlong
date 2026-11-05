# Firestore maps documents onto these POJOs via reflection.
-keep class com.example.ridealong.data.model.** { *; }
-keepclassmembers class com.example.ridealong.data.model.** { public <init>(); }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# osmdroid
-dontwarn org.osmdroid.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
