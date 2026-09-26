package com.rpcclient.rpc;

import com.google.protobuf.GeneratedMessageV3;
import com.rpcclient.rpc.transport.Transport;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Supplier;

@Getter
@Setter
public class RpcContext {

    String serviceName;
    String methodName;

    GeneratedMessageV3 message = null;
    Transport transport = null;
    int connectionClosedRetryCount = 0;
    int connectionTimeoutRetryCount = 0;
    int callRetryCount = 0;
    int callTimeoutCount = 0;
    int tooManyCallCount = 0;
    Supplier<Transport> transportGetter;

    public Transport getNewTransport() {
        return transportGetter.get();
    }
}
