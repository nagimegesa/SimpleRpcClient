package com.rpcclient.rpc;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.rpcclient.rpc.annotation.RpcMethod;
import com.rpcclient.rpc.exception.*;
import com.rpcclient.rpc.transport.Transport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

public class RpcCallerDirect implements InvocationHandler {

    private Transport transport;
    private final String serviceName;
    private final int retry;
    private final Supplier<Transport> getTransport;

    public RpcCallerDirect(String serviceName, Supplier<Transport> getTransport, int retry) {
        this.serviceName = serviceName;
        this.retry = retry;
        this.getTransport = getTransport;
        this.transport = getTransport.get();
    }

    public RpcCallerDirect(String serviceName, Transport transport, int retry) {
        this.serviceName = serviceName;
        this.transport = transport;
        this.retry = retry;
        this.getTransport = null;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if(method.getDeclaringClass() == Object.class) { // 继承class类的方法放行
            return method.invoke(proxy, args);
        }

        RpcMethod rpc = method.getAnnotation(RpcMethod.class);
        if(rpc == null) {
            throw new RpcException("Rpc method must has RpcMethod Annotation");
        }

        if (args != null && args.length != 1) {
            throw new RpcException("RPC method takes exactly one argument");
        }

        GeneratedMessageV3 message = null;
        if(args != null) {
            message = (GeneratedMessageV3) args[0];
        }
        Transport.SimpleResponse response = null;

        RuntimeException lastException = null;
        for(int i = 0; i < retry; ++i) {
            try {
                response = transport.call(serviceName, rpc.name(), message);
                break;
            } catch (RpcCallWriteFailedException | RpcCallTimeoutException e) {
                lastException = e; // 注意这里假设 call timeout 可以重试
            } catch (RpcConnectionClosedException e) {
                lastException = e;
                if(getTransport != null) { // 连接关闭尝试换一个 transport, router会处理好黑名单
                    transport = getTransport.get();
                }
            }
        }

        if(response == null && lastException != null) { // 多次尝试出错
            throw lastException;
        }

        Class<?> retType = method.getReturnType();
        if(retType == void.class || retType == Void.class) {
            return null;                // 没有返回值
        }

        // 到这里 response 应该不可能为 null, 如果有那就是序列化错误，没法处理
        assert response != null;

        try {
            Method parse = retType.getMethod("parseFrom", byte[].class);
            return parse.invoke(null, (Object) response.response);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InvalidProtocolBufferException) {
                throw new RpcException(RpcErrorCode.UNKNOWN_RESPONSE.code(), response.id, "Bad response");
            }
            throw cause;
        }
    }
}