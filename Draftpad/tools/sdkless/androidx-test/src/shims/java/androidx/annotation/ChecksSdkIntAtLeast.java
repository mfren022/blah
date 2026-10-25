package androidx.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compile-time stand-in for androidx.annotation.ChecksSdkIntAtLeast. */
@Retention(RetentionPolicy.CLASS)
@Target({METHOD, FIELD})
public @interface ChecksSdkIntAtLeast {
    int api() default -1;

    String codename() default "";

    int parameter() default -1;

    int lambda() default -1;
}
