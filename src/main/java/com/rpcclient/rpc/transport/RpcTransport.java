package com.rpcclient.rpc.transport;

import com.google.protobuf.GeneratedMessageV3;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import com.rpcclient.rpc.exception.RpcErrorCode;
import com.rpcclient.rpc.exception.RpcException;
import com.rpcclient.rpc.handler.MessageDecoder;
import com.rpcclient.rpc.handler.MessageEncoder;
import com.rpcclient.rpc.handler.ResponseHandler;
import com.rpcclient.rpc.message.RequestBuilder;
import com.rpcclient.rpc.message.Response;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class RpcTransport implements Transport {
    Bootstrap bootstrap;

    private static final AtomicLong requestId = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CompletableFuture<Response>> pending = new ConcurrentHashMap<>();
    private ChannelFuture channelFuture;

    public RpcTransport() {
        EventLoopGroup group = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)          // 客户端用 NioSocketChannel
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new MessageDecoder());
                        ch.pipeline().addLast(new ResponseHandler(pending));
                        ch.pipeline().addLast(new MessageEncoder());
                    }
                });
    }

    public void connect(String ip, short port) {
        try {
            channelFuture = bootstrap.connect(ip, port).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void close() {
        if (channelFuture != null) {
            channelFuture.channel().close();
        }

        bootstrap.group().shutdownGracefully();
    }


    public SimpleResponse call(String serviceName, String functionName, GeneratedMessageV3 message) {
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
                    future.completeExceptionally(new RpcException(
                            "com.rpcclient.rpc write failed, requestId=" + id + ", function=" + functionName,
                            f.cause()
                    ));
                }
            });

            Response response = future.get(3, TimeUnit.SECONDS);

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
            throw new RpcException(
                    "com.rpcclient.rpc call timeout, requestId=" + id + ", function=" + functionName,
                    e
            );

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            // ResponseHandler / writeListener 里 completeExceptionally 的 RpcException
            if (cause instanceof RpcException) {
                throw (RpcException) cause;
            }

            throw new RpcException(
                    "com.rpcclient.rpc call failed, requestId=" + id + ", function=" + functionName,
                    cause
            );

        } finally {
            pending.remove(id);
        }
    }
}
