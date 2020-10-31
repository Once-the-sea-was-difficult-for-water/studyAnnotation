package com.demo.proxy.jdk;

import java.lang.reflect.Proxy;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：Test </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/10/2 18:18
 */
public class Test {
    public static void main(String[] args) {
        CommonPerson commonPerson = new CommonPerson();
        HuangNiu huangNiu = new HuangNiu(commonPerson);
        BuyTicket buyTicket = (BuyTicket) Proxy.newProxyInstance(CommonPerson.class.getClassLoader(),new Class[]{BuyTicket.class},huangNiu);
        buyTicket.buyTicket();
    }
}
