package com.rpcclient.rpc.exception;

public class RpcTooManyCallException extends RpcException {
    public RpcTooManyCallException(String message) {
        super(message);
    }
    public RpcTooManyCallException(String message, Throwable e) {
        super(message, e);
    }
}
