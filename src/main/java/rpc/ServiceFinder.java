package rpc;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import rpc.exception.RpcException;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ServiceFinder {
    private static final String NACOS_SERVER_ADDRESS = "127.0.0.1:8848";
    private static final String USER_NAME = "nacos";
    private static final String PASSWORD = "nacos";

    private NamingService service = null;

    public ServiceFinder() {
        Properties props = new Properties();
        props.put("username", USER_NAME);
        props.put("password", PASSWORD);
        props.put("serverAddr", NACOS_SERVER_ADDRESS);

        try {
            service = NacosFactory.createNamingService(props);
        } catch (NacosException e) {
            throw new RpcException("can't create name service", e);
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
            throw new RpcException("find service " + name + " failed", e);
        }
    }

    public ServiceAddress selectService(String name) {
        try {
            Instance instance = service.selectOneHealthyInstance(name);
            return new ServiceAddress(instance.getIp(), instance.getPort());
        } catch (NacosException e) {
            throw new RpcException("select service " + name + " failed", e);
        }
    }

    public ListView<String> getServiceOfGroup(String groupName) {
        try {
            return service.getServicesOfServer(1, Integer.MAX_VALUE, groupName);
        } catch (NacosException e) {
            throw new RpcException("get service of group failed", e);
        }
    }
}