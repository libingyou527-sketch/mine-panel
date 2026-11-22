# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }

# SQLCipher
-keep,allowobfuscation,allowshrinking class net.zetetic.database.sqlcipher.** { *; }

# JNI bridge: native methods bind by fully-qualified name to libminepanel-llama.so.
# Class name, constructor and native method names must survive R8.
-keep class app.minepanel.model.GgufInferenceEngine {
    <init>();
    native <methods>;
}

# Model draft JSON is hand-built/parsed (no reflection); kept for clarity.
-keep class app.minepanel.model.ModelOutputJson { *; }
