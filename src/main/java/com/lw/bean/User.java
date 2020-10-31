package com.lw.bean;

import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.List;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：User </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/7/12 1:41
 */
public class User implements Serializable {
    private String code;

    private String name;
    List list;
    private static final ObjectStreamField[] serialPersistentFields =
            { new ObjectStreamField("list", List.class),new ObjectStreamField("code",String.class)
            };
    public User(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
