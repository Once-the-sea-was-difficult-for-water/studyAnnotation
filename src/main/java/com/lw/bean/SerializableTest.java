package com.lw.bean;

import java.io.*;
import java.util.List;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：SerializableTest </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/7/11 21:59
 */
public class SerializableTest implements Serializable {
  /*  MyList list; // Changed data type, was previously List

    private void readObject(ObjectInputStream ois)  throws IOException, ClassNotFoundException {
        // Obtain all the data from the original serialized form
        ObjectInputStream.GetField gf = ois.readFields();
        // Extract original list from that data and create the
        // new data type, MyList from the elements it contains
        this.list = MyList.createFromList(gf.get("list", list)));
    }*/
    /**
     * 将User对象作为文本写入磁盘
     */
    public static void writeObj() {
        User user = new User("1001", "Joe");
        try {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream("D:\\text.txt"));
            objectOutputStream.writeObject(user);
            objectOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * 将类从文本中提取并赋值给内存中的类
     */
    public static void readObj() {
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream("D:\\text.txt"));
            try {
                Object object = objectInputStream.readObject();
                User user = (User) object;
                System.out.println(user.getName());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public static void main(String args[]) {
        //readObj();
        //writeObj();
        User user = new User("1001", "Joe");
        System.out.println("-->>"+user.getName());
    }

}
