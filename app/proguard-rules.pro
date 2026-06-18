-dontwarn de.robv.android.xposed.**
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep public class org.hdhmc.bilihdpager.legacy.LegacyEntry {
    public <init>();
}

-keep public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
