package com.lw.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)  //定义注解在属性上生效
@Retention(RetentionPolicy.RUNTIME)//到运行时生效
public @interface Girl {
    String name() default "小姐姐";

    String cup();//品德
}
