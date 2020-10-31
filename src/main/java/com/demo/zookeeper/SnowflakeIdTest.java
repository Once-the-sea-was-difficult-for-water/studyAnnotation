package com.demo.zookeeper;
/** 
 * @projectName 包有帮订货系统 
 * @ClassName <p>类名称：SnowflakeIdTest </p >
 * @Description <p>类描述：类描述</p >
 * @author 刘威
 * @version 2.0 2020/9/20 21:39
 */
import lombok.extern.slf4j.Slf4j;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
/**
 * 测试SnowflakeIdWorker、SnowflakeIdGenerator
 * create by 尼恩 @ 疯狂创客圈
 **/
@Slf4j
public class SnowflakeIdTest {
    /**
     * 测试用例
     */
    public static void snowflakeIdTest() {
//或者节点的id
        //long workId = SnowflakeIdWorker.instance.getId();
        long workId =6666;
//初始化id生成器
        SnowflakeIdGenerator.instance.init(workId);
//创建一个线程池，并发生成id
        ExecutorService es = Executors.newFixedThreadPool(10);
        final HashSet idSet = new HashSet();
        Collections.synchronizedCollection(idSet);
        long start = System.currentTimeMillis();
        for (int i = 0; i< 10; i++)
            es.execute(() -> {
                for (long j = 0; j < 10000; j++) {
                    long id = SnowflakeIdGenerator.instance.nextId();
                    synchronized (idSet) {
                        idSet.add(id);
                        System.out.println("-->>"+id);
                    }
                }
            });
//关闭线程池
        es.shutdown();
        try {
            es.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long end = System.currentTimeMillis();
    }

    public static void main(String[] args) {
        snowflakeIdTest();
    }
}
