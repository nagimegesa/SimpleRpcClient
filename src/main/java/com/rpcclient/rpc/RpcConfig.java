package com.rpcclient.rpc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RpcConfig {
    @Value("${rpc.service.nacos.address}")
    public String NACOS_SERVER_ADDRESS;

    @Value("${rpc.service.nacos.user}")
    public String USER_NAME;

    @Value("${rpc.service.nacos.password}")
    public String PASSWORD;

    @Value("${rpc.service.call.routerRetry:3}")
    public int rpcCallRetry;

    @Value("${rpc.service.call-timeout-ms:3000}")
    public int rpcCallTimeout;

    @Value("${rpc.service.call.retry:3}")
    public int rpcRouterRetry;

    @Value("${rpc.service.router.blacklist-ttl-ms:30000}")
    public long blacklistTtlMs;

    @Value("${rpc.service.call-max:100000}")
    public long callMax;

    @Value("${rpc.service.call-limit-sleep-start-ms:0.1}")
    public double limitSleepStart;

    @Value("${rpc.service.call-limit-sleep-max-ms:5}")
    public double limitSleepMax;
}
