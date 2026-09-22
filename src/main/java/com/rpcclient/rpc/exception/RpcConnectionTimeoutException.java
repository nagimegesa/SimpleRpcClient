package com.rpcclient.rpc.exception;

public class RpcConnectionTimeoutException extends RpcException {
    public RpcConnectionTimeoutException(String message) {
        super(message);
    }
    public RpcConnectionTimeoutException(String message, Throwable e) {
        super(message, e);
    }
}
