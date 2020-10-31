package com.demo.threadlocal;


import org.springframework.transaction.annotation.Transactional;

import java.lang.ref.WeakReference;
import java.util.concurrent.Future;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：ThreadLocalTest </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/9/20 23:56
 */
public class ThreadLocalTest {
    static ThreadLocal<Person> threadLocal = new ThreadLocal<>();
    static ThreadLocal<Person> threadLocal2 = new ThreadLocal<>();
    static ThreadLocal<Person> threadLocal3 = new ThreadLocal<>();

    public static void main(String[] args) {
        Person person = new Person();
        person.setName("lalala");
            threadLocal.set(person);
            Person person1 = threadLocal.get();
        System.out.println("-->>"+person1.getName());
        Person person2 = new Person();
        person2.setName("6666");
        threadLocal.set(person2);
        Person person3 = threadLocal.get();
        System.out.println("-->>"+person3.getName());

        threadLocal2.set(person2);
        threadLocal3.set(person2);
        System.out.println("-->>threadLocal2"+threadLocal2.get().getName());
    /*    new Thread(new Runnable() {
            @Override
            public void run() {

            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
             threadLocal.set(new Person());
            }
        }).start();*/
    }
}
