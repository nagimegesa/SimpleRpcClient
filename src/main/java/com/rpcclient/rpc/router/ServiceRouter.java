package com.rpcclient.rpc.router;

import com.rpcclient.rpc.ServiceAddress;
import com.rpcclient.rpc.exception.RpcConnectionTimeoutException;
import com.rpcclient.rpc.transport.RpcTransport;
import com.rpcclient.rpc.transport.Transport;
import jakarta.annotation.Resource;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ServiceRouter {

    @Value("${rpc.service.timeout:3000}")
    private int transportWriteTimeout;

    @Value("${rpc.service.router.retry:3}")
    private int retry;

    @Value("${rpc.service.router.blacklist-ttl:30000}")
    private long blacklistTtlMs;

    private final ConcurrentHashMap<ServiceAddress, Transport> serviceTransport = new ConcurrentHashMap<>();
    // 黑名单
    private final ConcurrentHashMap<ServiceAddress, Long> blacklist = new ConcurrentHashMap<>();

    @Resource
    ServiceFinder finder;

    public Transport getTransport(String service) {
        for(int i = 0; i < retry; ++i) {
            ServiceAddress serviceAddress = finder.selectService(service);
            Transport transport = serviceTransport.get(serviceAddress);
            if(transport != null) { // 缓存命中
                if (!transport.isActive()) {
                    transport.close();
                    continue;
                }

                return transport;
            }

            // 没有命中缓存
            if(isBlacklisted(serviceAddress)) { // 检查名单
                continue;
            }

            transport = getTransport(serviceAddress);
            if(transport != null) {
                Transport retTransport = serviceTransport.putIfAbsent(serviceAddress, transport);
                if(retTransport != null && retTransport.isActive()) {  // 二次校验
                    transport.close();
                    return retTransport;
                } else if(retTransport != null) {
                    retTransport.close();
                    serviceTransport.put(serviceAddress, transport);
                }
                return transport;
            }
        }

        throw new RpcConnectionTimeoutException("Can't find a rpc connection after %d counts".formatted(retry));
    }

    private @Nullable Transport getTransport(ServiceAddress serviceAddress) {
        Transport transport = new RpcTransport();
        transport.setTimeout(transportWriteTimeout);
        transport.addCloseListener(t -> serviceTransport.remove(serviceAddress, transport));
        try {
            transport.connect(serviceAddress.ip, (short) serviceAddress.port);
        } catch (RpcConnectionTimeoutException e) {
            transport.close();
            addToBlacklist(serviceAddress); // 拉黑
            return null;
        }
        return transport;
    }

    private boolean isBlacklisted(ServiceAddress addr) {
        Long expire = blacklist.get(addr);
        if (expire == null) return false;
        if (expire <= System.currentTimeMillis()) {
            blacklist.remove(addr, expire);
            return false;
        }
        return true;
    }

    private void addToBlacklist(ServiceAddress addr) {
        blacklist.put(addr, System.currentTimeMillis() + blacklistTtlMs);
    }
}
