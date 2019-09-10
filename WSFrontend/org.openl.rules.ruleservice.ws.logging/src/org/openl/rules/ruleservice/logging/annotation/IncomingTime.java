package org.openl.rules.ruleservice.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Date;

import org.openl.rules.ruleservice.logging.Convertor;

@Retention(RetentionPolicy.RUNTIME)
@Target(value = { ElementType.FIELD, ElementType.METHOD })
public @interface IncomingTime {
    Class<? extends Convertor<?, Date>> convertor() default DefaultDateConvertor.class;
}
