package com.demo.influxdb;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;

import java.time.Instant;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：Mem </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/10/31 17:45
 */
@Measurement(name = "mem")
public class Mem {
    @Column(tag = true)
    String host;
    @Column
    Long used_percent;
    @Column(timestamp = true)
    Instant time;
}