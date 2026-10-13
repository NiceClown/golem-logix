package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.RecipeMonitorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: the state of all Cryo-Calculators of the network for the Recipe Monitor.
 *
 * @param error access error key (empty = all is well)
 */
public record MonitorPayload(int containerId, String error, List<Cpu> cpus) implements CustomPacketPayload {
    public static final Type<MonitorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "monitor"));

    /** One job step: what comes out and how much is left to make. */
    public record Step(ItemStack item, long left) {
    }

    /**
     * A calculator (or a mainframe core: core 1.., 0 for a standalone calculator). state -1 = idle, otherwise the ordinal of CraftingJob.Status.
     * targets = ordered items (count = ordered), steps = remaining steps, waiting = what is expected from machines.
     */
    public record Cpu(BlockPos pos, int core, int state, int coprocessors, long done, long total, ItemStack current,
                      List<Step> targets, List<Step> steps, List<Step> waiting) {
    }

    private static void writeSteps(RegistryFriendlyByteBuf buf, List<Step> list) {
        buf.writeVarInt(list.size());
        for (Step s : list) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.item().copyWithCount(1));
            buf.writeVarLong(s.left());
        }
    }

    private static List<Step> readSteps(RegistryFriendlyByteBuf buf) {
        int n = Math.min(64, buf.readVarInt());
        List<Step> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Step(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong()));
        }
        return out;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MonitorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeUtf(p.error);
                buf.writeVarInt(p.cpus.size());
                for (Cpu c : p.cpus) {
                    buf.writeBlockPos(c.pos());
                    buf.writeVarInt(c.core());
                    buf.writeVarInt(c.state());
                    buf.writeVarInt(c.coprocessors());
                    buf.writeVarLong(c.done());
                    buf.writeVarLong(c.total());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, c.current().copyWithCount(1));
                    writeSteps(buf, c.targets());
                    writeSteps(buf, c.steps());
                    writeSteps(buf, c.waiting());
                }
            },
            buf -> {
                int id = buf.readVarInt();
                String error = buf.readUtf();
                int n = Math.min(256, buf.readVarInt());
                List<Cpu> cpus = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    BlockPos pos = buf.readBlockPos();
                    int core = buf.readVarInt();
                    int state = buf.readVarInt();
                    int cop = buf.readVarInt();
                    long done = buf.readVarLong();
                    long total = buf.readVarLong();
                    ItemStack current = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    cpus.add(new Cpu(pos, core, state, cop, done, total, current, readSteps(buf), readSteps(buf), readSteps(buf)));
                }
                return new MonitorPayload(id, error, cpus);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MonitorPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof RecipeMonitorMenu menu && menu.containerId == payload.containerId) {
                menu.clientReceive(payload);
            }
        });
    }
}
