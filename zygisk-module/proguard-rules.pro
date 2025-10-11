# Adding optimization introduces certain risks, since for example not
# all optimizations performed by ProGuard works on all versions of Dalvik.
# The following flags turn off various optimizations known to have issues,
# but the list may not be complete or up to date.
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

-dontusemixedcaseclassnames
-verbose

-keepattributes *Annotation*

# For enumeration classes, see http://proguard.sourceforge.net/manual/examples.html#enumerations
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

-dontobfuscate