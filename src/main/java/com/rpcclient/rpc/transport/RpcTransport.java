package com.rpcclient.rpc.transport;

import com.google.protobuf.GeneratedMessageV3;
import com.rpcclient.rpc.exception.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import com.rpcclient.rpc.handler.MessageDecoder;
import com.rpcclient.rpc.handler.MessageEncoder;
import com.rpcclient.rpc.handler.ResponseHandler;
import com.rpcclient.rpc.message.RequestBuilder;
import com.rpcclient.rpc.message.Response;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.ConnectException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class RpcTransport implements Transport {

    private static final AtomicLong requestId = new AtomicLong(1);
    private static final EventLoopGroup group = new NioEventLoopGroup();
    private static final Bootstrap bootstrap;
    private static final ConcurrentHashMap<Long, CompletableFuture<Response>> pending = new ConcurrentHashMap<>();

    static {
        bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)          // 客户端用 NioSocketChannel
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new MessageDecoder());
                        ch.pipeline().addLast(new ResponseHandler(pending));
                        ch.pipeline().addLast(new MessageEncoder());
                    }
                });
    }

    CloseCallback closeCallback = null;
    private ChannelFuture channelFuture;

    @Setter
    private int timeout = 3000;

    public void connect(String ip, short port) {
        try {
            channelFuture = bootstrap.connect(ip, port).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RpcConnectionTimeoutException("Rpc connection time out");
        } catch (Exception e) {
            if (e instanceof ConnectException) { // 这里连接超时会抛出这个异常
                throw new RpcConnectionTimeoutException("Rpc connection timeout");
            }
            throw e;
        }
    }

    public void close() {
        if (channelFuture != null) {
            log.info("close transport");
            if(closeCallback != null) {
                closeCallback.OnClose(this);
            }
            channelFuture.channel().close();
        }
    }

    public boolean isActivate() {
        return channelFuture.channel().isActive();
    }

    public void addCloseListener(CloseCallback callback) {
        this.closeCallback = callback;
    }

    public SimpleResponse call(String serviceName, String functionName, GeneratedMessageV3 message) {

        if(!isActivate()) {
            close();
            throw new RpcConnectionClosedException("Rpc connection has been closed");
        }

        long id = requestId.getAndIncrement();

        byte[] request = new RequestBuilder()
                .requestId(id)
                .param(message)
                .serviceName(serviceName)
                .functionName(functionName)
                .build();

        CompletableFuture<Response> future = new CompletableFuture<>();
        pending.put(id, future);

        try {

            SimpleResponse retResponse = new SimpleResponse();
            retResponse.id = id;

            channelFuture.channel().writeAndFlush(request).addListener(f -> {
                if (!f.isSuccess()) {
                    future.completeExceptionally(new RpcCallWriteFailedException(
                            "com.rpcclient.rpc write failed, requestId=" + id + ", function=" + functionName
                    ));
                }
            });

            Response response = future.get(timeout, TimeUnit.MICROSECONDS);

            // ResponseHandler 正常只会在 type == 2 时 complete
            // 这里只是防御性判断
            if (response.type != 2) {
                RpcErrorCode code = RpcErrorCode.fromCode(response.errorCode);
                throw new RpcException(
                        response.errorCode,
                        response.requestId,
                        "RPC error: " + code
                );
            }

            retResponse.response = response.param;
            return retResponse;

        } catch (InterruptedException e) {
            // 恢复中断标志，避免上层线程池丢失中断信号
            Thread.currentThread().interrupt();
            throw new RpcException(
                    "com.rpcclient.rpc call interrupted, requestId=" + id + ", function=" + functionName,
                    e
            );
        } catch (TimeoutException e) {
            throw new RpcCallTimeoutException(
                    "com.rpcclient.rpc call timeout, requestId=" + id + ", function=" + functionName
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            // ResponseHandler / writeListener 里 completeExceptionally 的 RpcException
            if (cause instanceof RpcCallWriteFailedException) { // 写错误可以尝试重拾
                throw (RpcCallWriteFailedException) cause;
            } else if(cause instanceof RpcException) {
                throw (RpcException) cause; // 这个分支只可能是服务端执行后出现了错误
            }

            throw new RpcException( // 兜底的异常
                    "com.rpcclient.rpc call failed, requestId=" + id + ", function=" + functionName,
                    cause
            );

        } finally {
            pending.remove(id);
        }
    }
}
