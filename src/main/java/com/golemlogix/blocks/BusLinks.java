package com.golemlogix.blocks;

import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Общая часть всех шин: у каждой из шести сторон есть флаг «здесь подключена мини-трубка» (как у труб), по которому модель
 * рисует либо короткий разъём, либо мини-трубку до самого края блока; форма блока собирается из тела шины, устройства
 * подачи/выгрузки спереди и разъёмов.
 */
public final class BusLinks {
    /** Рабочий узел спереди: раструб (импорт), сопло (экспорт) или монокль (перископ). */
    public enum Device {FUNNEL, NOZZLE, LENS}

    public static final Map<Direction, BooleanProperty> PROPS = new EnumMap<>(Direction.class);

    static {
        PROPS.put(Direction.NORTH, BlockStateProperties.NORTH);
        PROPS.put(Direction.EAST, BlockStateProperties.EAST);
        PROPS.put(Direction.SOUTH, BlockStateProperties.SOUTH);
        PROPS.put(Direction.WEST, BlockStateProperties.WEST);
        PROPS.put(Direction.UP, BlockStateProperties.UP);
        PROPS.put(Direction.DOWN, BlockStateProperties.DOWN);
    }

    private BusLinks() {
    }

    public static void addProperties(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(DirectionalBlock.FACING);
        for (BooleanProperty p : PROPS.values()) {
            builder.add(p);
        }
    }

    public static BlockState withNoLinks(BlockState state) {
        for (BooleanProperty p : PROPS.values()) {
            state = state.setValue(p, false);
        }
        return state;
    }

    /** Соединяется ли шина в этом состоянии с соседним блоком в направлении dir: обе стороны должны быть согласны. */
    public static boolean connects(BlockState self, BlockState neighbor, Direction dir) {
        return GolemNode.linked(self, neighbor, dir);
    }

    /** Все шесть флагов по соседям (при постановке блока). */
    public static BlockState connected(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            state = state.setValue(PROPS.get(d), GolemNode.linked(level, pos, state, n, level.getBlockState(n), d));
        }
        return state;
    }

    /** Сосед изменился: флаг стороны по связи с ним (заглушка на трубе-соседе связь закрывает). */
    public static BlockState update(BlockState state, Direction direction, BlockState neighbor, BlockGetter level, BlockPos pos) {
        return state.setValue(PROPS.get(direction), GolemNode.linked(level, pos, state, pos.relative(direction), neighbor, direction));
    }

    // ----------------------------------------------------------------------------------------------- формы
    private static final Map<Long, VoxelShape> CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, VoxelShape> COLLISION = new ConcurrentHashMap<>();

    /**
     * Шина занимает переднюю половину блока (Z 0..8 «лицом на север»): рабочий узел 0..3.2, корпус 3.2..8. Боковые
     * мини-трубки выходят из середины корпуса (Z = {@link #MID}), задняя — из его задней грани до края блока.
     */
    public static final double BODY_LO = 4.8, BODY_HI = 11.2, BODY_Z0 = 3.2, BODY_Z1 = 8, MID = 5.6;

    /** Нижняя половина блока: шины и трубы работают как ступенька — на них заходят, не прыгая. */
    public static final VoxelShape STEP = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 8, 16);

    public static VoxelShape shape(BlockState state, Device device) {
        long key = key(state, device);
        return CACHE.computeIfAbsent(key, k -> build(state.getValue(DirectionalBlock.FACING), (int) (k & 0xFF), device));
    }

    /** Форма столкновений: та же, но не выше половины блока. */
    public static VoxelShape collision(BlockState state, Device device) {
        return COLLISION.computeIfAbsent(key(state, device), k -> step(shape(state, device)));
    }

    /** Обрезка формы по нижней половине блока; пустая (вся деталь сверху) — плоская ступенька-заглушка не нужна. */
    public static VoxelShape step(VoxelShape shape) {
        return Shapes.join(shape, STEP, net.minecraft.world.phys.shapes.BooleanOp.AND).optimize();
    }

    private static long key(BlockState state, Device device) {
        Direction facing = state.getValue(DirectionalBlock.FACING);
        int bits = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(PROPS.get(d))) {
                bits |= 1 << d.ordinal();
            }
        }
        return ((long) device.ordinal() << 16) | ((long) facing.ordinal() << 8) | bits;
    }

    private static VoxelShape build(Direction facing, int mask, Device device) {
        // собираем в системе «лицом на север», потом поворачиваем на нужную сторону
        double lo = BODY_LO, hi = BODY_HI, z0 = BODY_Z0, z1 = BODY_Z1, t0 = MID - 2, t1 = MID + 2;
        VoxelShape local = Shapes.or(box(lo, lo, z0, hi, hi, z1), box(5.5, hi, 3.6, 10.5, hi + 0.4, 7.6));
        local = Shapes.or(local, switch (device) {
            case FUNNEL -> Shapes.or(box(2.5, 2.5, 0, 13.5, 13.5, 1.2), box(3.6, 3.6, 1.2, 12.4, 12.4, z0));
            case NOZZLE -> box(4.6, 4.6, 0, 11.4, 11.4, z0);
            case LENS -> Shapes.or(box(3, 3, 0, 13, 13, 1.4), box(5, 5, 1.4, 11, 11, z0));
        });
        for (Direction l : Direction.values()) {
            if (l == Direction.NORTH) {
                continue; // спереди рабочий узел
            }
            boolean connected = (mask & (1 << rotate(l, facing).ordinal())) != 0;
            if (l == Direction.UP && !connected) {
                continue; // на верхней стороне без трубы — рунная плита со звездой, разъёма нет
            }
            double stub = 1.2;
            local = Shapes.or(local, switch (l) {
                case SOUTH -> box(6, 6, z1, 10, 10, connected ? 16 : z1 + stub);
                case EAST -> box(hi, 6, t0, connected ? 16 : hi + stub, 10, t1);
                case WEST -> box(connected ? 0 : lo - stub, 6, t0, lo, 10, t1);
                case UP -> box(6, hi, t0, 10, 16, t1);
                default -> box(6, connected ? 0 : lo - stub, t0, 10, lo, t1);
            });
        }
        return rotateShape(local, facing);
    }

    private static VoxelShape box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return net.minecraft.world.level.block.Block.box(x0, y0, z0, x1, y1, z1);
    }

    /** Куда смотрит локальное направление l после поворота «север -> facing» (так же, как поворачивает блокстейт). */
    public static Direction rotate(Direction l, Direction facing) {
        int x = l.getStepX(), y = l.getStepY(), z = l.getStepZ();
        int rx = x, ry = y, rz = z;
        switch (facing) {
            case EAST -> {
                rx = -z;
                rz = x;
            }
            case SOUTH -> {
                rx = -x;
                rz = -z;
            }
            case WEST -> {
                rx = z;
                rz = -x;
            }
            case UP -> {
                ry = -z;
                rz = y;
                rx = x;
            }
            case DOWN -> {
                ry = z;
                rz = -y;
                rx = x;
            }
            default -> {
            }
        }
        return Direction.getNearest(rx, ry, rz);
    }

    /** Поворачивает форму, нарисованную «лицом на север», в сторону facing. */
    public static VoxelShape rotateShape(VoxelShape shape, Direction facing) {
        if (facing == Direction.NORTH) {
            return shape;
        }
        VoxelShape[] out = {Shapes.empty()};
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double[] a = point(x0, y0, z0, facing), b = point(x1, y1, z1, facing);
            out[0] = Shapes.or(out[0], Shapes.box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                    Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])));
        });
        return out[0].optimize();
    }

    /** Точка блока (0..1) после поворота вокруг центра блока. */
    private static double[] point(double x, double y, double z, Direction facing) {
        double cx = x - 0.5, cy = y - 0.5, cz = z - 0.5;
        double rx = cx, ry = cy, rz = cz;
        switch (facing) {
            case EAST -> {
                rx = -cz;
                rz = cx;
            }
            case SOUTH -> {
                rx = -cx;
                rz = -cz;
            }
            case WEST -> {
                rx = cz;
                rz = -cx;
            }
            case UP -> {
                ry = -cz;
                rz = cy;
            }
            case DOWN -> {
                ry = cz;
                rz = -cy;
            }
            default -> {
            }
        }
        return new double[]{rx + 0.5, ry + 0.5, rz + 0.5};
    }
}
