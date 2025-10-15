package omm.mtk.easy.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author mahatoky rasolonirina
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface ConfigurationProperties {
    String prefix() default "";
    String value() default ""; // alias pour prefix
}
