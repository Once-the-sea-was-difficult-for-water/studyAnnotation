package com.lw.service;

import com.lw.bean.City;

import java.util.List;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：CityService </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/3/2 13:19
 */
public interface CityService {

    List<City> selectAll();
}
