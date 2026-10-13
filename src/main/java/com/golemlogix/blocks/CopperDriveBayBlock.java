package com.golemlogix.blocks;

import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.menu.DriveBayMenu;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Дисковый шкаф — многоблок 2x2x1 (две клетки в ширину, две в высоту, одна в глубину). Ставится одним предметом, как дверь:
 * лицом к игроку, «нижняя левая» часть (если смотреть на шкаф спереди) там, куда ткнули, остальные — справа и выше.
 * Блок-сущность с дисками живёт только в «главной» нижней левой части; остальные части лишь проводят сеть и пересылают клики.
 */
public class CopperDriveBayBlock extends Block implements EntityBlock, GolemNode {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** Часть шкафа: right — сколько клеток правее главной (глазами зрителя спереди), up — сколько выше. */
    public enum Part implements StringRepresentable {
        BOTTOM_LEFT("bl", 0, 0),
        BOTTOM_RIGHT("br", 1, 0),
        TOP_LEFT("tl", 0, 1),
        TOP_RIGHT("tr", 1, 1);

        private final String key;
        public final int right;
        public final int up;

        Part(String key, int right, int up) {
            this.key = key;
            this.right = right;
            this.up = up;
        }

        public static Part of(int right, int up) {
            return values()[(up << 1) | right];
        }

        /** Часть, лежащая по горизонтали зеркально (для Mirror). */
        public Part mirrored() {
            return of(1 - right, up);
        }

        @Override
        public String getSerializedName() {
            return key;
        }
    }

    public CopperDriveBayBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(3.5f).requiresCorrectToolForDrops().noOcclusion()
                .pushReaction(PushReaction.BLOCK).lightLevel(state -> state.getValue(GolemProperties.ACTIVE) ? 5 : 0));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.BOTTOM_LEFT).setValue(GolemProperties.ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, GolemProperties.ACTIVE);
    }

    // ------------------------------------------------------------------ геометрия многоблока

    /** Куда от главной части отложена «правая» сторона шкафа (если смотреть на шкаф спереди). */
    public static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static BlockPos partPos(BlockPos master, Direction facing, Part part) {
        return master.relative(rightOf(facing), part.right).above(part.up);
    }

    /** Позиция главной части по позиции любой другой. */
    public static BlockPos masterOf(BlockPos pos, BlockState state) {
        Part part = state.getValue(PART);
        return pos.relative(rightOf(state.getValue(FACING)), -part.right).below(part.up);
    }

    /** Блок-сущность шкафа по любой из его четырёх частей. */
    @Nullable
    public static CopperDriveBayBlockEntity entityOf(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(masterOf(pos, state)) instanceof CopperDriveBayBlockEntity bay ? bay : null;
    }

    /** Ставит шкаф целиком (для команд, тестов и генераторов); главная часть — в master. */
    public static void place(LevelAccessor level, BlockPos master, Direction facing) {
        Block block = ModBlocks.COPPER_DRIVE_BAY.get();
        for (Part part : Part.values()) {
            level.setBlock(partPos(master, facing, part),
                    block.defaultBlockState().setValue(FACING, facing).setValue(PART, part), Block.UPDATE_CLIENTS);
        }
        if (level instanceof Level lvl) {
            for (Part part : Part.values()) {
                BlockPos p = partPos(master, facing, part);
                lvl.blockUpdated(p, block);
            }
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos();
        for (Part part : Part.values()) {
            BlockPos p = partPos(master, facing, part);
            if (!level.isInWorldBounds(p) || (part != Part.BOTTOM_LEFT && !level.getBlockState(p).canBeReplaced(context))) {
                return null;
            }
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(PART, Part.BOTTOM_LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (Part part : Part.values()) {
            if (part != Part.BOTTOM_LEFT) {
                level.setBlock(partPos(pos, facing, part), state.setValue(PART, part), 3);
            }
        }
    }

    /** Часть без соседа-«напарника» по ребру разрушается — шкаф исчезает целиком. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Part part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        Direction sidePartner = part.right == 0 ? rightOf(facing) : rightOf(facing).getOpposite();
        Direction verticalPartner = part.up == 0 ? Direction.UP : Direction.DOWN;
        if (direction == sidePartner || direction == verticalPartner) {
            Part expected = direction == sidePartner ? Part.of(1 - part.right, part.up) : Part.of(part.right, 1 - part.up);
            if (!neighbor.is(this) || neighbor.getValue(PART) != expected || neighbor.getValue(FACING) != facing) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {
            // в творческом режиме остальные части убираем тихо, чтобы предмет не выпал ещё раз
            BlockPos master = masterOf(pos, state);
            for (Part part : Part.values()) {
                BlockPos other = partPos(master, state.getValue(FACING), part);
                BlockState otherState = level.getBlockState(other);
                if (!other.equals(pos) && otherState.is(this)) {
                    level.setBlock(other, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, other, Block.getId(otherState));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        // зеркало переворачивает «лево/право» шкафа: части меняются местами по горизонтали
        return state.rotate(mirror.getRotation(state.getValue(FACING))).setValue(PART, state.getValue(PART).mirrored());
    }

    // ------------------------------------------------------------------------------ сеть и окно

    @Override
    public int channelCost() {
        return 1;
    }

    /** Канал занимает только главная часть. */
    @Override
    public int channelCost(BlockState state) {
        return state.getValue(PART) == Part.BOTTOM_LEFT ? 1 : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.BOTTOM_LEFT ? new CopperDriveBayBlockEntity(pos, state) : null;
    }

    /** События блока (открыли окно — дверца витрины) передаются блок-сущности, иначе сервер не разошлёт их клиентам. */
    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    /** Клиент: дверца витрины плавно открывается и закрывается. */
    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide() && type == com.golemlogix.init.ModBlockEntities.COPPER_DRIVE_BAY.get()
                ? (lvl, p, st, be) -> CopperDriveBayBlockEntity.clientTick(lvl, p, st, (CopperDriveBayBlockEntity) be)
                : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        CopperDriveBayBlockEntity bay = entityOf(level, pos, state);
        if (bay != null) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new DriveBayMenu(id, inv, bay.getContainer(), bay.priorityData()),
                    Component.translatable("block.golem_logix.copper_drive_bay")));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CopperDriveBayBlockEntity bay) {
                for (ItemStack disk : bay.getDisks()) {
                    if (!disk.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, disk.copy());
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
