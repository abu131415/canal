package com.abu.canal.demo;


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
