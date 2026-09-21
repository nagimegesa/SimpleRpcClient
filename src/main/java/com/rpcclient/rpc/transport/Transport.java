package com.rpcclient.rpc.transport;

import com.google.protobuf.GeneratedMessageV3;

public interface Transport {

    public class SimpleResponse {
        public long id;
        public byte[] response;
    }

    public void connect(String ip, short port);
    public void close();
    public SimpleResponse call(String serviceName, String functionName, GeneratedMessageV3 message);
}
