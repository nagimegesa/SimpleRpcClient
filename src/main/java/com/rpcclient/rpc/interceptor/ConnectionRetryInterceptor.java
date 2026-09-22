package com.rpcclient.rpc.interceptor;

import com.rpcclient.rpc.RpcConfig;
import com.rpcclient.rpc.RpcContext;
import com.rpcclient.rpc.exception.RpcConnectionClosedException;
import com.rpcclient.rpc.transport.Transport;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class ConnectionRetryInterceptor implements RpcInterceptor {
    @Resource
    RpcConfig config;

    @Override
    public Object process(RpcContext context, RpcInterceptorChain chain) {
        RuntimeException lastException = null;
        for (int i = 0; i < config.rpcRouterRetry; ++i) {
            try {
                Transport transport = context.getTransport();
                if (transport == null || !transport.isActive()) {
                    context.setTransport(context.getNewTransport());
                }
                return chain.process(context);
            } catch (RpcConnectionClosedException e) {
                context.setConnectionClosedRetryCount(context.getConnectionClosedRetryCount() + 1);
                lastException = e;
                context.setTransport(context.getNewTransport());
            } catch (RuntimeException e) {
                lastException = e;
            }
        }
        assert lastException != null;
        throw lastException;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
