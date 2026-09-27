package pe.axiz.reflectionpoc.domain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PaymentPlugin {
    String code();
    String description();
    int apiVersion() default 1;
    String[] instruments();
    String[] currencies();
    int priority() default 100;
}
