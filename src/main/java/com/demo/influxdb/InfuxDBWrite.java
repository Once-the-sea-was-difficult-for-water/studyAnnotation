package com.demo.influxdb;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.QueryResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：InfuxDBWrite </p >
 * @Description <p>类描述：1.8写入</p >
 * @author 刘威
 * @version 2.0 2020/11/1 16:56
 */
public class InfuxDBWrite {

    public static void main(String[] args) {
        batchInsert();
        singleInsert();
        query();
    }

    /**
     * <p>方法描述：批量循环插入 </p >
     * @param
     * @author 刘威
     * @since 2020/11/1 18:00
     * @return void
     */
    public static void batchInsertCycle(){
        InfluxDBConnection influxDBConnection = new InfluxDBConnection("admin", "1qaz@WSX", "http://127.0.0.1:8086", "test", "autogen");
        Map<String, String> tags = new HashMap<String, String>();
        tags.put("tag1", "标签值");
        tags.put("tag2", "标签值");
        Map<String, Object> fields1 = new HashMap<String, Object>();
        fields1.put("field1", "abc");
        // 数值型，InfluxDB的字段类型，由第一天插入的值得类型决定
        fields1.put("field2", 123456);
        Map<String, Object> fields2 = new HashMap<String, Object>();
        fields2.put("field1", "String类型");
        fields2.put("field2", 4165165);
        // 一条记录值
        Point point1 = influxDBConnection.pointBuilder("test", System.currentTimeMillis(), tags, fields1);
        Point point2 = influxDBConnection.pointBuilder("test", System.currentTimeMillis(), tags, fields2);
        // 将两条记录添加到batchPoints中
        BatchPoints batchPoints1 = BatchPoints.database("test").tag("tag1", "标签值1").retentionPolicy("autogen")
                .consistency(InfluxDB.ConsistencyLevel.ALL).build();
        BatchPoints batchPoints2 = BatchPoints.database("test").tag("tag2", "标签值2").retentionPolicy("autogen")
                .consistency(InfluxDB.ConsistencyLevel.ALL).build();
        batchPoints1.point(point1);
        batchPoints2.point(point2);
        // 将两条数据批量插入到数据库中
        influxDBConnection.batchInsert(batchPoints1);
        influxDBConnection.batchInsert(batchPoints2);
    }
    /**
     * <p>方法描述：序列化批量插入 </p >
     * @param
     * @author 刘威
     * @since 2020/11/1 18:00
     * @return void
     */
    public static void batchInsert(){
        InfluxDBConnection influxDBConnection = new InfluxDBConnection("admin", "1qaz@WSX", "http://127.0.0.1:8086", "test", "autogen");
        Map<String, String> tags1 = new HashMap<String, String>();
        tags1.put("tag1", "标签值");
        Map<String, String> tags2 = new HashMap<String, String>();
        tags2.put("tag2", "标签值");
        Map<String, Object> fields1 = new HashMap<String, Object>();
        fields1.put("field1", "abc");
        // 数值型，InfluxDB的字段类型，由第一天插入的值得类型决定，数据类型不一致会造成插入失败
        fields1.put("field2", 123456);
        Map<String, Object> fields2 = new HashMap<String, Object>();
        fields2.put("field1", "String类型");
        fields2.put("field2", 3);
        // 一条记录值
        Point point1 = influxDBConnection.pointBuilder("test", System.currentTimeMillis(), tags1, fields1);
        Point point2 = influxDBConnection.pointBuilder("test", System.currentTimeMillis(), tags2, fields2);
        BatchPoints batchPoints1 = BatchPoints.database("test").tag("tag1", "标签值1")
                .retentionPolicy("autogen").consistency(InfluxDB.ConsistencyLevel.ALL).build();
        // 将两条记录添加到batchPoints中
        batchPoints1.point(point1);
        BatchPoints batchPoints2 = BatchPoints.database("test").tag("tag2", "标签值2")
                .retentionPolicy("autogen").consistency(InfluxDB.ConsistencyLevel.ALL).build();
        // 将两条记录添加到batchPoints中
        batchPoints2.point(point2);
        // 将不同的batchPoints序列化后，一次性写入数据库，提高写入速度
        List<String> records = new ArrayList<String>();
        records.add(batchPoints1.lineProtocol());
        records.add(batchPoints2.lineProtocol());
        // 将两条数据批量插入到数据库中
        influxDBConnection.batchInsert("test", "autogen", InfluxDB.ConsistencyLevel.ALL, records);
    }

    public static void singleInsert(){
        InfluxDBConnection influxDBConnection = new InfluxDBConnection("admin", "1qaz@WSX", "http://127.0.0.1:8086", "test", "autogen");
        Map<String, String> tags = new HashMap<String, String>();
        tags.put("tag1", "标签值");
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("field1", "String类型");
        // 数值型，InfluxDB的字段类型，由第一天插入的值得类型决定
        fields.put("field2", 3.141592657);
        // 时间使用毫秒为单位
        influxDBConnection.insert("test2", tags, fields, System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }


    public static void query(){
        InfluxDBConnection influxDBConnection = new InfluxDBConnection("admin", "1qaz@WSX", "http://127.0.0.1:8086", "test", "autogen");
        QueryResult results = influxDBConnection
                .query("SELECT * FROM test  order by time desc limit 1000");
        //results.getResults()是同时查询多条SQL语句的返回值，此处我们只有一条SQL，所以只取第一个结果集即可。
        QueryResult.Result oneResult = results.getResults().get(0);
        if (oneResult.getSeries() != null) {
            List<List<Object>> valueList = oneResult.getSeries().stream().map(QueryResult.Series::getValues)
                    .collect(Collectors.toList()).get(0);
            if (valueList != null && valueList.size() > 0) {
                for (List<Object> value : valueList) {
                    Map<String, String> map = new HashMap<String, String>();
                    // 数据库中字段1取值
                    String field1 = value.get(0) == null ? null : value.get(0).toString();
                    System.out.println("-->>field1"+field1);
                    // 数据库中字段2取值
                    String field2 = value.get(1) == null ? null : value.get(1).toString();
                    System.out.println("-->>field2"+field2);
                    // TODO 用取出的字段做你自己的业务逻辑……
                }
            }
        }
    }
}
