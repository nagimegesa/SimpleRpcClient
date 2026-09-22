package com.rpcclient.rpc.exception;

public class RpcCallTimeoutException extends RpcException {
    public RpcCallTimeoutException(String message) {
        super(message);
    }
    public RpcCallTimeoutException(String message, Throwable e) {
        super(message, e);
    }
}
