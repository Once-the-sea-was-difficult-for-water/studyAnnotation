package com.demo.proxy.jdk;
/** 
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：CommonPerson </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/10/2 18:15
 */
public class CommonPerson2 implements BuyTicket{
    @Override
    public void buyTicket() {
        //输出结果;
        System.out.println("--"+"买到票了");
    }
}
