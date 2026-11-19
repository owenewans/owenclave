package androidx.room;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Desktop stub for the Room annotation with the same fully qualified name.
 * The shared `KryoConverters` / `GsonConverters` are annotated with it; on
 * desktop we never run Room, we only need the annotation to be present.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface TypeConverter {
}
