package org.example.traveljava.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    
    int max() default 100;
    
    int duration() default 60;
    
    String key() default "";
}
