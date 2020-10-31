package com.lw.aspect;

import com.lw.util.BeanUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：SetFieldValueAspect </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/3/2 12:02
 */
@Component
@Aspect
public class SetFieldValueAspect {
    @Autowired
    BeanUtil beanUtil;

    @Around("@annotation(com.lw.annotation.NeedSetValueField)")
    public Object doSetFieldValue(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Object ret = proceedingJoinPoint.proceed();//执行原方法去获取结果集

        //操作结果集，将值设置到结果集
        this.beanUtil.setFieldValueForCol((Collection) ret);
        return ret;

    }
}
