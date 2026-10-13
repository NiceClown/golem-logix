package com.golemlogix.items;

import com.golemlogix.blockentity.PipeBlockEntity;
import com.golemlogix.blocks.SnowPipeBlock;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pipe Plug: right-click on a pipe closes its side closest to the click point (on a sleeve, that sleeve). A closed side
 * connects to nothing: this way two pipes running side by side do not merge into one line and their channels do not mix. Right-clicking
 * a closed side with a plug removes it.
 */
public class PipePlugItem extends ModItemWithTooltip {
    public PipePlugItem(Properties properties) {
        super("tooltip.golem_logix.pipe_plug", properties);
    }

    /** The side of the pipe closest to the click point (by the largest offset from the block centre). */
    public static Direction sideAt(BlockPos pos, Vec3 hit) {
        Vec3 d = hit.subtract(Vec3.atCenterOf(pos));
        return Direction.getNearest(d.x, d.y, d.z);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SnowPipeBlock pipe) || !(level.getBlockEntity(pos) instanceof PipeBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Direction side = sideAt(pos, ctx.getClickLocation());
        boolean plug = !be.isPlugged(side);
        Player player = ctx.getPlayer();
        toggle(level, pos, side, plug);
        if (plug) {
            if (player == null || !player.getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        } else if (player != null && !player.getAbilities().instabuild) {
            ItemStack back = new ItemStack(this);
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
        }
        level.playSound(null, pos, plug ? SoundEvents.COPPER_PLACE : SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 0.6f, plug ? 1.2f : 1.5f);
        if (player != null) {
            player.displayClientMessage(Component.translatable(plug ? "message.golem_logix.plug.on" : "message.golem_logix.plug.off",
                    Component.translatable("direction.golem_logix." + side.getName())), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Add or remove a plug and recompute the links of the pipe and its neighbour. */
    public static void toggle(Level level, BlockPos pos, Direction side, boolean plug) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity be) || !(level.getBlockState(pos).getBlock() instanceof SnowPipeBlock pipe)) {
            return;
        }
        be.setPlugged(side, plug);
        level.setBlock(pos, pipe.relink(level.getBlockState(pos), level, pos), Block.UPDATE_ALL);
        BlockPos n = pos.relative(side);
        BlockState ns = level.getBlockState(n);
        BlockState updated = Block.updateFromNeighbourShapes(ns, level, n);
        if (updated != ns) {
            level.setBlock(n, updated, Block.UPDATE_ALL);
        }
        // both sides of the plug: a split network needs its lights and channels recomputed on each side
        GolemNetwork.nodesChanged(level, pos);
        GolemNetwork.nodesChanged(level, n);
    }
}
