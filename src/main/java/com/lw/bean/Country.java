package com.lw.bean;

import java.util.Date;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：Country </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/3/1 18:21
 */
public class Country {

    private int countryId;

    private String country;

    private Date lastUpdate;

    public Country() {
    }

    public Country(int countryId, String country, Date lastUpdate) {
        this.countryId = countryId;
        this.country = country;
        this.lastUpdate = lastUpdate;
    }

    public int getCountryId() {
        return countryId;
    }

    public void setCountryId(int countryId) {
        this.countryId = countryId;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
