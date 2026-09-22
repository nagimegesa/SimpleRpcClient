package com.rpcclient.rpc;

import com.rpcclient.rpc.exception.*;
import com.rpcclient.rpc.interceptor.RpcInterceptor;
import com.rpcclient.rpc.router.ServiceFinder;
import com.rpcclient.rpc.router.ServiceRouter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import com.rpcclient.rpc.annotation.RpcService;
import com.rpcclient.rpc.transport.Transport;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Supplier;

@Component
public class RpcClient {

    @Resource
    private ServiceRouter router;

    @Resource
    List<RpcInterceptor> interceptors;


    @SuppressWarnings("unchecked")
    public <T> T newService(Class<T> metaClass) {

        RpcService annotation = metaClass.getAnnotation(RpcService.class);
        if( annotation == null ) {
            throw new RpcException("Rpc Service class must has a annotation Rpc Service");
        }

        Supplier<Transport> getTransport = () -> router.getTransport(annotation.name());

        RpcContext context = new RpcContext();
        context.setTransportGetter(getTransport);
        context.setServiceName(annotation.name());

        // return (T) Proxy.newProxyInstance(metaClass.getClassLoader(), new Class[]{metaClass}, new RpcCaller(annotation.name(), getTransport, config.rpcCallRetry));
         return (T) Proxy.newProxyInstance(metaClass.getClassLoader(), new Class[]{metaClass}, new RpcCaller(context, interceptors));
    }

    @SuppressWarnings("unchecked")
    public static <T> T newService(Class<T> metaClass, Transport transport, int retry) {

        RpcService annotation = metaClass.getAnnotation(RpcService.class);
        if( annotation == null ) {
            throw new RpcException("Rpc Service class must has a annotation Rpc Service");
        }

        return (T) Proxy.newProxyInstance(metaClass.getClassLoader(), new Class[]{metaClass}, new RpcCallerDirect(annotation.name(), transport, retry));
    }
}