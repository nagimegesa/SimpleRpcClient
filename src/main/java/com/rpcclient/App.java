package com.rpcclient;

import com.rpcclient.protoc.hello.HelloWorldRequest;
import com.rpcclient.rpc.RpcClient;
import com.rpcclient.rcpservice.HelloService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.annotation.Resource;

@SpringBootApplication
public class App {

    @Resource
    RpcClient client;

    public static void main(String[] args) {
        ConfigurableApplicationContext run = SpringApplication.run(App.class, args);
        run.getBean(App.class).test();
    }

    void test() {
        HelloService helloService = client.newService(HelloService.class);
        HelloWorldRequest hello = HelloWorldRequest.newBuilder().setMsg("hello").build();
        System.out.println(helloService.hello(hello));
    }
}
