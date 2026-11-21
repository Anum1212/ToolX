# Project-specific R8 rules. Media3, Compose and Coil ship their own consumer rules.

# Optional decoder and Java desktop classes referenced by pdfbox and jaudiotagger
-dontwarn com.gemalto.jp2.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

# Keep rules for libraries that rely on reflection or runtime inspection
-keep class org.jaudiotagger.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }

