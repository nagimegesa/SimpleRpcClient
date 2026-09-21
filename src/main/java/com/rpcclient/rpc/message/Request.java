package com.rpcclient.rpc.message;

public class Request {
    public int type;
    public long requestId;      // 8 字节
    public int errorCode;
    public int serviceNameLen;
    public String serviceName;
    public int functionNameLen;
    public String functionName;
    public int paramLen;        // 4 字节
    public byte[] param;
}