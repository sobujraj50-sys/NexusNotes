# Nexus Notes — R8 rules (release builds).
#
# Policy: shrink and optimise library code, but keep the app's own classes and member names.
# The app is small; keeping it makes crash reports (Settings › crash dialog) readable without a
# mapping file and removes any risk from reflection in Room-generated code or enum lookups.
-keep class com.nexusnotes.** { *; }

# Readable stack traces.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Compile-time-only annotations referenced by some libraries.
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn kotlinx.coroutines.debug.**
