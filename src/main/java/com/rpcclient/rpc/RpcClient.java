package com.rpcclient.rpc;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.rpcclient.rpc.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.rpcclient.rpc.annotation.RpcMethod;
import com.rpcclient.rpc.annotation.RpcService;
import com.rpcclient.rpc.transport.RpcTransport;
import com.rpcclient.rpc.transport.Transport;

import javax.annotation.Resource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RpcClient {

    @Resource
    public ServiceFinder finder;

    @Value("${rpc.service.timeout:3000}")
    private int transportWriteTimeout;

    @Value("${rpc.service.retry:3}")
    private int retry;

    private static final ConcurrentHashMap<ServiceAddress, Transport> serviceTransport = new ConcurrentHashMap<>();

    private Transport getTransport(String service) {
        for(int i = 0; i < retry; ++i) {
            ServiceAddress serviceAddress = finder.selectService(service);
            if(serviceTransport.containsKey(serviceAddress)) {
                return serviceTransport.get(serviceAddress);
            }
            Transport transport = new RpcTransport();
            transport.setTimeout(transportWriteTimeout);
            transport.addCloseListener(t -> serviceTransport.remove(serviceAddress, transport));
            try {
                transport.connect(serviceAddress.ip, (short) serviceAddress.port);
            } catch (RpcConnectionTimeoutException ignored) {
                continue;
            }
            serviceTransport.put(serviceAddress, transport);
            return transport;
        }

        throw new RpcConnectionTimeoutException("The RPC connection still timed out after %d attempts.".formatted(retry));
    }

    @SuppressWarnings("unchecked")
    public <T> T newService(Class<T> metaClass) {

        RpcService annotation = metaClass.getAnnotation(RpcService.class);
        if( annotation == null ) {
            throw new RpcException("Rpc Service class must has a annotation Rpc Service");
        }
        Transport transport = getTransport(annotation.name());
        return (T) Proxy.newProxyInstance(metaClass.getClassLoader(), new Class[]{metaClass}, new RpcCaller(annotation.name(), transport));
    }

    @SuppressWarnings("unchecked")
    public static <T> T newService(Class<T> metaClass, Transport transport) {

        RpcService annotation = metaClass.getAnnotation(RpcService.class);
        if( annotation == null ) {
            throw new RpcException("Rpc Service class must has a annotation Rpc Service");
        }
        return (T) Proxy.newProxyInstance(metaClass.getClassLoader(), new Class[]{metaClass}, new RpcCaller(annotation.name(), transport));
    }

    public static class RpcCaller implements InvocationHandler {

        private final Transport transport;
        private final String serviceName;
        public RpcCaller(String serviceName, Transport transport) {
            this.serviceName = serviceName;
            this.transport = transport;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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

            try {
                response = transport.call(serviceName, rpc.name(), message);
            } catch (RpcCallWriteFailedException e) {
                // TODO: 写失败重试
                return null;
            } catch (RpcCallTimeoutException e) {
                // TODO: 超时重试
                return null;
            } catch (RpcConnectionClosedException e) {
                // TODO: 连接关闭重试
                return null;
            }

            Class<?> retType = method.getReturnType();
            if(retType == void.class || retType == Void.class) {
                return null;                // 没有返回值
            }

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

}