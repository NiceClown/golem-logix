package com.golemlogix.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Shared bounds checks for custom payload codecs so malformed packets cannot allocate huge lists or desync buffers.
 */
final class PayloadCodecs {
    private PayloadCodecs() {
    }

    static int readCount(FriendlyByteBuf buf, int max) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) {
            throw new DecoderException("List size " + n + " out of bounds [0, " + max + "]");
        }
        return n;
    }
}
