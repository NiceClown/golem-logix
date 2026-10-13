package com.golemlogix.blockentity;

import com.golemlogix.blocks.CoolingCoprocessorBlock;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.network.CraftDonePayload;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Крио-Калькулятор: центральный процессор автокрафта. Одна задача за раз;
 * Сопроцессоры Охлаждения вплотную к нему ускоряют параллельное выполнение шагов.
 * Пока задача идёт, над калькулятором крутится мини-модель того, что он делает прямо сейчас.
 */
public class CryoCalculatorBlockEntity extends BlockEntity implements com.golemlogix.crafting.CraftingCpu {
    public static final int CYCLE_TICKS = 4;
    public static final int BASE_WORKERS = 1;
    public static final int WORKERS_PER_COPROCESSOR = 2;
    /** Сжатый Сопроцессор в ячейке апгрейда ускоряет вдвое сильнее приставленного: место ему не нужно, но сжатие стоит Source. */
    public static final int WORKERS_PER_COMPRESSED = 4;

    @Nullable
    private CraftingJob job;
    private int coprocessors = 0;
    private boolean active = false;
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.CALCULATOR, this);
    private final RedstoneControl redstone = new RedstoneControl();
    /** Что последний раз отослано клиентам как «делается сейчас» (чтобы слать только при смене). */
    private ItemStack sentCurrent = ItemStack.EMPTY;
    private long lastCurrentSync = -100;
    /** Витрина для клиентов: даже мгновенный заказ виден не меньше VisualHold.TICKS тиков (на крафт не влияет). */
    private final VisualHold hold = new VisualHold();

    /**
     * Ускоряют ли сейчас сопроцессоры работу (за последние BOOST_HOLD тиков был цикл с несколькими шагами сразу): тогда над
     * калькулятором появляется снеговик-бригадир с головой атланта.
     */
    private boolean boosted = false;
    private long boostUntil = 0;
    private static final int BOOST_HOLD = 60;

    public boolean isBoosted() {
        return boosted;
    }

    // --- клиент
    private boolean clientHasJob = false;
    private ItemStack clientCurrent = ItemStack.EMPTY;

    public CryoCalculatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_CALCULATOR.get(), pos, state);
    }

    @Override
    public boolean isIdle() {
        return job == null;
    }

    @Override
    @Nullable
    public CraftingJob getJob() {
        return job;
    }

    @Override
    public BlockPos cpuPos() {
        return worldPosition;
    }

    @Override
    public int getCoprocessors() {
        return coprocessors;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public int getWorkers() {
        return BASE_WORKERS + WORKERS_PER_COPROCESSOR * coprocessors
                + WORKERS_PER_COMPRESSED * upgrades.count(com.golemlogix.upgrade.UpgradeType.SPEED);
    }

    /** Сколько Сжатых Сопроцессоров стоит в ячейках. */
    public int getCompressed() {
        return upgrades.count(com.golemlogix.upgrade.UpgradeType.SPEED);
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    /** Данные окна: 0 — режим редстоуна. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return redstone.mode().ordinal();
            }

            @Override
            public void set(int index, int value) {
                redstone.setMode(RedstoneMode.byId(value));
                setChanged();
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
    }

    @Override
    public void startJob(CraftingJob newJob) {
        this.job = newJob;
        if (level != null) {
            hold.show(newJob.current().stack(), level.getGameTime());
        }
        setChanged();
        syncToClient();
    }

    /** Сколько единиц предмета ждёт текущая задача от машин (0, если задачи нет). */
    @Override
    public long wants(ItemKey key) {
        return job == null ? 0L : job.wants(key);
    }

    @Override
    public ItemStack absorb(ItemStack stack) {
        if (job == null) {
            return stack;
        }
        ItemStack rest = job.absorb(stack);
        if (rest.getCount() != stack.getCount()) {
            setChanged();
        }
        return rest;
    }

    /** Отмена: всё содержимое задачи возвращается в сеть (лишнее выбрасывается). */
    @Override
    public void cancelJob() {
        if (job == null || level == null || level.isClientSide()) {
            return;
        }
        GolemNetwork net = GolemNetwork.scan(level, worldPosition);
        List<ItemStack> overflow = job.refundAll(net);
        for (ItemStack s : overflow) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5, s);
        }
        job = null;
        setChanged();
        syncToClient();
    }

    /** Выбросить содержимое при разрушении блока. */
    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (!upgrades.getItem(i).isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5, upgrades.removeItemNoUpdate(i));
            }
        }
        if (job == null) {
            return;
        }
        for (var e : job.inventory().entrySet()) {
            long n = e.getValue();
            while (n > 0) {
                int chunk = (int) Math.min(n, e.getKey().stack().getMaxStackSize());
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5, e.getKey().toStack(chunk));
                n -= chunk;
            }
        }
        job = null;
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CryoCalculatorBlockEntity be) {
        long time = level.getGameTime();
        boolean hasRedstone = be.upgrades.has(UpgradeType.REDSTONE);
        boolean powered = hasRedstone && level.hasNeighborSignal(pos);
        if (hasRedstone) {
            be.redstone.observe(powered);
        }
        if ((time + pos.asLong()) % 20 == 0) {
            int n = 0;
            for (Direction dir : Direction.values()) {
                BlockPos p = pos.relative(dir);
                BlockState ns = level.getBlockState(p);
                if (ns.getBlock() instanceof CoolingCoprocessorBlock) {
                    n++;
                    ensureCoprocessorEntity(level, p, ns);
                }
            }
            be.coprocessors = n;
        }

        boolean wasActive = be.active;
        if (be.job == null) {
            be.active = false;
        } else if ((time + pos.asLong()) % CYCLE_TICKS == 0) {
            GolemNetwork net = GolemNetwork.get(level, pos);
            boolean ok = net.isOnline() && net.isDeviceActive(pos);
            boolean allowed = be.redstone.allows(hasRedstone, powered, ok);
            be.active = ok && allowed;
            if (ok && !allowed) {
                if (be.job.status() != CraftingJob.Status.PAUSED) {
                    be.job.setStatus(CraftingJob.Status.PAUSED);
                    be.syncToClient();
                }
            } else if (ok) {
                CraftingJob.Status before = be.job.status();
                long doneBefore = be.job.doneExecutions();
                be.job.tick(net, be.getWorkers());
                long done = be.job.doneExecutions() - doneBefore;
                if (done != 0) {
                    level.playSound(null, pos, ModSounds.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.3f, 1.2f);
                }
                // сопроцессоры по-настоящему помогли: за цикл сделано больше шагов, чем смог бы один калькулятор
                if (done > BASE_WORKERS) {
                    be.boostUntil = time + BOOST_HOLD;
                }
                boolean justShown = be.hold.show(be.job.current().stack(), time);
                if (be.job.isDone()) {
                    notifyFinished(level, be.job);
                    be.job = null;
                    be.active = false;
                }
                if (justShown) {
                    be.syncToClient();
                }
                be.setChanged();
                if (be.job == null || be.job.status() != before) {
                    be.syncToClient();
                } else {
                    be.syncCurrentIfChanged(time);
                }
            }
        }
        boolean boosted = be.active && time < be.boostUntil;
        if (boosted != be.boosted) {
            be.boosted = boosted;
            be.syncToClient();
        }
        if (wasActive != be.active) {
            be.syncToClient();
            if (state.hasProperty(GolemProperties.ACTIVE) && state.getValue(GolemProperties.ACTIVE) != be.active) {
                level.setBlock(pos, state.setValue(GolemProperties.ACTIVE, be.active), 3);
            }
        }
        if (be.hold.ended(time)) {
            be.syncToClient();
        }
    }

    /** Мини-модель над калькулятором: клиентам — только при смене предмета и не чаще двух раз в секунду. */
    private void syncCurrentIfChanged(long time) {
        if (job == null || time - lastCurrentSync < 10) {
            return;
        }
        ItemStack cur = job.current().stack();
        if (!ItemStack.isSameItemSameComponents(cur, sentCurrent)) {
            lastCurrentSync = time;
            syncToClient();
        }
    }

    /** Заказ готов: тому, кто его сделал, — уведомление (как в AE2). */
    public static void notifyFinished(Level level, CraftingJob done) {
        if (done.requester() == null || !(level instanceof ServerLevel sl)) {
            return;
        }
        ServerPlayer player = sl.getServer().getPlayerList().getPlayer(done.requester());
        if (player == null) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> e : done.targets().entrySet()) {
            items.add(e.getKey().toStack((int) Math.min(e.getValue(), Integer.MAX_VALUE)));
            if (items.size() >= 8) {
                break;
            }
        }
        PacketDistributor.sendToPlayer(player, new CraftDonePayload(items));
    }

    /**
     * Сопроцессоры, поставленные до появления у них анимации, не имеют блок-сущности: создаём её и сообщаем клиентам,
     * чтобы маховик начал крутиться и у старых блоков.
     */
    private static void ensureCoprocessorEntity(Level level, BlockPos p, BlockState state) {
        LevelChunk chunk = level.getChunkAt(p);
        if (chunk.getBlockEntity(p, LevelChunk.EntityCreationType.CHECK) == null) {
            level.getBlockEntity(p);
            level.sendBlockUpdated(p, state, state, 3);
        }
    }

    // --------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (job != null) {
            tag.put("Job", job.save(registries));
        }
        tag.put("Upgrades", upgrades.createTag(registries));
        redstone.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        job = tag.contains("Job") ? CraftingJob.load(tag.getCompound("Job"), registries) : null;
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
        redstone.load(tag);
    }

    private boolean devWorking = false;
    private ItemStack devItem = ItemStack.EMPTY;

    public void setVisualWorkForDev(boolean on, @Nullable String item) {
        this.devWorking = on;
        if (item != null) {
            var it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(item));
            this.devItem = new ItemStack(it);
        } else {
            this.devItem = ItemStack.EMPTY;
        }
        setChanged();
        syncToClient();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        long now = level == null ? 0 : level.getGameTime();
        boolean showing = hold.active(now);
        tag.putBoolean("Active", devWorking || active || showing);
        tag.putBoolean("Boosted", devWorking || boosted);
        tag.putBoolean("HasJob", devWorking || job != null);
        sentCurrent = devWorking && !devItem.isEmpty() ? devItem : (job != null ? job.current().stack() : hold.item(now));
        if (!sentCurrent.isEmpty()) {
            tag.put("Current", sentCurrent.save(registries));
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        active = tag.getBoolean("Active");
        boosted = tag.getBoolean("Boosted");
        clientHasJob = tag.getBoolean("HasJob");
        clientCurrent = tag.contains("Current") ? ItemStack.parse(lookupProvider, tag.getCompound("Current")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    /** Пакет sendBlockUpdated несёт тот же тег, что и при загрузке чанка: читаем только поля для клиента (не задачу). */
    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        handleUpdateTag(pkt.getTag(), lookupProvider);
    }

    public boolean clientHasJob() {
        return clientHasJob;
    }

    /** Клиент: что калькулятор делает сейчас (пусто — простаивает). */
    public ItemStack clientCurrent() {
        return clientCurrent;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Ключ цели для отображения (может быть null). */
    @Nullable
    public ItemKey targetKey() {
        return job == null ? null : job.target();
    }

    // --------------------------------------------------------------- клиентская анимация

    /** Плавная «раскрутка» калькулятора 0..1 (горки и матричный дождь) и её фаза. */
    public float spin = 0f;
    public float spinPrev = 0f;
    /** Бригадир: 0 — нет, 1 — стоит на крыше (выскакивает и прячется плавно). */
    public float foreman = 0f;
    public float foremanPrev = 0f;

    // Визуальный таймер минимальной длительности работы (не менее 28 тиков = 1.4 секунды)
    // Гарантирует, что при мгновенном крафте анимация полностью отыгрывает сборку изделия
    // и плавно возвращается в покой, без резких исчезновений и телепортаций.
    public int visualWorkTimer = 0;
    private ItemStack visualItem = ItemStack.EMPTY;

    public ItemStack clientDisplayItem() {
        if (!clientCurrent.isEmpty()) {
            return clientCurrent;
        }
        return visualItem;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CryoCalculatorBlockEntity be) {
        be.spinPrev = be.spin;
        be.foremanPrev = be.foreman;

        boolean on = state.hasProperty(GolemProperties.ACTIVE) ? state.getValue(GolemProperties.ACTIVE) : be.active;
        if (on || !be.clientCurrent.isEmpty()) {
            be.visualWorkTimer = Math.max(be.visualWorkTimer, 32);
            if (!be.clientCurrent.isEmpty()) {
                be.visualItem = be.clientCurrent.copy();
            }
        } else if (be.visualWorkTimer > 0) {
            be.visualWorkTimer--;
        }

        boolean activeVisually = on || be.visualWorkTimer > 0;

        if (activeVisually) {
            // Энергичный разгон до максимума
            be.spin += (1f - be.spin) * 0.14f;
            if (be.spin > 0.98f) {
                be.spin = 1f;
            }
        } else {
            // Плавное возвращение в состояние простоя (smooth return-to-idle)
            be.spin += (0f - be.spin) * 0.05f;
            if (be.spin < 0.005f) {
                be.spin = 0f;
                be.visualItem = ItemStack.EMPTY;
            }
        }

        be.foreman = Math.max(0f, Math.min(1f, be.foreman + (be.boosted && be.spin > 0.4f ? 0.08f : -0.05f)));
    }
}
