package com.rpcclient.rpc.interceptor;

import com.rpcclient.rpc.RpcConfig;
import com.rpcclient.rpc.RpcContext;
import com.rpcclient.rpc.exception.RpcCallTimeoutException;
import com.rpcclient.rpc.exception.RpcCallWriteFailedException;
import com.rpcclient.rpc.exception.RpcTooManyCallException;
import jakarta.annotation.PostConstruct;
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
            } catch (RpcTooManyCallException e) {
                lastException = e;
                context.setTooManyCallCount(context.getTooManyCallCount() + 1);
                tryBackOff(i);
            }
        }
        assert lastException != null;
        throw lastException;
    }

    private void tryBackOff(int count) {
        double sleepTime = config.limitSleepStart; // 单位 ms，用 double
        for (int i = 0; i < count; ++i) {
            sleepTime *= 2;
            if (sleepTime >= config.limitSleepMax) {
                sleepTime = config.limitSleepMax;
                break;
            }
        }
        long nanos = (long) (sleepTime * 1_000_000L); // ms → ns
        try {
            Thread.sleep(nanos / 1_000_000, (int)(nanos % 1_000_000));
        } catch (InterruptedException ignore) {

        }
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
