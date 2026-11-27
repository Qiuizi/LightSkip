# LightSkip Release ProGuard / R8 rules
# Keep all AndroidManifest-declared components and entry points intact.
-keep public class app.qi.qingtiao.MainActivity
-keep public class app.qi.qingtiao.PageActivity
-keep public class app.qi.qingtiao.DemoActivity
-keep public class app.qi.qingtiao.GuideActivity
-keep public class app.qi.qingtiao.SkipService
-keep public class app.qi.qingtiao.SkipTileService
-keep public class app.qi.qingtiao.UpdateFileProvider

# Keep org.json / reflection-safe data structures if any are serialized
-keepclassmembers class app.qi.qingtiao.UpdateInfo { *; }
-keepclassmembers class app.qi.qingtiao.RecordedNode { *; }
-keepclassmembers class app.qi.qingtiao.RuleBook$Entry { *; }

# Keep stack trace line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
