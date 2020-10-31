package com.lw.dao;

import com.lw.bean.Country;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：CountryDao </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/3/2 12:56
 */
public interface CountryDao {

    @Select("select * from country")
    List<Country> selectAll();


    @Select("select * from country where country_id = #{0}")
    Country findById(int countryId);
}
