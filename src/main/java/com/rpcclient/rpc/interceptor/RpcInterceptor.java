package com.rpcclient.rpc.interceptor;

import com.rpcclient.rpc.RpcContext;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Component
public interface RpcInterceptor extends Ordered {
    public Object process(RpcContext context, RpcInterceptorChain chain);
}