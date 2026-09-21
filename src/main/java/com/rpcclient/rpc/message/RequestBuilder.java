package com.rpcclient.rpc.message;

import com.google.protobuf.GeneratedMessageV3;

import java.nio.charset.StandardCharsets;

/*
 * 0x0a 0x0b    // 2
 * type 1 字节  1 = REQUEST 2 = RESPONSE 3 = ERROR server端只会接收到 1 // 3
 * request_id   8 字节 大端 无符号，不能为 0 // 11
 * name_len 1 字节 // 12
 * function name for name_len // 12 + name_len
 * parma_len  4 字节 大端  // 16 + name_len
 * serialize parma for parma_len // 16 + name_len + parma_len
 * 0x0b 0x0c // 18 + name_len + parma_len
 */

public class RequestBuilder {
    Request request;

    public RequestBuilder() {
        request = new Request();
        request.type = 1;
        request.requestId = 0;
        request.functionNameLen = 0;
        request.serviceNameLen = 0;
        request.paramLen = 0;
        request.errorCode = 0;
    }

    public RequestBuilder serviceName(String name) {

        if(name.length() > 255) {
            throw new RuntimeException("The com.rpcclient.service name is too long");
        }

        request.serviceNameLen = name.length();
        request.serviceName = name;
        return this;
    }

    public RequestBuilder functionName(String name) {

        if(name.length() > 255) {
            throw new RuntimeException("The function name is too long");
        }

        request.functionNameLen = name.length();
        request.functionName = name;

        return this;
    }

    public RequestBuilder requestId(Long id) {
        request.requestId = id;
        return this;
    }

    public RequestBuilder param(GeneratedMessageV3 message) {
        if(message != null) {
            request.param = message.toByteArray();
            request.paramLen = message.getSerializedSize();
        }
        return  this;
    }

    public byte[] build() {

        if (request.type != 1) {
            throw new IllegalArgumentException("client request type must be 1");
        }

        if (request.requestId == 0L) {
            throw new IllegalArgumentException("requestId cannot be 0");
        }

        byte[] serviceNameBytes = request.serviceName == null
                ? new byte[0]
                : request.serviceName.getBytes(StandardCharsets.UTF_8);

        if (serviceNameBytes.length > 255) {
            throw new IllegalArgumentException("function name is too long");
        }

        byte[] funcNameBytes = request.functionName == null
                ? new byte[0]
                : request.functionName.getBytes(StandardCharsets.UTF_8);

        if (funcNameBytes.length > 255) {
            throw new IllegalArgumentException("function name is too long");
        }

        byte[] paramBytes = request.param == null ? new byte[0] : request.param;
        request.paramLen = paramBytes.length;

        int totalLen = 20 + request.serviceNameLen + request.functionNameLen + request.paramLen;
        byte[] ret = new byte[totalLen];

        int idx = 0;

        // 0x0a 0x0b
        ret[idx++] = 0x0a;
        ret[idx++] = 0x0b;

        // type 1 字节
        ret[idx++] = (byte) request.type;

        // request_id 8 字节大端
        long id = request.requestId;
        ret[idx++] = (byte) (id >>> 56);
        ret[idx++] = (byte) (id >>> 48);
        ret[idx++] = (byte) (id >>> 40);
        ret[idx++] = (byte) (id >>> 32);
        ret[idx++] = (byte) (id >>> 24);
        ret[idx++] = (byte) (id >>> 16);
        ret[idx++] = (byte) (id >>> 8);
        ret[idx++] = (byte) id;

        // error code 1 字节
        ret[idx++] = (byte) request.errorCode;

        // service_name_len 1 字节
        ret[idx++] = (byte) request.serviceNameLen;

        // com.rpcclient.service name
        System.arraycopy(serviceNameBytes, 0, ret, idx, request.serviceNameLen);
        idx += request.serviceNameLen;

        // func_name_len 1 字节
        ret[idx++] = (byte) request.functionNameLen;

        // function name
        System.arraycopy(funcNameBytes, 0, ret, idx, request.functionNameLen);
        idx += request.functionNameLen;

        // param_len 4 字节大端
        ret[idx++] = (byte) (request.paramLen >>> 24);
        ret[idx++] = (byte) (request.paramLen >>> 16);
        ret[idx++] = (byte) (request.paramLen >>> 8);
        ret[idx++] = (byte) request.paramLen;

        // param
        System.arraycopy(paramBytes, 0, ret, idx, request.paramLen);
        idx += request.paramLen;

        // 0x0b 0x0c
        ret[idx++] = 0x0b;
        ret[idx] = 0x0c;

        return ret;
    }
}
