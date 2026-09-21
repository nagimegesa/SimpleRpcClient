package com.rpcclient.service;


import com.rpcclient.protoc.hello.HelloWorldRequest;
import com.rpcclient.protoc.hello.HelloWorldResponse;
import com.rpcclient.rpc.annotation.RpcMethod;
import com.rpcclient.rpc.annotation.RpcService;

@RpcService(name = "HelloService")
public interface HelloService {
    @RpcMethod(name = "hello")
    public HelloWorldResponse hello(HelloWorldRequest request);
}