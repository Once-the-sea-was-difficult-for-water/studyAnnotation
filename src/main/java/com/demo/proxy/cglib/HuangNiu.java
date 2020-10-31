package com.demo.proxy.cglib;

import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：Huangniu </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/10/2 18:25
 */
public class HuangNiu implements MethodInterceptor {
    @Override
    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
        //输出结果;
        System.out.println("阿黄牛带票");
        Object res = methodProxy.invokeSuper(o,objects);
        return res;
    }
}
