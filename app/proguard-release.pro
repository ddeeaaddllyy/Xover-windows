# Xover release obfuscation.
#
# External libraries stay as normal library jars. ProGuard processes only Xover
# project jars, writes obfuscated jars with the same file names, and emits
# build/obfuscated-release/mapping.txt for crash decoding.

-keep public class com.xover.music.app.XoverMain {
    public static void main(java.lang.String[]);
}

-keep public class com.xover.music.app.ReleaseRuntimeSmoke {
    public static void verify();
}

# Enum names are used by the wire protocol and by Enum.valueOf at runtime.
# ProGuard must preserve both the enum flag and the original constant names.
-keep enum com.xover.music.** { *; }

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

-optimizationpasses 5
-allowaccessmodification
-overloadaggressively
-repackageclasses x
-adaptclassstrings

-dontnote
