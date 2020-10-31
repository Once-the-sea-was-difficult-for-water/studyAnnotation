package com.lw.service.impl;

import com.lw.annotation.NeedSetValueField;
import com.lw.bean.City;
import com.lw.dao.CityDao;
import com.lw.service.CityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：CityServiceImpl </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/3/2 13:19
 */
@Service
public class CityServiceImpl implements CityService {

    @Autowired
    private CityDao cityDao;

    @NeedSetValueField
    @Override
    public List<City> selectAll() {
        return cityDao.selectAll();
    }
}
