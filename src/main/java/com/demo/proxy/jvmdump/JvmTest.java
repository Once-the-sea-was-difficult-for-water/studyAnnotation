package com.demo.proxy.jvmdump;


import java.util.ArrayList;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：JvmTest </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/10/7 16:03
 */
public class JvmTest {
    static class OOMObject{
        String name;
        int age;
    }
    public static void main(String[] args) {
        ArrayList<OOMObject> oomObjects = new ArrayList<>();
        while (true){
            oomObjects.add(new OOMObject());
        }
    }
}
