# Simple RPC Client

基于 Java 17 与 Netty 的 RPC 客户端，用于调用 [SimpleRedisClient](https://github.com/nagimegesa/SimpleRedisClient) 中 C++ 实现的 RPC 服务端。

---

## 特性

- **注解式服务代理**：接口标注 `@RpcService`，方法标注 `@RpcMethod`，即可获得可调用的远程服务代理。方法调用、请求编码、响应关联、结果反序列化由框架完成，新增服务只需一个接口文件。
- **Nacos 服务发现**：通过 Nacos 获取服务实例并按权重选择健康实例，支持多实例部署下的负载均衡，同时开放全量实例查询接口以便自定义选路策略。
- **自定义二进制协议**：与 C++ 服务端共用同一套协议，定长包头加变长字段，Protobuf 负责序列化，支持粘包与分包处理。
- **单连接并发请求**：基于 Netty 异步传输，同一服务地址复用一条 TCP 连接，通过请求 ID 关联并发请求与响应，连接断开时在途请求立即失败。
- **错误码透传**：服务端错误以 `RpcException` 抛出，携带错误码与请求 ID，涵盖报文错误、未知函数、参数解析失败等类型。

---

## 环境要求

| 项目 | 要求 |
| --- | --- |
| JDK | 17 或更高 |
| 构建工具 | Maven 3.6 或更高 |
| RPC 服务端 | SimpleRedisClient 中的 `rpc_server`，默认监听 `127.0.0.1:8891` |
| 注册中心 | Nacos，默认地址 `127.0.0.1:8848` |

---

## 快速开始

### 编译

```bash
mvn clean package
```

产物为 `target/classes` 与 `target/RpcClient-1.0-SNAPSHOT.jar`。

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

---

## 用法

### 定义服务接口

服务名与方法名必须与服务端注册时使用的字符串完全一致：

```java
package service;

import protoc.hello.HelloWorldRequest;
import protoc.hello.HelloWorldResponse;
import rpc.annotation.RpcMethod;
import rpc.annotation.RpcService;

@RpcService(name = "HelloService")
public interface HelloService {
    @RpcMethod(name = "hello")
    HelloWorldResponse hello(HelloWorldRequest request);
}
```

接口必须标注 `@RpcService`，方法必须标注 `@RpcMethod`。方法最多接收一个 Protobuf 参数，返回值可以是 Protobuf 生成类，也可以省略。

### 发起调用

```java
RpcClient client = new RpcClient(new ServiceFinder());
HelloService service = client.newService(HelloService.class);

HelloWorldResponse res = service.hello(
        HelloWorldRequest.newBuilder().setMsg("hello").build());

System.out.println(res.getRes());
```

`newService` 返回动态代理对象，调用失败时抛出携带错误码与请求 ID 的 `RpcException`。

### 跳过服务发现直连

```java
Transport transport = new RpcTransport();
transport.connect("127.0.0.1", (short) 8891);

HelloService service = RpcClient.newService(HelloService.class, transport);
```
---

## 目录结构

```text
src/main/java/
  Application.java              多线程压测入口
  protoc/hello/                 hello.proto 生成的 Protobuf 类
  service/HelloService.java     示例服务接口
  rpc/
      RpcClient.java            动态代理与连接缓存
      ServiceFinder.java        Nacos 服务发现
      ServiceAddress.java       服务地址
      annotation/               @RpcService 与 @RpcMethod
      exception/                RpcException 与 RpcErrorCode
      handler/                  解码器、编码器与响应处理器
      message/                  请求与响应模型，以及协议编码器
      transport/                传输层接口与 Netty 实现
src/main/resources/             暂空
src/test/java/                  暂空
```

---

## 压测结果

`Application` 提供定长压测能力，参数依次为并发线程数、压测秒数、预热秒数：

```bash
mvn -q dependency:copy-dependencies -DoutputDirectory=target/dependency
java -cp "target/classes;target/dependency/*" Application 32 30 5
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
