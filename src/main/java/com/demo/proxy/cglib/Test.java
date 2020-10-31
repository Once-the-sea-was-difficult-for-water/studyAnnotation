package com.demo.proxy.cglib;

import org.springframework.cglib.proxy.Enhancer;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：Test </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/10/2 18:29
 */
public class Test {
    public static void main(String[] args) {
        HuangNiu huangNiu = new HuangNiu();
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(CommonPerson.class);
        enhancer.setCallback(huangNiu);
        CommonPerson commonPerson = (CommonPerson) enhancer.create();
        commonPerson.buyTicket();
    }
}
