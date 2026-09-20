package rpc;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import rpc.annotation.RpcMethod;
import rpc.annotation.RpcService;
import rpc.exception.RpcErrorCode;
import rpc.exception.RpcException;
import rpc.transport.RpcTransport;
import rpc.transport.Transport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;

public class RpcClient {
    public final ServiceFinder finder;

    private static final ConcurrentHashMap<ServiceAddress, Transport> serviceTransport = new ConcurrentHashMap<>();

    public RpcClient(ServiceFinder finder) {
        this.finder = finder;
    }

    private Transport getTransport(String service) {
        ServiceAddress serviceAddress = finder.selectService(service);
        if(serviceTransport.containsKey(serviceAddress)) {
            return serviceTransport.get(serviceAddress);
        }

        Transport transport = new RpcTransport();
        transport.connect(serviceAddress.ip, (short) serviceAddress.port);
        serviceTransport.put(serviceAddress, transport);
        return transport;
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
                return method.invoke(proxy, args);
            }

            if (args != null && args.length != 1) {
                throw new RpcException("RPC method takes exactly one argument");
            }

            GeneratedMessageV3 message = null;
            if(args != null) {
                message = (GeneratedMessageV3) args[0];
            }

            Transport.SimpleResponse response = transport.call(serviceName, rpc.name(), message);
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