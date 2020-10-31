package com.demo.notes;
/** 
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：Person </p >
 * @Description <p>类描述：在Java中定义一个不做事且没有参数的构造方法的作用</p >
 * @author 刘威
 * @version 2.0 2020/10/25 13:59
 */
public class Person extends SuperClass{
    private int age;
    public String get(){
        return "java程序在执行子类的构造方法之前，" +
                "如果没有用super()来调用父类特定的构造方法，" +
                "则会调用父类中“没有参数的构造方法”。因此，" +
                "如果父类中只定义了有参数的构造方法，" +
                "而在子类的构造方法中又没有用super()来调用父类中特定的构造方法，" +
                "则编译时将发生错误，因为Java程序在父类中找不到没有参数的构造方法" +
                "可供执行。解决办法是在父类里加上一个不做事且没有参数的构造方法。";
    }

    public Person(int age) {
        super("lalala");
        this.age = age;
    }



    public static void main(String[] args) {
        Person person1 = new Person(22);
        System.out.println("-->>"+person1.get());

        Person person2 = new Person(33);
        swap(person1,person2);
        System.out.println("-->>"+person1.age);
        //输出结果;
        System.out.println("-->>"+person2.age);

    }

    public static void swap(Person person1, Person person2){
        Person person3 = person2;
        person2 = person1;
        person1 = person3;
        System.out.println("-->>"+person1.age);
        //输出结果;
        System.out.println("-->>"+person2.age);
        person1.age = 66;
    }
}
