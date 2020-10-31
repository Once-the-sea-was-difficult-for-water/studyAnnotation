package com.demo.threadlocal;

import lombok.Data;

public class Person {
    public String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}