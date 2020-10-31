package com.demo.proxy.jdk;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：HuangNiu </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/10/2 18:16
 */
public class HuangNiu implements InvocationHandler {
    private final CommonPerson target;
    public HuangNiu(CommonPerson target) {
        this.target = target;
    }



    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //输出结果;
        System.out.println("黄牛代购");
        Object res = method.invoke(target,args);
        return res;
    }
}
