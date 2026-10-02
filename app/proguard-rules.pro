# ===== Room 相关 Keep 规则（防反射混淆崩溃） =====
# Room 实体类（data 包下所有 Entity）
-keep class com.rongyu.shixuji.data.** { *; }

# Room DAO 接口方法与生成的实现
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase

# Room 编译器生成的 *_Impl 类
-keep class com.rongyu.shixuji.data.**.*Impl { *; }
-keepnames class com.rongyu.shixuji.data.** { *; }

# Kotlin 协程（Room suspend 依赖）
-dontwarn kotlinx.coroutines.**

# 若开启 JSON 序列化（当前未用），取消下行注释
# -keepattributes Signature, InnerClasses, EnclosingMethod

# 通用：保留注解（自适应图标等无需，但安全）
-keepattributes *Annotation*