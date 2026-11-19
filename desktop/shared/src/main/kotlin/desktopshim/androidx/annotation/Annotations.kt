package androidx.annotation

@Retention(AnnotationRetention.BINARY)
annotation class RequiresApi(val value: Int = 1, val api: Int = 1)

@Retention(AnnotationRetention.BINARY)
annotation class MainThread

@Retention(AnnotationRetention.BINARY)
annotation class AttrRes

@Retention(AnnotationRetention.BINARY)
annotation class ColorRes

@Retention(AnnotationRetention.BINARY)
annotation class DrawableRes

@Retention(AnnotationRetention.BINARY)
annotation class RawRes

@Retention(AnnotationRetention.BINARY)
annotation class XmlRes
