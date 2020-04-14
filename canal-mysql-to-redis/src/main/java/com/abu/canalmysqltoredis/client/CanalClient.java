package com.abu.canalmysqltoredis.client;

import com.abu.canalmysqltoredis.utils.RedisUtil;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry.*;
import com.alibaba.otter.canal.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Copyright © 2018 zxl
 * <p>
 * DESCRIPTION        这里主要做两个工作，一个是循环从Canal上取数据，一个是将数据更新至Redis
 *
 * @create 2018-04-16 16:04
 * @author: zxl
 */
@Component
public class CanalClient {

    //日志
    private  final Logger logger = LoggerFactory.getLogger(CanalClient.class);

    public  void syn() {

        // 创建链接
        CanalConnector connector = CanalConnectors.newSingleConnector(new InetSocketAddress("192.168.126.136",
                11111), "example", "", "");//IP和端口为上文中 canal.properties中的IP和端口，“example”为默认，用户名密码不填

        logger.info("正在连接...");
        System.out.println("正在连接...");
        System.out.println(connector);
        int batchSize = 1000;
        try {
            connector.connect();
            connector.subscribe(".*\\..*");
            logger.info("连接成功");
            System.out.println("连接成功");
            connector.rollback();
            while (true) {
                Message message = connector.getWithoutAck(batchSize); // 获取指定数量的数据
                long batchId = message.getId();
                int size = message.getEntries().size();
                if (batchId == -1 || size == 0) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    printEntry(message.getEntries());
                }

                connector.ack(batchId); // 提交确认
                // connector.rollback(batchId); // 处理失败, 回滚数据
            }

        } finally {
            connector.disconnect();
            logger.info("连接释放成功");
            System.out.println("连接释放成功");
        }
    }

    private  void printEntry( List<Entry> entrys) {
        for (Entry entry : entrys) {
            if (entry.getEntryType() == EntryType.TRANSACTIONBEGIN || entry.getEntryType() == EntryType.TRANSACTIONEND) {
                continue;
            }

            RowChange rowChage = null;
            try {
                rowChage = RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                throw new RuntimeException("ERROR ## parser of eromanga-event has an error , data:" + entry.toString(),
                        e);
            }

            EventType eventType = rowChage.getEventType();
            System.out.println(String.format("================> binlog[%s:%s] , name[%s,%s] , eventType : %s",
                    entry.getHeader().getLogfileName(), entry.getHeader().getLogfileOffset(),
                    entry.getHeader().getSchemaName(), entry.getHeader().getTableName(),
                    eventType));

            for (RowData rowData : rowChage.getRowDatasList()) {
                if (eventType == EventType.DELETE) {
                    redisDelete(rowData.getBeforeColumnsList());
                    logger.info("删除redis数据成功");
                } else if (eventType == EventType.INSERT) {
                    redisInsert(rowData.getAfterColumnsList());
                    logger.info("成功新增数据到redis");
                } else {
                    System.out.println("------->修改之前的数据为：");
//                    printColumn(rowData.getBeforeColumnsList());
                    logger.info("修改之前的数据为："+printColumn(rowData.getBeforeColumnsList()));
                    System.out.println("------->修改之后的数据为：");
                    redisUpdate(rowData.getAfterColumnsList());
//                    printColumn(rowData.getBeforeColumnsList());
                    logger.info("修改之后的数据为："+printColumn(rowData.getAfterColumnsList()));
                }
            }
        }
    }



    private  String printColumn( List<Column> columns) {
        String s = null;
        for (Column column : columns) {
            s = column.getName() + " : " + column.getValue() + "    update=" + column.getUpdated();
            System.out.println(s);
            logger.info("字段名为："+column.getName() + " 值为: " + column.getValue() + "  是否更新为：" +Integer.toString(column.getUpdated()==true?1:0));
            s=s+"    ";
        }
        return s;
    }
//下面是往redis里操作数据
    private static void redisInsert( List<Column> columns){
        JSONObject json=new JSONObject();
        for (Column column : columns) {
            json.put(column.getName(), column.getValue());
        }
        if(columns.size()>0){
            RedisUtil.stringSet(columns.get(0).getValue(),json.toJSONString());
        }
    }

    private   void redisUpdate( List<Column> columns){
        JSONObject json=new JSONObject();
        for (Column column : columns) {
            json.put(column.getName(), column.getValue());
//            logger.info("更新到redis后的字段为："+column.getName()+"  值为："+column.getValue());
        }
        if(columns.size()>0){
//            RedisUtil.stringSet("ceshi:"+ columns.get(0).getValue(),json.toJSONString());//加上“ceshi：”的话，是redis的结构有“ceshi”的文件夹，数据会在这个文件夹下
            RedisUtil.stringSet(columns.get(0).getValue(),json.toJSONString());
        }
    }

    private static  void redisDelete( List<Column> columns){
        JSONObject json=new JSONObject();
        for (Column column : columns) {
            json.put(column.getName(), column.getValue());
        }
        if(columns.size()>0){
//            RedisUtil.delKey("ceshi:"+ columns.get(0).getValue());
            RedisUtil.delKey(columns.get(0).getValue());
        }
    }
}
