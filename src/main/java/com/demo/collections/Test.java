package com.demo.collections;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.TreeSet;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：Test </p >
 * @Description <p>类描述：collectio</p >
 * @author 刘威
 * @version 2.0 2020/10/25 0:28
 */
public class Test {

    public static void main(String[] args) {
        Object object = new Object();
        //不安全
        HashSet hashSet = new HashSet();
        hashSet.add("asd");
        //不安全
        HashMap hashMap = new HashMap();
        hashMap.put("lala","s1025");
        //线程安全
        Hashtable hashtable = new Hashtable();
        hashtable.put("sad","SDFa");
        String str = "sdfafalnkdcnoisanvjfovndf";
        char [] chars = str.toCharArray();
        TreeSet treeSet = new TreeSet();
        for (char cha:chars) {
            treeSet.add(cha);
        }
        //输出结果;
        System.out.println("---"+treeSet.toString());



    }
}
