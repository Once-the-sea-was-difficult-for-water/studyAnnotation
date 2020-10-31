package com.lw.util;

import com.lw.annotation.NeedSetValue;
import org.springframework.beans.BeansException;
import org.springframework.cglib.beans.BulkBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：BeanUtil </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/3/2 12:06
 */
@Component
public class BeanUtil implements ApplicationContextAware {

    private ApplicationContext applicationContext;
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    //1、拿注解 ---》method -->java bean --->spring -->applicationContext
    public void setFieldValueForCol(Collection col) throws NoSuchFieldException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        //先拿注解
        Class<?> clazz = col.iterator().next().getClass();

        Field[] fields = clazz.getDeclaredFields();

        Map<String,Object> cache = new HashMap<>();

        for (Field needField : fields){
            NeedSetValue sv = needField.getAnnotation(NeedSetValue.class);
            if (sv == null)
                continue;
            needField.setAccessible(true);
            Object bean = this.applicationContext.getBean(sv.beanClass());

            Method method = sv.beanClass().getMethod(sv.method(), clazz.getDeclaredField(sv.param()).getType());

            Field paramField = clazz.getDeclaredField(sv.param());

            paramField.setAccessible(true);
            Field targetField =null;
            boolean needInnerField = !StringUtils.isEmpty(sv.targetField());

            String keyPrefix = sv.beanClass()+"-"+sv.method()+"-"+sv.targetField()+"-";

            for (Object obj : col){
                Object paramValue = paramField.get(obj);

                if (paramValue == null)
                    continue;
                Object value = null;
                //先拿缓存
                String key = keyPrefix + paramValue;

                if (cache.containsKey(key)){
                    value = cache.get(key);
                }else {
                    value = method.invoke(bean, paramValue);
                    if (needInnerField){
                        if (value != null){
                            if (targetField == null){
                                targetField = value.getClass().getDeclaredField(sv.targetField());
                                targetField.setAccessible(true);
                            }
                            value = targetField.get(value);
                        }
                    }
                    cache.put(key,value);
                }
                needField.set(obj,value);
            }
        }

    }

}
