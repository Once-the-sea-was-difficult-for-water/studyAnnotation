package com.lw.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lw.bean.City;
import com.lw.bean.User;
import com.lw.service.CityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：CityController </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/3/2 13:20
 */
@RestController
@RequestMapping("city")
public class CityController {

    @Autowired
    private CityService cityService;

    @RequestMapping("select/all")
    public List<City> selectAll(){
        return cityService.selectAll();
    }
    @RequestMapping("select/user")
    public Object select() throws JsonProcessingException {
        User user = new User("1001", "Joe");
        Object object = user;
        return object;
    }

}
