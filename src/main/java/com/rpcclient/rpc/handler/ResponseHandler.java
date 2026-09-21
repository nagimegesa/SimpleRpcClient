package com.rpcclient.rpc.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import com.rpcclient.rpc.exception.RpcErrorCode;
import com.rpcclient.rpc.exception.RpcException;
import com.rpcclient.rpc.message.Response;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ResponseHandler extends SimpleChannelInboundHandler<Response> {

    ConcurrentHashMap<Long, CompletableFuture<Response>> pending;

    public ResponseHandler(ConcurrentHashMap<Long, CompletableFuture<Response>> p) {
        pending = p;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Response msg) {
        CompletableFuture<Response> future = pending.get(msg.requestId);
        if (future == null) {
            // 无法关联。若是 requestId=0 的 type=3，通常说明服务端解析早期就失败了。
            return;
        }

        if (msg.type == 2) {
            future.complete(msg);
        } else {
            RpcErrorCode code = RpcErrorCode.fromCode(msg.errorCode);
            future.completeExceptionally(new RpcException(
                    msg.errorCode,
                    msg.requestId,
                    "RPC error: " + code
            ));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        RpcException e = new RpcException("connection closed");
        pending.forEach((id, f) -> f.completeExceptionally(e));
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        pending.forEach((id, f) -> f.completeExceptionally(cause));
        ctx.close();
    }
}