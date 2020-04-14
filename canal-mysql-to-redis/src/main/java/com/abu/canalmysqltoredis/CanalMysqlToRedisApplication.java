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
