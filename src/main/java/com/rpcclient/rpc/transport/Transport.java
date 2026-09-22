package com.rpcclient.rpc.transport;

import com.google.protobuf.GeneratedMessageV3;

import java.io.Closeable;

public interface Transport extends Closeable {
    public interface CloseCallback {
        public void onClose(Transport transport);
    }

    public class SimpleResponse {
        public long id;
        public byte[] response;
    }

    public void setTimeout(int timeout);
    public void connect(String ip, short port);
    public void close();
    public void addCloseListener(CloseCallback callback);
    public boolean isActive();
    public SimpleResponse call(String serviceName, String functionName, GeneratedMessageV3 message);

}
