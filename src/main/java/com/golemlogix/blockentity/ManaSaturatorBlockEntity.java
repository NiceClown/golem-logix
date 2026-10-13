package com.golemlogix.blockentity;

import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModItems;
import com.golemlogix.init.ModSounds;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.SaturatorStructure;
import com.golemlogix.system.SourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Насыщатель Маны — конвейер из трёх мест: левая задняя палуба (очередь заготовок), наковальня (железный голем куёт) и
 * правая задняя палуба (готовые пластины). Два медных голема носят независимо: подносчик заранее берёт следующую заготовку
 * и ждёт у наковальни, уносчик выходит к наковальне, пока идёт ритуал, и забирает результат сразу после последнего удара —
 * кузнец почти не простаивает.
 */
public class ManaSaturatorBlockEntity extends BlockEntity {
    /** Наковальня: заготовка под молотом или только что выкованный результат (его заберёт уносчик). */
    private ItemStack heldItem = ItemStack.EMPTY;
    /** Левая палуба: очередь заготовок (одного вида, до DECK_MAX). */
    private ItemStack inDeck = ItemStack.EMPTY;
    /** Правая палуба: готовые пластины (одного вида, до стопки). */
    private ItemStack outDeck = ItemStack.EMPTY;
    public static final int DECK_MAX = 4;
    /**
     * Ход ритуала в «единицах работы»: за тик +10 и ещё +4 за каждый Сопроцессор Охлаждения на крыше (5 с без них, ~1.7 с
     * с пятью).
     */
    private int ritualProgress = 0;
    public static final int MAX_PROGRESS = 1000;
    public static final int BASE_SPEED = 10;
    public static final int SPEED_PER_COOLER = 4;
    private boolean isCrafting = false;
    private int coolers = 0;
    /** Модуль Редстоуна (ставится ПКМ по машине) и его режим. */
    private boolean redstoneModule = false;
    private final com.golemlogix.upgrade.RedstoneControl redstone = new com.golemlogix.upgrade.RedstoneControl();
    /** Внутренний запас Source: пополняется из сети (или соседних банок) и расходуется на насыщение. */
    public static final int BUFFER_MAX = 5000;
    private static final int PULL_STEP = 100;
    private int source = 0;
    /** Последний разосланный клиентам уровень заполнения (в восьмых), чтобы не слать лишнего. */
    private int syncedLevel = 0;

    // ---------------------------------------------------------------------------------- носильщики
    /** Сколько тиков идёт медный голем от своей палубы до наковальни (и обратно). */
    public static final int WALK = 18;
    /** Фазы носильщика: дома, идёт к наковальне, ждёт у наковальни, идёт к палубе. */
    public static final int HOME = 0, TO_ANVIL = 1, AT_ANVIL = 2, TO_DECK = 3;

    /** Медный голем-носильщик: что в руках, фаза и сколько тиков осталось идти. */
    public static final class Carrier {
        public ItemStack hands = ItemStack.EMPTY;
        public int phase = HOME, timer = 0;

        void go(int newPhase) {
            phase = newPhase;
            timer = newPhase == TO_ANVIL || newPhase == TO_DECK ? WALK : 0;
        }

        CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag t = new CompoundTag();
            t.putInt("Phase", phase);
            t.putInt("Timer", timer);
            if (!hands.isEmpty()) {
                t.put("Hands", hands.save(registries));
            }
            return t;
        }

        void load(CompoundTag t, HolderLookup.Provider registries) {
            phase = t.getInt("Phase");
            timer = t.getInt("Timer");
            hands = t.contains("Hands") ? ItemStack.parse(registries, t.getCompound("Hands")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        }
    }

    /** Подносчик (левая палуба -> наковальня) и уносчик (наковальня -> правая палуба). */
    public final Carrier bringer = new Carrier(), taker = new Carrier();

    public ManaSaturatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MANA_SATURATOR.get(), pos, state);
    }

    /** Что на наковальне. */
    public ItemStack getHeldItem() {
        return heldItem;
    }

    /** Очередь заготовок на левой палубе. */
    public ItemStack inputDeck() {
        return inDeck;
    }

    /** Готовые пластины на правой палубе. */
    public ItemStack outputDeck() {
        return outDeck;
    }

    /**
     * Для шин, воронок и поставщиков шаблонов: ячейка 0 — левая палуба (только положить заготовку, до DECK_MAX одного
     * вида), ячейка 1 — правая палуба (только забрать готовое).
     */
    private final net.neoforged.neoforge.items.IItemHandler automation = new net.neoforged.neoforge.items.IItemHandler() {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? inDeck : outDeck;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !isInput(stack) || !formed()) {
                return stack;
            }
            int room = roomInDeck(stack);
            if (room <= 0) {
                return stack;
            }
            int n = Math.min(room, stack.getCount());
            if (!simulate) {
                addToDeck(stack.copyWithCount(n));
            }
            return stack.copyWithCount(stack.getCount() - n);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1 || amount <= 0 || outDeck.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int n = Math.min(amount, outDeck.getCount());
            ItemStack out = outDeck.copyWithCount(n);
            if (!simulate) {
                outDeck.shrink(n);
                if (outDeck.isEmpty()) {
                    outDeck = ItemStack.EMPTY;
                }
                changed();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? DECK_MAX : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && isInput(stack);
        }
    };

    public net.neoforged.neoforge.items.IItemHandler automation() {
        return automation;
    }

    private boolean formed() {
        BlockState state = getBlockState();
        return state.hasProperty(ManaSaturatorBlock.FORMED) && state.getValue(ManaSaturatorBlock.FORMED);
    }

    /** Сколько ещё таких заготовок поместится на левую палубу. */
    public int roomInDeck(ItemStack stack) {
        if (inDeck.isEmpty()) {
            return DECK_MAX;
        }
        return ItemStack.isSameItemSameComponents(inDeck, stack) ? DECK_MAX - inDeck.getCount() : 0;
    }

    /** Положить заготовки на левую палубу (проверка места — roomInDeck). */
    public void addToDeck(ItemStack stack) {
        if (inDeck.isEmpty()) {
            inDeck = stack.copy();
        } else {
            inDeck.grow(stack.getCount());
        }
        changed();
    }

    /** Забрать с палуб: сначала готовое, иначе очередь заготовок (рукой игрока). */
    public ItemStack takeFromDecks() {
        ItemStack out;
        if (!outDeck.isEmpty()) {
            out = outDeck;
            outDeck = ItemStack.EMPTY;
        } else {
            out = inDeck;
            inDeck = ItemStack.EMPTY;
        }
        if (!out.isEmpty()) {
            changed();
        }
        return out;
    }

    /** Положить прямо на наковальню (сцены DevShots и старые миры); ход ритуала сбрасывается. */
    public void setHeldItem(ItemStack stack) {
        this.heldItem = stack;
        this.ritualProgress = 0;
        this.isCrafting = false;
        changed();
    }

    /** Всё, что лежит в машине и в руках носильщиков (выпадает при разрушении). */
    public java.util.List<ItemStack> allItems() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (ItemStack s : new ItemStack[]{inDeck, heldItem, outDeck, bringer.hands, taker.hands}) {
            if (!s.isEmpty()) {
                out.add(s.copy());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ ручная ковка (наковальня до сборки машины)
    /**
     * Ручная ковка на наковальне несобранного насыщателя (промежуточная ступень, машина — её апгрейд): заготовку кладут на
     * площадку ПКМ, бьют Кузнечным молотом (ЛКМ), каждый удар тратит Source из заряда молота и немного сытости; после
     * MANUAL_HITS ударов заготовка становится пластиной — одной (машина даёт две). Вручную куются ТОЛЬКО три базовые пластины.
     */
    public static final int MANUAL_HITS = 4, MANUAL_COOLDOWN = 6;
    private int manualHits = 0;
    private long lastStrike = -100;

    public enum Strike { HIT, DONE, COOLDOWN, NO_SOURCE, NOTHING }

    /** Что даёт ручная ковка: только три базовые пластины, по одной. */
    public static ItemStack manualOutputFor(ItemStack input) {
        if (input.is(Blocks.IRON_BLOCK.asItem())) {
            return new ItemStack(ModItems.FLATTENED_IRON_CORE.get());
        }
        if (input.is(Blocks.COPPER_BLOCK.asItem()) || input.is(Blocks.CUT_COPPER.asItem()) || input.is(Blocks.WAXED_COPPER_BLOCK.asItem())) {
            return new ItemStack(ModItems.FLATTENED_COPPER_MATRIX.get());
        }
        if (input.is(Blocks.PACKED_ICE.asItem())) {
            return new ItemStack(ModItems.FLATTENED_CRYO_SUBSTRATE.get());
        }
        return ItemStack.EMPTY;
    }

    public static boolean isManualInput(ItemStack stack) {
        return !manualOutputFor(stack).isEmpty();
    }

    public int manualHits() {
        return manualHits;
    }

    public ItemStack anvilItem() {
        return heldItem;
    }

    public void placeOnAnvil(ItemStack one) {
        heldItem = one.copyWithCount(1);
        manualHits = 0;
        changed();
    }

    public ItemStack takeFromAnvil() {
        ItemStack s = heldItem;
        heldItem = ItemStack.EMPTY;
        manualHits = 0;
        changed();
        return s;
    }

    /** Удар молотом по заготовке на наковальне (сервер). */
    public Strike strike(ServerLevel level, BlockPos pos, Player player, ItemStack hammer) {
        if (!isManualInput(heldItem)) {
            return Strike.NOTHING;
        }
        long now = level.getGameTime();
        if (now - lastStrike < MANUAL_COOLDOWN) {
            return Strike.COOLDOWN;
        }
        if (!com.golemlogix.items.ForgeHammerItem.canStrike(hammer)) {
            return Strike.NO_SOURCE;
        }
        com.golemlogix.items.ForgeHammerItem.spendStrike(hammer);
        lastStrike = now;
        manualHits++;
        player.causeFoodExhaustion(0.3f);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.97, z = pos.getZ() + 0.5;
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 1.2f + manualHits * 0.06f);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.7f, 1.5f);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 8, 0.15, 0.03, 0.15, 0.1);
        level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 4, 0.2, 0.05, 0.2, 0.02);
        if (manualHits >= MANUAL_HITS) {
            heldItem = manualOutputFor(heldItem);
            manualHits = 0;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 6, 0.15, 0.05, 0.15, 0.02);
            level.playSound(null, pos, ModSounds.ANVIL_CRUSH.get(), SoundSource.BLOCKS, 0.9f, 1.5f);
            changed();
            return Strike.DONE;
        }
        changed();
        return Strike.HIT;
    }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public float getProgressFraction() {
        return Math.min(1f, (float) ritualProgress / (float) MAX_PROGRESS);
    }

    /** Сколько единиц работы за тик (с учётом сопроцессоров на крыше). */
    public int speed() {
        return BASE_SPEED + SPEED_PER_COOLER * coolers;
    }

    public int getCoolers() {
        return coolers;
    }

    public boolean hasRedstoneModule() {
        return redstoneModule;
    }

    public com.golemlogix.upgrade.RedstoneMode redstoneMode() {
        return redstone.mode();
    }

    /** ПКМ Модулем Редстоуна: поставить его (true) или, если уже стоит, переключить режим (false). */
    public boolean installOrCycleRedstone() {
        boolean installed = !redstoneModule;
        if (installed) {
            redstoneModule = true;
            redstone.setMode(com.golemlogix.upgrade.RedstoneMode.HIGH);
        } else {
            redstone.setMode(redstone.mode().next(true));
        }
        changed();
        return installed;
    }

    /** Снять Модуль Редстоуна (Shift+ПКМ пустой рукой); true — он стоял. */
    public boolean removeRedstoneModule() {
        if (!redstoneModule) {
            return false;
        }
        redstoneModule = false;
        changed();
        return true;
    }

    /** Есть ли сигнал редстоуна у любой клетки машины. */
    private boolean powered(Level level, BlockPos pos, Direction facing) {
        for (BlockPos p : SaturatorStructure.cells(pos, facing)) {
            if (level.isLoaded(p) && level.hasNeighborSignal(p)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCrafting() {
        return isCrafting;
    }

    public int getSource() {
        return source;
    }

    /** Заполнение чаши от 0 до 1. */
    public float getSourceFraction() {
        return Math.min(1f, (float) source / BUFFER_MAX);
    }

    private int levelEighths() {
        return source <= 0 ? 0 : 1 + Math.min(7, source * 8 / (BUFFER_MAX + 1));
    }

    /** Сеть, подключённая к любому разъёму машины (у которой есть ядро), иначе сеть фасадного разъёма. */
    public static GolemNetwork network(Level level, BlockPos controller, Direction facing) {
        GolemNetwork first = null;
        for (int port : SaturatorStructure.PORTS) {
            GolemNetwork net = GolemNetwork.get(level, SaturatorStructure.cellPos(controller, facing, port));
            if (net.hasCore()) {
                return net;
            }
            if (first == null) {
                first = net;
            }
        }
        return first;
    }

    /** Пополняет запас: из сети через любой разъём (если в ней есть ядро), иначе из банок вокруг машины. */
    private void refill(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        if (source >= BUFFER_MAX) {
            return;
        }
        // подкачка растёт вместе с ускорением: каждый охладитель ускоряет ритуал, значит и Source уходит быстрее
        int want = Math.min(PULL_STEP * (1 + coolers), BUFFER_MAX - source);
        int got = 0;
        Direction facing = state.getValue(ManaSaturatorBlock.FACING);
        GolemNetwork net = network(serverLevel, pos, facing);
        if (!SourceHelper.sourceRequired()) {
            // без Ars Nouveau Source взять негде: машина работает даром (см. SourceHelper.sourceRequired)
            got = want;
        } else if (net.hasCore() && net.isOnline()) {
            // списание из сети — всё или ничего: если столько нет, берём меньшими порциями (сколько есть)
            for (int ask = want; ask >= 10 && got == 0; ask /= 2) {
                if (net.consumeSource(ask)) {
                    got = ask;
                }
            }
        } else {
            got = SourceHelper.drainAround(serverLevel, SaturatorStructure.cells(pos, facing), want);
        }
        if (got > 0) {
            source += got;
            setChanged();
        }
    }

    private void syncLevel(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        int lv = levelEighths();
        if (lv != syncedLevel) {
            syncedLevel = lv;
            serverLevel.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /** Собирает машину, когда вокруг правильно стоят блоки. */
    private void tryForm(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(ManaSaturatorBlock.FACING);
        if (SaturatorStructure.check(serverLevel, pos, facing).valid()) {
            SaturatorStructure.form(serverLevel, pos, facing);
        }
    }

    /**
     * Шаг носильщиков (сервер). Подносчик: дома и есть очередь — берёт заготовку и идёт к наковальне, ждёт, пока она
     * освободится, кладёт и возвращается. Уносчик: идёт к наковальне, как только там ритуал или готовый результат, забирает
     * результат и несёт на правую палубу (если там чужие пластины или нет места — ждёт с ношей).
     */
    private boolean stepCarriers() {
        boolean changed = false;
        // подносчик
        switch (bringer.phase) {
            case HOME -> {
                if (!inDeck.isEmpty()) {
                    bringer.hands = inDeck.split(1);
                    if (inDeck.isEmpty()) {
                        inDeck = ItemStack.EMPTY;
                    }
                    bringer.go(TO_ANVIL);
                    changed = true;
                }
            }
            case TO_ANVIL -> {
                if (--bringer.timer <= 0) {
                    bringer.go(AT_ANVIL);
                    changed = true;
                }
            }
            case AT_ANVIL -> {
                if (heldItem.isEmpty()) {
                    heldItem = bringer.hands;
                    bringer.hands = ItemStack.EMPTY;
                    ritualProgress = 0;
                    isCrafting = false;
                    bringer.go(TO_DECK);
                    changed = true;
                }
            }
            default -> {
                if (--bringer.timer <= 0) {
                    bringer.go(HOME);
                    changed = true;
                }
            }
        }
        // уносчик
        boolean result = !heldItem.isEmpty() && !isInput(heldItem);
        switch (taker.phase) {
            case HOME -> {
                if (isCrafting || result) {
                    taker.go(TO_ANVIL);
                    changed = true;
                }
            }
            case TO_ANVIL -> {
                if (--taker.timer <= 0) {
                    taker.go(AT_ANVIL);
                    changed = true;
                }
            }
            case AT_ANVIL -> {
                if (result) {
                    taker.hands = heldItem;
                    heldItem = ItemStack.EMPTY;
                    taker.go(TO_DECK);
                    changed = true;
                } else if (!isCrafting && (heldItem.isEmpty() || !isInput(heldItem))) {
                    // забирать нечего (результат унесли руками) — домой налегке
                    taker.go(TO_DECK);
                    changed = true;
                }
            }
            default -> {
                if (taker.timer > 0) {
                    taker.timer--;
                } else if (taker.hands.isEmpty()) {
                    taker.go(HOME);
                    changed = true;
                } else if (outDeck.isEmpty() || ItemStack.isSameItemSameComponents(outDeck, taker.hands)
                        && outDeck.getCount() + taker.hands.getCount() <= outDeck.getMaxStackSize()) {
                    if (outDeck.isEmpty()) {
                        outDeck = taker.hands;
                    } else {
                        outDeck.grow(taker.hands.getCount());
                    }
                    taker.hands = ItemStack.EMPTY;
                    taker.go(HOME);
                    changed = true;
                }
            }
        }
        return changed;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ManaSaturatorBlockEntity entity) {
        boolean formed = state.getValue(ManaSaturatorBlock.FORMED);
        boolean powered = false;
        if (!(level instanceof ServerLevel sl)) {
            if (formed) {
                entity.walkLamp(level);
                entity.clientTick(level, pos);
            }
            return;
        }
        long t = level.getGameTime() + pos.asLong();
        if (!formed) {
            if (t % 20 == 0) {
                entity.tryForm(sl, pos, state);
                if (sl.getBlockState(pos).getValue(ManaSaturatorBlock.FORMED)) {
                    entity.manualHits = 0;
                }
            }
            entity.ritualProgress = 0;
            entity.isCrafting = false;
            return;
        }
        Direction facing = state.getValue(ManaSaturatorBlock.FACING);
        if (t % 40 == 0 && !SaturatorStructure.check(level, pos, facing).valid()) {
            SaturatorStructure.disassemble(level, pos, facing, null);
            return;
        }
        if (t % 5 == 0) {
            entity.refill(sl, pos, state);
        }
        if (t % 20 == 0) {
            int n = SaturatorStructure.coolers(level, pos, facing);
            if (n != entity.coolers) {
                entity.coolers = n;
                entity.setChanged();
                sl.sendBlockUpdated(pos, state, state, 3);
            }
        }
        if (entity.redstoneModule) {
            powered = entity.powered(level, pos, facing);
            entity.redstone.observe(powered);
        }
        entity.syncLevel(sl, pos, state);
        boolean changed = entity.stepCarriers();

        ItemStack held = entity.heldItem;
        if (held.isEmpty() || !isInput(held)) {
            entity.ritualProgress = 0;
            entity.isCrafting = false;
        } else {
            ItemStack recipeOutput = outputFor(held);
            int requiredSource = entity.getRequiredSource(held);
            // начинаем, когда в чаше хватает Source (и разрешает Модуль Редстоуна), и сразу списываем его
            if (!entity.isCrafting && entity.source >= requiredSource && entity.redstone.allows(entity.redstoneModule, powered, true)) {
                entity.source -= requiredSource;
                entity.isCrafting = true;
                entity.ritualProgress = 0;
                changed = true;
            }
            if (entity.isCrafting) {
                int before = entity.ritualProgress;
                entity.ritualProgress = Math.min(MAX_PROGRESS, entity.ritualProgress + entity.speed());
                // гул раз в секунду ритуала
                if (before / 200 != entity.ritualProgress / 200) {
                    sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), ModSounds.VACUUM_HUM.get(), SoundSource.BLOCKS, 0.6f, 1.2f);
                }
                // голем бьёт по заготовке пять раз за ритуал: звон удара и искры
                int hitsBefore = (before * 5 + MAX_PROGRESS / 4) / MAX_PROGRESS;
                int hitsNow = (entity.ritualProgress * 5 + MAX_PROGRESS / 4) / MAX_PROGRESS;
                if (hitsNow != hitsBefore && entity.ritualProgress < MAX_PROGRESS) {
                    sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), net.minecraft.sounds.SoundEvents.IRON_GOLEM_ATTACK,
                            SoundSource.BLOCKS, 0.5f, 1.3f);
                    sl.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.getX() + 0.5, pos.getY() - 0.05, pos.getZ() + 0.5,
                            6, 0.15, 0.05, 0.15, 0.08);
                }
                if (entity.ritualProgress >= MAX_PROGRESS) {
                    held.shrink(1);
                    if (!held.isEmpty()) {
                        // в старых мирах на наковальне могла лежать стопка: лишние результаты — рядом
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, recipeOutput.copy());
                    } else {
                        entity.heldItem = recipeOutput.copy();
                    }
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 35, 0.3, 0.3, 0.3, 0.1);
                    sl.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 25, 0.3, 0.3, 0.3, 0.05);
                    sl.playSound(null, pos.getX(), pos.getY(), pos.getZ(), ModSounds.ANVIL_CRUSH.get(), SoundSource.BLOCKS, 1.0f, 1.4f);
                    entity.ritualProgress = 0;
                    entity.isCrafting = false;
                    changed = true;
                }
            }
        }
        if (changed) {
            entity.changed();
        }
    }

    /** Клиент: ход ритуала между пакетами, искры у наковальни, шаги носильщиков (переходы фаз приходят с сервера). */
    private void clientTick(Level level, BlockPos pos) {
        for (Carrier c : new Carrier[]{bringer, taker}) {
            if ((c.phase == TO_ANVIL || c.phase == TO_DECK) && c.timer > 0) {
                c.timer--;
            }
        }
        if (isCrafting && ritualProgress < MAX_PROGRESS) {
            ritualProgress = Math.min(MAX_PROGRESS, ritualProgress + speed());
        }
        if (isCrafting) {
            double cx = pos.getX() + 0.5, cy = pos.getY() + 0.1, cz = pos.getZ() + 0.5;
            double angle = level.getGameTime() * 0.2;
            double radius = 0.6 * (1.0 - getProgressFraction() * 0.5);
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, cx + Math.cos(angle) * radius, cy, cz + Math.sin(angle) * radius, 0, 0.02, 0);
            level.addParticle(ParticleTypes.SNOWFLAKE, cx + (Math.random() - 0.5) * 0.4, cy + (Math.random() - 0.5) * 0.4,
                    cz + (Math.random() - 0.5) * 0.4, 0, 0, 0);
        }
    }

    /**
     * Клиент: медный голем с лампой на крыше (над балкой, клетка хозяина X 16..32, Z 0..16). В простое ходит по кругу вокруг
     * основания кристалла (19.5..28.5 x 3.5..12.5) по восьми точкам, при ритуале и нехватке Source стоит впереди.
     */
    public final LampWalker walker = new LampWalker(24f, 1.8f);
    private static final float[][] RING = {{17.6f, 1.8f}, {24f, 1.8f}, {30.4f, 1.8f}, {30.4f, 8f}, {30.4f, 14.2f}, {24f, 14.2f},
            {17.6f, 14.2f}, {17.6f, 8f}};

    private void walkLamp(Level level) {
        var r = level.random;
        walker.tick(lampState() == 0, 24f, 1.8f, r, () -> {
            // к соседней точке круга (по или против часовой): напрямик через кристалл голем не ходит
            walker.ring = Math.floorMod(walker.ring + (r.nextInt(4) == 0 ? -1 : 1), RING.length);
            return RING[walker.ring];
        });
    }

    /** Сигнал лампы на крыше: 0 — простой, 1 — идёт ритуал, 2 — заготовка лежит, а Source не хватает. */
    public int lampState() {
        if (isCrafting) {
            return 1;
        }
        if (!heldItem.isEmpty() && isInput(heldItem) && source < getRequiredSource(heldItem)) {
            return 2;
        }
        return 0;
    }

    /** Можно ли прессовать предмет (иначе клик с ним по машине — обычная установка блока). */
    public static boolean isInput(ItemStack stack) {
        return !outputFor(stack).isEmpty();
    }

    public static ItemStack outputFor(ItemStack input) {
        if (input.is(Blocks.IRON_BLOCK.asItem())) {
            return new ItemStack(ModItems.FLATTENED_IRON_CORE.get(), 2);
        } else if (input.is(Blocks.COPPER_BLOCK.asItem()) || input.is(Blocks.CUT_COPPER.asItem()) || input.is(Blocks.WAXED_COPPER_BLOCK.asItem())) {
            return new ItemStack(ModItems.FLATTENED_COPPER_MATRIX.get(), 2);
        } else if (input.is(Blocks.PACKED_ICE.asItem())) {
            return new ItemStack(ModItems.FLATTENED_CRYO_SUBSTRATE.get(), 2);
        } else if (input.is(ModItems.IMPLODED_WITHER_PLASMA.get())) {
            return new ItemStack(ModItems.INTERDIMENSIONAL_OCCULT_MATRIX.get());
        } else if (input.is(com.golemlogix.init.ModBlocks.COOLING_COPROCESSOR.get().asItem())) {
            // сопроцессор, сжатый в плоский ускоритель для шин
            return new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get());
        }
        return ItemStack.EMPTY;
    }

    private int getRequiredSource(ItemStack input) {
        if (input.is(ModItems.IMPLODED_WITHER_PLASMA.get())) {
            return 2500;
        }
        return 1000;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!heldItem.isEmpty()) {
            tag.put("HeldItem", heldItem.save(registries));
        }
        if (!inDeck.isEmpty()) {
            tag.put("InDeck", inDeck.save(registries));
        }
        if (!outDeck.isEmpty()) {
            tag.put("OutDeck", outDeck.save(registries));
        }
        tag.put("Bringer", bringer.save(registries));
        tag.put("Taker", taker.save(registries));
        tag.putInt("Progress", ritualProgress);
        tag.putInt("ManualHits", manualHits);
        tag.putInt("Source", source);
        tag.putBoolean("IsCrafting", isCrafting);
        tag.putInt("Coolers", coolers);
        tag.putBoolean("RedstoneModule", redstoneModule);
        redstone.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        heldItem = tag.contains("HeldItem") ? ItemStack.parse(registries, tag.getCompound("HeldItem")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        inDeck = tag.contains("InDeck") ? ItemStack.parse(registries, tag.getCompound("InDeck")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        outDeck = tag.contains("OutDeck") ? ItemStack.parse(registries, tag.getCompound("OutDeck")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        bringer.load(tag.getCompound("Bringer"), registries);
        taker.load(tag.getCompound("Taker"), registries);
        this.ritualProgress = tag.getInt("Progress");
        this.manualHits = tag.getInt("ManualHits");
        this.source = tag.getInt("Source");
        this.isCrafting = tag.getBoolean("IsCrafting");
        this.coolers = tag.getInt("Coolers");
        this.redstoneModule = tag.getBoolean("RedstoneModule");
        redstone.load(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
