package com.rpcclient.rpc.exception;

public class RpcConnectionClosedException extends RpcException {
    public RpcConnectionClosedException(String message) {
        super(message);
    }

    public RpcConnectionClosedException(String message, Throwable e) {
        super(message, e);
    }
}
