package com.rpcclient.rpc.exception;

import lombok.Getter;

@Getter
public class RpcException extends RuntimeException {
    private final int errorCode;
    private final long requestId;

    public RpcException(String message) {
        this(-1, -1, message, null);
    }

    public RpcException(String message, Throwable cause) {
        this(-1, -1, message, cause);
    }

    public RpcException(int errorCode, long requestId, String message) {
        this(errorCode, requestId, message, null);
    }

    public RpcException(int errorCode, long requestId, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.requestId = requestId;
    }
}