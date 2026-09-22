# Simple RPC Client

基于 Spring Boot 3 与 Netty 的 RPC 客户端，用于调用 [SimpleRedisClient](https://github.com/nagimegesa/SimpleRedisClient) 中 C++ 实现的 RPC 服务端。

---

## 特性

- **注解式服务代理**：接口标注 `@RpcService`，方法标注 `@RpcMethod`，即可获得可调用的远程服务代理。方法调用、请求编码、响应关联、结果反序列化由框架完成，新增服务只需一个接口文件。
- **Spring Boot 集成**：`RpcClient`、`ServiceRouter` 与 `ServiceFinder` 都是容器内的 Bean，调用方通过 `@Resource` / `@Autowired` 注入即可使用；Nacos 地址、超时、重试等配置由 `application.properties` 提供并被 `@Value` 注入。
- **Nacos 服务发现与路由**：`ServiceRouter` 负责选路与连接缓存，按健康实例建立连接，并通过**建连失败黑名单（带 TTL）**跳过刚失效的实例，应对 Nacos 只保证最终一致的问题。
- **自定义二进制协议**：与 C++ 服务端共用同一套协议，定长包头加变长字段，Protobuf 负责序列化，支持粘包与分包处理。
- **单连接并发请求**：基于 Netty 异步传输，同一服务地址复用一条 TCP 连接，通过请求 ID 关联并发请求与响应；`pending` 表按连接隔离，某条连接断开只会让该连接上的在途请求失败。
- **连接失效自愈**：调用前检查连接健康状态，不可用即关闭并从缓存剔除，下一次调用自动重建；连接断开时回调清理缓存。
- **分层异常与重试**：失败按类型区分（建连失败、写失败、调用超时、连接断开），连接类失败自动换连接重试，服务端错误码原样透传。

---

## 环境要求

| 项目 | 要求 |
| --- | --- |
| JDK | 17 或更高 |
| 构建工具 | Maven 3.6.3 或更高 |
| 框架 | Spring Boot 3.3.4 |
| RPC 服务端 | SimpleRedisClient 中的 `rpc_server`，默认监听 `127.0.0.1:8891` |
| 注册中心 | Nacos，默认地址 `127.0.0.1:8848` |

---

## 配置

配置位于 `src/main/resources/application.properties`：

```properties
# Nacos 注册中心
rpc.service.nacos.address=127.0.0.1:8848
rpc.service.nacos.user=nacos
rpc.service.nacos.password=nacos

# 调用与路由
rpc.service.timeout=3000
rpc.service.call.retry=3
rpc.service.router.retry=3
rpc.service.router.blacklist-ttl=30000
```

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `rpc.service.nacos.address` | — | Nacos 服务端地址，格式为 `host:port` |
| `rpc.service.nacos.user` | — | Nacos 用户名 |
| `rpc.service.nacos.password` | — | Nacos 密码 |
| `rpc.service.timeout` | 3000 | 单次调用的读响应超时，单位毫秒 |
| `rpc.service.call.retry` | 3 | 单次请求的重试次数（连接类失败时换连接重试） |
| `rpc.service.router.retry` | 3 | 路由层寻找可用连接的最大尝试次数 |
| `rpc.service.router.blacklist-ttl` | 30000 | 建连失败的实例被拉黑的时长，单位毫秒 |

前三项均为必填，`ServiceFinder` 在 `@PostConstruct` 阶段据此创建 Nacos 命名服务，缺失或错误会导致容器启动失败。

---

## 快速开始

### 编译

```bash
mvn clean package
```

`spring-boot-maven-plugin` 会打成可执行 jar，产物为 `target/RpcClient-1.0-SNAPSHOT.jar`。

> 单元测试 `ApplicationTest` 是需要真实 Nacos 与 RPC 服务端参与的压测，打包时可跳过：
>
> ```bash
> mvn clean package -DskipTests
> ```

### 启动依赖

```bash
# Nacos
sh startup.sh -m standalone

# C++ 服务端，在 SimpleRedisClient 项目根目录执行
cmake -S . -B cmake-build-release -DCMAKE_BUILD_TYPE=Release
cmake --build cmake-build-release -j$(nproc)
./cmake-build-release/rpc_server
```

服务端启动后会以 `HelloService` 为名注册到 Nacos。

### 运行

```bash
# 运行打包产物
java -jar target/RpcClient-1.0-SNAPSHOT.jar

# 或者直接用 Maven 插件启动
mvn spring-boot:run
```

入口类是 `com.rpcclient.App`，由 `@SpringBootApplication` 完成组件扫描与自动装配。

---

## 用法

### 定义服务接口

服务名与方法名必须与服务端注册时使用的字符串完全一致：

```java
package com.rpcclient.rcpservice;

import com.rpcclient.protoc.hello.HelloWorldRequest;
import com.rpcclient.protoc.hello.HelloWorldResponse;
import com.rpcclient.rpc.annotation.RpcMethod;
import com.rpcclient.rpc.annotation.RpcService;

@RpcService(name = "HelloService")
public interface HelloService {
    @RpcMethod(name = "hello")
    HelloWorldResponse hello(HelloWorldRequest request);
}
```

接口必须标注 `@RpcService`，方法必须标注 `@RpcMethod`。方法最多接收一个 Protobuf 参数，返回值可以是 Protobuf 生成类，也可以省略。接口本身不需要注册成 Spring Bean，`RpcClient` 会为其生成动态代理。

### 通过 Spring 注入并发起调用

`RpcClient` 是 `@Component`，直接注入使用即可，无需手工 `new`：

```java
import com.rpcclient.protoc.hello.HelloWorldRequest;
import com.rpcclient.protoc.hello.HelloWorldResponse;
import com.rpcclient.rpc.RpcClient;
import com.rpcclient.rcpservice.HelloService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class HelloCaller {

    @Resource
    private RpcClient rpcClient;

    public String hello() {
        HelloService service = rpcClient.newService(HelloService.class);
        HelloWorldResponse res = service.hello(
                HelloWorldRequest.newBuilder().setMsg("hello").build());
        return res.getRes();
    }
}
```

`newService` 返回动态代理对象，调用失败时抛出携带错误码与请求 ID 的 `RpcException`。服务地址由 `ServiceRouter` 从 Nacos 选取健康实例，同一地址复用同一条 Netty 连接。

### 错误处理

失败按类型区分，`RpcException` 的子类对应不同阶段：

| 异常 | 含义 | 是否重试 |
| --- | --- | --- |
| `RpcConnectionTimeoutException` | 建连失败或超时 | 换实例重试 |
| `RpcCallWriteFailedException` | 请求写入失败（确定未送达） | 重试 |
| `RpcCallTimeoutException` | 等待响应超时（结果未知） | 重试 |
| `RpcConnectionClosedException` | 连接已断开 | 换连接重试 |
| `RpcException`（带服务端 `errorCode`） | 服务端业务错误 | 不重试 |

重试次数由 `rpc.service.call.retry` 控制。**注意 `RpcCallTimeoutException` 的语义是「结果未知」**——服务端可能已经执行，只是响应没回来，对非幂等接口需谨慎依赖重试。

### 跳过服务发现直连

不经过 Nacos 时，使用静态重载直接绑定一个传输层实例：

```java
import com.rpcclient.rpc.RpcClient;
import com.rpcclient.rpc.transport.RpcTransport;
import com.rpcclient.rpc.transport.Transport;

Transport transport = new RpcTransport();
transport.connect("127.0.0.1", (short) 8891);

// 第三个参数为请求重试次数
HelloService service = RpcClient.newService(HelloService.class, transport, 3);
```

该方式不依赖 Nacos 配置，可脱离 Spring 容器单独使用。

---

## 目录结构

```text
src/main/java/com/rpcclient/
  App.java                      Spring Boot 启动类
  protoc/hello/                 hello.proto 生成的 Protobuf 类
  service/HelloService.java     示例服务接口
  rpc/
      RpcClient.java            Spring Bean，动态代理与请求重试
      ServiceAddress.java       服务地址
      router/
          ServiceRouter.java    Spring Bean，选路、连接缓存与黑名单
          ServiceFinder.java    Spring Bean，Nacos 服务发现
      annotation/               @RpcService 与 @RpcMethod
      exception/                RpcException 及分类子类、RpcErrorCode
      handler/                  解码器、编码器与响应处理器
      message/                  请求与响应模型，以及协议编码器
      transport/                传输层接口与 Netty 实现
src/main/resources/
  application.properties        Nacos 地址、超时与重试配置
src/test/java/com/rpcclient/
  ApplicationTest.java          @SpringBootTest 压测
```

---

## 压测结果

压测由 `ApplicationTest` 提供，同样基于 Spring 上下文获取 `RpcClient`，并发线程数、压测秒数、预热秒数在 `loadTest()` 中调整：

```java
@SpringBootTest
public class ApplicationTest {
    @Resource
    RpcClient rpcClient;

    @Test
    void loadTest() throws Exception {
        int threads     = 32;
        int durationSec = 30;
        int warmupSec   = 5;
        // ...
    }
}
```

运行方式：

```bash
mvn test -Dtest=ApplicationTest
```

**测试环境**：WSL2 Ubuntu 20.04 运行 GCC 14 编译的 `rpc_server`，监听 `127.0.0.1:8891`，Nacos 运行在 `127.0.0.1:8848`，客户端使用 JDK 17。

| 指标 | 结果 |
| --- | --- |
| 并发线程 | 32 |
| 压测时长 | 30 s |
| 总请求数 | 2,634,287 |
| 成功 / 失败 | 2,634,287 / 0 |
| 吞吐量 | 87,800 QPS |
| 平均延迟 | 0.364 ms |
| p50 / p90 | 0.332 ms / 0.547 ms |
| p99 / p99.9 | 0.861 ms / 1.809 ms |
---

## 协议附录
### 请求

| 偏移 | 长度 | 字段 | 说明 |
| --- | --- | --- | --- |
| 0 | 2 | `0x0a 0x0b` | 起始魔数 |
| 2 | 1 | `type` | 固定为 1 |
| 3 | 8 | `request_id` | 无符号 64 位，不能为 0 |
| 11 | 1 | `error_code` | 客户端固定为 0 |
| 12 | 1 | `service_len` | 服务名长度，不超过 255 |
| 13 | n | `service_name` | UTF-8 编码 |
| 13+n | 1 | `func_len` | 函数名长度，不超过 255 |
| 14+n | m | `func_name` | UTF-8 编码 |
| 14+n+m | 4 | `param_len` | 请求体字节数 |
| 18+n+m | p | `param` | Protobuf 序列化后的请求体 |
| 18+n+m+p | 2 | `0x0b 0x0c` | 结束魔数 |

### 响应

| 偏移 | 长度 | 字段 | 说明 |
| --- | --- | --- | --- |
| 0 | 2 | `0x0a 0x0b` | 起始魔数 |
| 2 | 1 | `type` | 2 成功，3 错误 |
| 3 | 8 | `request_id` | 回显请求 ID |
| 11 | 1 | `error_code` | 成功为 0 |
| 12 | 4 | `param_len` | 响应体字节数，错误响应为 0 |
| 16 | p | `param` | Protobuf 序列化后的响应体 |
| 16+p | 2 | `0x0b 0x0c` | 结束魔数 |

### 错误码

| 错误码 | 枚举 | 含义 |
| --- | --- | --- |
| 1 | `BAD_REQUEST` | 报文解析失败 |
| 3 | `BAD_INTERNAL` | 服务端业务处理异常 |
| 100 | `UNKNOWN_FUNCTION` | 服务名或函数名未注册 |
| 101 | `UNKNOWN_PARAM` | 请求体反序列化失败 |
| 102 | `UNKNOWN_RESPONSE` | 响应体序列化或解析失败 |
| -1 | `UNKNOWN` | 客户端侧建连、读写、超时与连接关闭 |
