package rpc.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import rpc.message.Response;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class MessageDecoder extends ByteToMessageDecoder {

    static final int START_MAGIC = 0x0a0b;
    static final int END_MAGIC = 0x0b0c;

    private enum ParserState {
        START_MAGIC_NUMBER,
        TYPE,
        REQUEST_ID,
        ERROR_CODE,
        PARMA_LEN,
        PARAM,
        END_MAGIC_NUMBER
    }

    private ParserState state = ParserState.START_MAGIC_NUMBER;
    private Response response = new Response();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (true) {
            switch (state) {
                case START_MAGIC_NUMBER:
                    if (in.readableBytes() < 2) {
                        return;
                    }
                    short magic = in.getShort(in.readerIndex());
                    if (magic != START_MAGIC) {
                        in.skipBytes(1);
                        continue;
                    }
                    in.skipBytes(2);
                    state = ParserState.TYPE;
                    break;

                case TYPE:
                    if (in.readableBytes() < 1) {
                        return;
                    }
                    byte type = in.readByte();
                    // 客户端侧 type 只能是 2 或 3
                    if (type != 2 && type != 3) {
                        reset();
                        continue;
                    }
                    response.type = type;
                    state = ParserState.REQUEST_ID;
                    break;

                case REQUEST_ID:
                    // requestId 8 字节
                    if (in.readableBytes() < 8) {
                        return;
                    }
                    response.requestId = in.readLong();
                    state = ParserState.ERROR_CODE;
                    break;
                case ERROR_CODE:
                    if (in.readableBytes() < 1) {
                        return;
                    }
                    response.errorCode = in.readByte();
                    state = ParserState.PARMA_LEN;
                    break;

                case PARMA_LEN:
                    if (in.readableBytes() < 4) {
                        return;
                    }
                    response.paramLen = in.readInt();
                    if (response.paramLen < 0) {
                        reset();
                        continue;
                    }
                    state = ParserState.PARAM;
                    break;

                case PARAM:
                    if (in.readableBytes() < response.paramLen) {
                        return;
                    }
                    byte[] paramBytes = new byte[response.paramLen];
                    in.readBytes(paramBytes);
                    response.param = paramBytes;
                    state = ParserState.END_MAGIC_NUMBER;
                    break;

                case END_MAGIC_NUMBER:
                    if (in.readableBytes() < 2) {
                        return;
                    }
                    short endMagic = in.readShort();
                    if (endMagic != END_MAGIC) {
                        reset();
                        continue;
                    }

                    out.add(response);
                    response = new Response();
                    state = ParserState.START_MAGIC_NUMBER;
                    break;

                default:
                    throw new IllegalStateException("Unknown state: " + state);
            }
        }
    }

    private void reset() {
        state = ParserState.START_MAGIC_NUMBER;
        response = new Response();
    }
}