package com.rpcclient.rpc.message;

public class Response {
    public int type;
    public long requestId;      // 8 字节
    public int errorCode;
    public int paramLen;        // 4 字节
    public byte[] param;

    @Override
    public String toString() {
        return "Response{" +
                "type=" + type +
                ", requestId=" + requestId +
                ", errorCode=" + errorCode +
                ", paramLen=" + paramLen +
                ", param=" + (param == null ? null : param.length) +
                '}';
    }
}