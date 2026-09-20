package service;


import protoc.hello.HelloWorldRequest;
import protoc.hello.HelloWorldResponse;
import rpc.annotation.RpcMethod;
import rpc.annotation.RpcService;

@RpcService(name = "HelloService")
public interface HelloService {
    @RpcMethod(name = "hello")
    public HelloWorldResponse hello(HelloWorldRequest request);
}