package com.rpcclient.rpc.interceptor;

import com.rpcclient.rpc.RpcContext;

import java.util.List;

public class RpcInterceptorChain {
    private final List<RpcInterceptor> interceptors;
    private final int index;
    private final RpcInterceptorTerminal handler;
    public RpcInterceptorChain(List<RpcInterceptor> interceptors, int index, RpcInterceptorTerminal terminal) {
        this.interceptors = interceptors;
        this.index = index;
        this.handler = terminal;
    }

    public Object process(RpcContext context) {
        if(index == interceptors.size() - 1) {
            return handler.invoke();
        }
        RpcInterceptor rpcInterceptor = interceptors.get(index);
        RpcInterceptorChain rpcInterceptorChain = new RpcInterceptorChain(interceptors, index + 1, handler);
        return rpcInterceptor.process(context, rpcInterceptorChain);
    }
}
