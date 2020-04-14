# canal介绍

## 1.应用场景

canal 通过实时同步数据库表的方式实现，例如我们要统计每天注册与登录人数，我们只需把会员表同步到统计库中，实现本地统计就可以了，这样效率更高，耦合度更低，Canal就是一个很好的数据库同步工具。canal是阿里巴巴旗下的一款开源项目，纯Java开发。基于数据库增量日志解析，提供增量数据订阅&消费，目前主要支持了MySQL。 

## 2.canal环境搭建

**canal的原理是基于mysql binlog技术，所以这里一定需要开启mysql的binlog写入功能**

**（1）**开启binlog

```

修改 mysql 的配置文件 my.cnf
vi /etc/my.cnf 
追加内容：
log-bin=mysql-bin     #binlog文件名
binlog_format=ROW     #选择row模式
server_id=1           #mysql实例id,不能和canal的slaveId重复
```

**(2)**登录 mysql 客户端，查看 log_bin 变量

```
mysql> show variables like 'log_bin';
```

**(3)**在mysql里面添加以下的相关用户和权限

```
CREATE USER 'canal'@'%' IDENTIFIED BY 'canal';
GRANT SHOW VIEW, SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
FLUSH PRIVILEGES;
```

## 3、下载安装Canal服务

下载地址：<https://github.com/alibaba/canal/releases>

**（1）下载之后，放到目录中，解压文件**

cd `/usr/local/canal`

```
canal.deployer-1.1.4.tar.gz

tar zxvf canal.deployer-1.1.4.tar.gz
```

**（2）修改配置文件**

```
vi conf/example/instance.properties
#需要改成自己的数据库信息
canal.instance.master.address=192.168.44.132:3306
#需要改成自己的数据库用户名与密码
canal.instance.dbUsername=canal
canal.instance.dbPassword=canal
#需要改成同步的数据库表规则，例如只是同步一下表
#canal.instance.filter.regex=.*\\..*
canal.instance.filter.regex=guli_ucenter.ucenter_member
```

**（3）进入bin目录下启动**

**sh bin/startup.sh**

**引入依赖**

```
<dependency>
    <groupId>com.alibaba.otter</groupId>
    <artifactId>canal.client</artifactId>
    <version>1.1.4</version>
</dependency>
<dependency>
    <groupId>commons-dbutils</groupId>
    <artifactId>commons-dbutils</artifactId>
    <version>1.7</version>
</dependency>
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>druid-spring-boot-starter</artifactId>
    <version>1.1.22</version>
</dependency>
```

##  4、创建application.properties配置文件

```
# 服务端口
server.port=8088
# 服务名
spring.application.name=canal-client
# mysql数据库连接
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://localhost:3306/db_order?serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=kang123
spring.datasource.type=com.alibaba.druid.pool.DruidDataSource
spring.datasource.druid.initial-size=6
spring.datasource.druid.max-wait=3000
spring.datasource.druid.max-active=20
```

编写canal客户端 ==复制过去就行==

（1）两个mysql同步，要求两个数据中的表要是一致的

```
package com.abu.canal.demo.client;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry.*;
import com.alibaba.otter.canal.protocol.Message;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class CanalClient {
    //sql队列
    private Queue<String> SQL_QUEUE = new ConcurrentLinkedQueue<>();
    @Autowired
    private DataSource dataSource;

    /**
     * 30
     * canal入库方法
     * 31
     */
    public void run() {
        CanalConnector connector = CanalConnectors.newSingleConnector(new InetSocketAddress("192.168.126.136",
                11111), "example", "", "");
        int batchSize = 1000;
        try {
            connector.connect();
            connector.subscribe(".*\\..*");
            connector.rollback();
            try {
                while (true) {
                    //尝试从master那边拉去数据batchSize条记录，有多少取多少
                    Message message = connector.getWithoutAck(batchSize);
                    long batchId = message.getId();
                    int size = message.getEntries().size();
                    if (batchId == -1 || size == 0) {
                        Thread.sleep(1000);
                    } else {
                        dataHandle(message.getEntries());
                    }
                    connector.ack(batchId);
                    //当队列里面堆积的sql大于一定数值的时候就模拟执行
                    if (SQL_QUEUE.size() >= 1) {
                        executeQueueSql();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } finally {
            connector.disconnect();
        }
    }

    /**
     * 模拟执行队列里面的sql语句
     */
    public void executeQueueSql() throws SQLException {
        int size = SQL_QUEUE.size();
        for (int i = 0; i < size; i++) {
            String sql = SQL_QUEUE.poll();
            System.out.println("[sql]----> " + sql);
            this.execute(sql.toString());
        }
    }

    /**
     * 83
     * 数据处理
     * 84
     * <p>
     * 85
     *
     * @param entrys 86
     */
    private void dataHandle(List<Entry> entrys) throws InvalidProtocolBufferException {
        for (Entry entry : entrys) {
            if (EntryType.ROWDATA == entry.getEntryType()) {
                RowChange rowChange = RowChange.parseFrom(entry.getStoreValue());
                EventType eventType = rowChange.getEventType();
                if (eventType == EventType.DELETE) {
                    saveDeleteSql(entry);
                } else if (eventType == EventType.UPDATE) {
                    saveUpdateSql(entry);
                } else if (eventType == EventType.INSERT) {
                    saveInsertSql(entry);
                }
            }
        }
    }

    /**
     * 104
     * 保存更新语句
     * 105
     * <p>
     * 106
     *
     * @param entry 107
     */
    private void saveUpdateSql(Entry entry) {
        try {
            RowChange rowChange = RowChange.parseFrom(entry.getStoreValue());
            List<RowData> rowDatasList = rowChange.getRowDatasList();
            for (RowData rowData : rowDatasList) {
                List<Column> newColumnList = rowData.getAfterColumnsList();
                StringBuffer sql = new StringBuffer("update " + entry.getHeader().getTableName() + " set ");
                for (int i = 0; i < newColumnList.size(); i++) {
                    sql.append(" " + newColumnList.get(i).getName()
                            + " = '" + newColumnList.get(i).getValue() + "'");
                    if (i != newColumnList.size() - 1) {
                        sql.append(",");
                    }
                }
                sql.append(" where ");
                List<Column> oldColumnList = rowData.getBeforeColumnsList();
                for (Column column : oldColumnList) {
                    if (column.getIsKey()) {
                        //暂时只支持单一主键
                        sql.append(column.getName() + "=" + column.getValue());
                        break;
                    }
                }
                SQL_QUEUE.add(sql.toString());
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    /**
     * 139
     * 保存删除语句
     * 140
     * <p>
     * 141
     *
     * @param entry 142
     */
    private void saveDeleteSql(Entry entry) {
        try {
            RowChange rowChange = RowChange.parseFrom(entry.getStoreValue());
            List<RowData> rowDatasList = rowChange.getRowDatasList();
            for (RowData rowData : rowDatasList) {
                List<Column> columnList = rowData.getBeforeColumnsList();
                StringBuffer sql = new StringBuffer("delete from " + entry.getHeader().getTableName() + " where ");
                for (Column column : columnList) {
                    if (column.getIsKey()) {
                        //暂时只支持单一主键
                        sql.append(column.getName() + "=" + column.getValue());
                        break;
                    }
                }
                SQL_QUEUE.add(sql.toString());
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    /**
     * 165
     * 保存插入语句
     * 166
     * <p>
     * 167
     *
     * @param entry 168
     */
    private void saveInsertSql(Entry entry) {
        try {
            RowChange rowChange = RowChange.parseFrom(entry.getStoreValue());
            List<RowData> rowDatasList = rowChange.getRowDatasList();
            for (RowData rowData : rowDatasList) {
                List<Column> columnList = rowData.getAfterColumnsList();
                StringBuffer sql = new StringBuffer("insert into " + entry.getHeader().getTableName() + " (");
                for (int i = 0; i < columnList.size(); i++) {
                    sql.append(columnList.get(i).getName());
                    if (i != columnList.size() - 1) {
                        sql.append(",");
                    }
                }
                sql.append(") VALUES (");
                for (int i = 0; i < columnList.size(); i++) {
                    sql.append("'" + columnList.get(i).getValue() + "'");
                    if (i != columnList.size() - 1) {
                        sql.append(",");
                    }
                }
                sql.append(")");
                SQL_QUEUE.add(sql.toString());
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    /**
     * 198
     * 入库
     * 199
     *
     * @param sql 200
     */
    public void execute(String sql) throws SQLException {
        Connection con = null;
        try {
            if (null == sql) return;
            con = dataSource.getConnection();
            QueryRunner qr = new QueryRunner();
            int row = qr.execute(con, sql);
            System.out.println("update: " + row);
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DbUtils.closeQuietly(con);
        }
    }
}

```

**编写启动类**

```

import com.abu.canal.demo.client.CanalClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CanalDemoApplication implements CommandLineRunner {

    @Autowired
    private CanalClient canalClient;

    public static void main(String[] args) {
        SpringApplication.run(CanalDemoApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        this.canalClient.run();
    }
}
```

***



（2）mysql数据同步到redis中

canal客户端

```
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
                11111), "example", "", "");//IP和端口为上文中 canal.properties中的IP和端口，“example”为默认，用户名密码不填

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
```



redis工具类

```
package com.abu.canalmysqltoredis.utils;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Copyright © 2018 zxl
 * <p>
 * DESCRIPTION
 *
 * @create 2018-04-16 16:19
 * @author: zxl
 */
public class RedisUtil {
    // Redis服务器IP
    private static String ADDR = "192.168.126.136";

    // Redis的端口号
    private static int PORT = 6379;

    // 访问密码
//    private static String AUTH = "asdf!@#$!@#$";

    // 可用连接实例的最大数目，默认值为8；
    // 如果赋值为-1，则表示不限制；如果pool已经分配了maxActive个jedis实例，则此时pool的状态为exhausted(耗尽)。
    private static int MAX_ACTIVE = 1024;

    // 控制一个pool最多有多少个状态为idle(空闲的)的jedis实例，默认值也是8。
    private static int MAX_IDLE = 200;

    // 等待可用连接的最大时间，单位毫秒，默认值为-1，表示永不超时。如果超过等待时间，则直接抛出JedisConnectionException；
    private static int MAX_WAIT = 10000;

    // 过期时间
    protected static int  expireTime = 660 * 660 *24;

    // 连接池
    protected static JedisPool pool;

    /**
     * 静态代码，只在初次调用一次
     */
    static {
        JedisPoolConfig config = new JedisPoolConfig();
        //最大连接数
        config.setMaxTotal(MAX_ACTIVE);
        //最多空闲实例
        config.setMaxIdle(MAX_IDLE);
        //超时时间
        config.setMaxWaitMillis(MAX_WAIT);
        //
        config.setTestOnBorrow(false);
        pool = new JedisPool(config, ADDR, PORT, 1000);
    }

    /**
     * 获取jedis实例
     */
    protected static synchronized Jedis getJedis() {
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
        } catch (Exception e) {
            e.printStackTrace();
            if (jedis != null) {
                pool.returnBrokenResource(jedis);
            }
        }
        return jedis;
    }

    /**
     * 释放jedis资源
     *
     * @param jedis
     * @param isBroken
     */
    protected static void closeResource(Jedis jedis, boolean isBroken) {
        try {
            if (isBroken) {
                pool.returnBrokenResource(jedis);
            } else {
                pool.returnResource(jedis);
            }
        } catch (Exception e) {

        }
    }

    /**
     *  是否存在key
     *
     * @param key
     */
    public static boolean existKey(String key) {
        Jedis jedis = null;
        boolean isBroken = false;
        try {
            jedis = getJedis();
            jedis.select(0);
            return jedis.exists(key);
        } catch (Exception e) {
            isBroken = true;
        } finally {
            closeResource(jedis, isBroken);
        }
        return false;
    }

    /**
     *  删除key
     *
     * @param key
     */
    public static void delKey(String key) {
        Jedis jedis = null;
        boolean isBroken = false;
        try {
            jedis = getJedis();
            jedis.select(0);
            jedis.del(key);
        } catch (Exception e) {
            isBroken = true;
        } finally {
            closeResource(jedis, isBroken);
        }
    }

    /**
     *  取得key的值
     *
     * @param key
     */
    public static String stringGet(String key) {
        Jedis jedis = null;
        boolean isBroken = false;
        String lastVal = null;
        try {
            jedis = getJedis();
            jedis.select(0);
            lastVal = jedis.get(key);
            jedis.expire(key, expireTime);
        } catch (Exception e) {
            isBroken = true;
        } finally {
            closeResource(jedis, isBroken);
        }
        return lastVal;
    }

    /**
     *  添加string数据
     *
     * @param key
     * @param value
     */
    public static String stringSet(String key, String value) {
        Jedis jedis = null;
        boolean isBroken = false;
        String lastVal = null;
        try {
            jedis = getJedis();
            jedis.select(0);
            lastVal = jedis.set(key, value);
            jedis.expire(key, expireTime);
        } catch (Exception e) {
            e.printStackTrace();
            isBroken = true;
        } finally {
            closeResource(jedis, isBroken);
        }
        return lastVal;
    }

    /**
     *  添加hash数据
     *
     * @param key
     * @param field
     * @param value
     */
    public static void hashSet(String key, String field, String value) {
        boolean isBroken = false;
        Jedis jedis = null;
        try {
            jedis = getJedis();
            if (jedis != null) {
                jedis.select(0);
                jedis.hset(key, field, value);
                jedis.expire(key, expireTime);
            }
        } catch (Exception e) {
            isBroken = true;
        } finally {
            closeResource(jedis, isBroken);
        }
    }
}
```

主引导类

```java
package com.abu.canalmysqltoredis;

import com.abu.canalmysqltoredis.client.CanalClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CanalMysqlToRedisApplication implements CommandLineRunner {

    @Autowired
    private CanalClient canalClient;

    public static void main(String[] args) {
        SpringApplication.run(CanalMysqlToRedisApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        canalClient.syn();
    }
}
```

配置文件信息

```
# 服务端口
server.port=8089
# 服务名
spring.application.name=canal-redis
# mysql数据库连接
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://localhost:3306/db_order?serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=kang123
spring.datasource.type=com.alibaba.druid.pool.DruidDataSource
spring.datasource.druid.initial-size=6
spring.datasource.druid.max-wait=3000
spring.datasource.druid.max-active=20
```