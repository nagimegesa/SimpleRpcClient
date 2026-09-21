package com.rpcclient.rpc.exception;

public enum RpcErrorCode {
    BAD_REQUEST(1),
    BAD_INTERNAL(3),
    UNKNOWN_FUNCTION(100),
    UNKNOWN_PARAM(101),
    UNKNOWN_RESPONSE(102),
    UNKNOWN(-1);

    private final int code;

    RpcErrorCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static RpcErrorCode fromCode(int code) {
        for (RpcErrorCode c : values()) {
            if (c.code == code) {
                return c;
            }
        }
        return UNKNOWN;
    }
}