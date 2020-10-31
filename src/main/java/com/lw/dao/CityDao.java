package com.lw.dao;

import com.lw.bean.City;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：CityDao </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/3/2 13:22
 */
public interface CityDao {

    @Select("select * from city")
    List<City> selectAll();
}
