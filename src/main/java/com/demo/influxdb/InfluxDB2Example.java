package com.demo.influxdb;
/** 
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：InfluxDB2Example </p >
 * @Description <p>类描述：2.0写入</p >
 * @author 刘威
 * @version 2.0 2020/10/31 17:43
 */

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.demo.zookeeper.SnowflakeIdGenerator;
import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.Task;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxTable;

public class InfluxDB2Example {
    public static void main(final String[] args) {

        // You can generate a Token from the "Tokens Tab" in the UI
        String token = "zRvtzbn5QUkOWZb1y6ctU1MGAu_3vSki6tGxUiBRX03BZJco83WDP00MOGdlLw8IA_jrcTm2rtDNTXRSmEQ3aQ==";
        String bucket = "demo";
        String org = "personal";
        InfluxDBClient client = InfluxDBClientFactory.create("http://121.36.31.202:8086", token.toCharArray());
        WriteApi writeApi = client.getWriteApi();


     Random random = new Random(1);
  /*         Mem mem1 = new Mem();
        mem1.host = "host" + random.nextInt(10);
        mem1.used_percent = Long.valueOf(123);
        mem1.time = Instant.now();
        writeApi.writeMeasurement(bucket, org, WritePrecision.NS, mem1);
        System.out.println("-->>"+ Instant.now()+"-->"+mem1.used_percent);*/
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            Mem mem1 = new Mem();
            mem1.host = "host" + random.nextInt(10);
            mem1.used_percent = SnowflakeIdGenerator.instance.nextId();
            mem1.time = Instant.now();
            writeApi.writeMeasurement(bucket, org, WritePrecision.NS, mem1);
            System.out.println("-->>"+ Instant.now()+"-->"+mem1.used_percent);
        },0,3, TimeUnit.SECONDS);
/*        String query = String.format("from(bucket: %s) |> range(start: -1h)", bucket);
        List<FluxTable> tables = client.getQueryApi().query(query, org);
        System.out.println("-->>"+tables.toString());*/
    }
}