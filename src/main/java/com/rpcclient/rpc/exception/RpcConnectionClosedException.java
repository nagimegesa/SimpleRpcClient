package com.rpcclient.rpc.exception;

public class RpcConnectionClosedException extends RpcException {
    public RpcConnectionClosedException(String message) {
        super(message);
    }
}
