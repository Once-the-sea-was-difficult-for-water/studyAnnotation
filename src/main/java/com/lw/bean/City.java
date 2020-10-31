package com.lw.bean;

import com.lw.annotation.Girl;
import com.lw.annotation.NeedSetValue;
import com.lw.dao.CountryDao;

import java.io.Serializable;
import java.util.Date;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：City </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/2/29 23:25
 */
public class City implements Serializable {

    private int cityId;

    private String city;

    private int countryId;

    private Date lastUpdate;

    @NeedSetValue(beanClass = CountryDao.class, param = "countryId", method = "findById", targetField = "country")
    private String country;//反射 --》find --反射怎么调用  反射拿method    --》bean -->getAnnotation --->order结果集


    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public int getCityId() {
        return cityId;
    }

    public void setCityId(int cityId) {
        this.cityId = cityId;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public int getCountryId() {
        return countryId;
    }

    public void setCountryId(int countryId) {
        this.countryId = countryId;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public City() {
    }

    public City(int cityId, String city, int countryId, Date lastUpdate) {
        this.cityId = cityId;
        this.city = city;
        this.countryId = countryId;
        this.lastUpdate = lastUpdate;
    }
}
