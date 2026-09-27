# Xover release obfuscation.
#
# External libraries stay as normal library jars. ProGuard processes only Xover
# project jars, writes obfuscated jars with the same file names, and emits
# build/obfuscated-release/mapping.txt for crash decoding.

-keep public class com.xover.music.app.XoverMain {
    public static void main(java.lang.String[]);
}

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keepdirectories

-optimizationpasses 5
-allowaccessmodification
-overloadaggressively
-repackageclasses x
-adaptclassstrings

-dontnote
