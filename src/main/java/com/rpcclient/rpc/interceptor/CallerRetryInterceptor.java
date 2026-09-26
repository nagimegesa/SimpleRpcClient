package com.rpcclient.rpc.interceptor;

import com.rpcclient.rpc.RpcConfig;
import com.rpcclient.rpc.RpcContext;
import com.rpcclient.rpc.exception.RpcCallTimeoutException;
import com.rpcclient.rpc.exception.RpcCallWriteFailedException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class CallerRetryInterceptor implements RpcInterceptor {

    @Resource
    RpcConfig config;

    @Override
    public Object process(RpcContext context, RpcInterceptorChain chain) {
        RuntimeException lastException = null;
        for(int i = 0; i < config.rpcCallRetry; ++i) {
            try {
                return chain.process(context);
            } catch (RpcCallWriteFailedException e) {
                lastException = e;
                context.setCallRetryCount(context.getCallRetryCount() + 1);
            } catch (RpcCallTimeoutException e) {
                lastException = e;
                context.setCallTimeoutCount(context.getCallTimeoutCount() + 1);
            }
            // 请求太多不捕获
//            } catch (RpcTooManyCallException e) {
//                lastException = e;
//                context.setTooManyCallCount(context.getTooManyCallCount() + 1);
//            }
        }
        assert lastException != null;
        throw lastException;
    }
    @Override
    public int getOrder() {
        return 1;
    }
}
