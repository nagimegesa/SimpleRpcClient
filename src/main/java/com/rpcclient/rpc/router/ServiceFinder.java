package com.rpcclient.rpc.router;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.rpcclient.rpc.ServiceAddress;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.rpcclient.rpc.exception.RpcException;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Component
public class ServiceFinder {

    @Value("${rpc.service.nacos.address}")
    private String NACOS_SERVER_ADDRESS;
    @Value("${rpc.service.nacos.user}")
    private String USER_NAME;
    @Value("${rpc.service.nacos.password}")
    private String PASSWORD;

    private NamingService service = null;

    @PostConstruct
    void init() {
        Properties props = new Properties();
        props.put("username", USER_NAME);
        props.put("password", PASSWORD);
        props.put("serverAddr", NACOS_SERVER_ADDRESS);

        try {
            service = NacosFactory.createNamingService(props);
        } catch (NacosException e) {
            throw new RpcException("can't create name com.rpcclient.service", e);
        }
    }

    public List<ServiceAddress> findService(String name)  {
        try {
            List<Instance> hello = service.getAllInstances(name);
            List<ServiceAddress> services = new ArrayList<>();
            hello.forEach((instance)-> {
                services.add(new ServiceAddress(instance.getIp(), instance.getPort()));
            });
            return services;
        } catch (NacosException e) {
            throw new RpcException("find com.rpcclient.service " + name + " failed", e);
        }
    }

    public ServiceAddress selectService(String name) {
        try {
            Instance instance = service.selectOneHealthyInstance(name);
            return new ServiceAddress(instance.getIp(), instance.getPort());
        } catch (NacosException e) {
            throw new RpcException("select com.rpcclient.service " + name + " failed", e);
        }
    }

    public ListView<String> getServiceOfGroup(String groupName) {
        try {
            return service.getServicesOfServer(1, Integer.MAX_VALUE, groupName);
        } catch (NacosException e) {
            throw new RpcException("get com.rpcclient.service of group failed", e);
        }
    }
}