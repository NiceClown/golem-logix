package com.golemlogix.integration.thaumaturge;

import com.golemlogix.blockentity.EssentiaCauldronBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Registers Thaumaturge {@link EssentiaCapabilities#TRANSPORT} and {@link EssentiaCapabilities#STORAGE}
 * for {@link ModBlockEntities#ESSENTIA_CAULDRON}. Only loaded when Thaumaturge is present.
 */
final class ThaumaturgeCapabilities {
    /** One shared transactional journal per cauldron BE so multi-side inserts in one Transaction revert atomically. */
    private static final WeakHashMap<EssentiaCauldronBlockEntity, SnapshotJournal<int[]>> JOURNALS = new WeakHashMap<>();

    private ThaumaturgeCapabilities() {
    }

    private static SnapshotJournal<int[]> journalFor(EssentiaCauldronBlockEntity be) {
        synchronized (JOURNALS) {
            return JOURNALS.computeIfAbsent(be, target -> {
                WeakReference<EssentiaCauldronBlockEntity> ref = new WeakReference<>(target);
                return new SnapshotJournal<>() {
                    @Override
                    protected int[] createSnapshot() {
                        EssentiaCauldronBlockEntity b = ref.get();
                        return b == null ? new int[]{0, 0} : new int[]{b.auram(), b.gelum()};
                    }

                    @Override
                    protected void revertToSnapshot(int[] snapshot) {
                        EssentiaCauldronBlockEntity b = ref.get();
                        if (b != null) {
                            b.restoreTanksRaw(snapshot[0], snapshot[1]);
                        }
                    }

                    @Override
                    protected void onRootCommit(int[] originalState) {
                        EssentiaCauldronBlockEntity b = ref.get();
                        if (b != null) {
                            b.onStorageCommitted();
                        }
                    }
                };
            });
        }
    }

    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, ModBlockEntities.ESSENTIA_CAULDRON.get(),
                (be, side) -> {
                    if (side != null && side != be.primarySide() && side != be.coolantSide()) {
                        return null;
                    }
                    return new CauldronTransport(be);
                });
        event.registerBlockEntity(EssentiaCapabilities.STORAGE, ModBlockEntities.ESSENTIA_CAULDRON.get(),
                (be, side) -> {
                    if (side != null && side != be.primarySide() && side != be.coolantSide()) {
                        return null;
                    }
                    return new CauldronStorage(be, side);
                });
    }

    /** Sided sink-only {@link IEssentiaTransport}: viewer's left sucks primary (Auram), right sucks coolant (Gelum). */
    private static final class CauldronTransport implements IEssentiaTransport {
        private final EssentiaCauldronBlockEntity be;

        private CauldronTransport(EssentiaCauldronBlockEntity be) {
            this.be = be;
        }

        @Override
        public boolean isConnectable(Direction face) {
            return face == be.primarySide() || face == be.coolantSide();
        }

        @Override
        public boolean canInputFrom(Direction face) {
            return isConnectable(face);
        }

        @Override
        public boolean canOutputTo(Direction face) {
            return false;
        }

        @Override
        public void setSuction(@Nullable Holder<IAspect> aspect, int amount) {
        }

        @Override
        @Nullable
        public Holder<IAspect> getSuctionType(Direction face) {
            Level level = be.getLevel();
            if (level == null || be.isRedstonePaused()) {
                return null;
            }
            if (face == be.primarySide() && be.primarySpace() > 0) {
                return ThaumaturgeBridge.primaryAspect(level);
            }
            if (face == be.coolantSide() && be.coolantSpace() > 0) {
                return ThaumaturgeBridge.coolantAspect(level);
            }
            return null;
        }

        @Override
        public int getSuctionAmount(Direction face) {
            if (be.getLevel() == null || be.isRedstonePaused()) {
                return 0;
            }
            if (face == be.primarySide() && be.primarySpace() > 0) {
                return ThaumaturgeBridge.CAULDRON_SUCTION;
            }
            if (face == be.coolantSide() && be.coolantSpace() > 0) {
                return ThaumaturgeBridge.CAULDRON_SUCTION;
            }
            return 0;
        }

        @Override
        public int takeEssentia(Holder<IAspect> aspect, int amount, Direction face) {
            return 0;
        }

        @Override
        public int addEssentia(Holder<IAspect> aspect, int amount, Direction face) {
            return addEssentia(aspect, amount, face, false);
        }

        @Override
        public int addEssentia(Holder<IAspect> aspect, int amount, Direction face, boolean simulate) {
            Level level = be.getLevel();
            if (level == null || aspect == null || amount <= 0 || be.isRedstonePaused()) {
                return 0;
            }
            if (face == be.primarySide() && ThaumaturgeBridge.isPrimary(level, aspect)) {
                return be.addPrimary(amount, simulate);
            }
            if (face == be.coolantSide() && ThaumaturgeBridge.isCoolant(level, aspect)) {
                return be.addCoolant(amount, simulate);
            }
            return 0;
        }

        @Override
        public int spaceFor(Holder<IAspect> aspect, Direction face) {
            return addEssentia(aspect, Integer.MAX_VALUE, face, true);
        }

        @Override
        @Nullable
        public Holder<IAspect> getEssentiaType(Direction face) {
            Level level = be.getLevel();
            if (level == null) {
                return null;
            }
            if (face == be.primarySide() && be.auram() > 0) {
                return ThaumaturgeBridge.primaryAspect(level);
            }
            if (face == be.coolantSide() && be.gelum() > 0) {
                return ThaumaturgeBridge.coolantAspect(level);
            }
            return null;
        }

        @Override
        public int getEssentiaAmount(Direction face) {
            if (face == be.primarySide()) {
                return be.auram();
            }
            if (face == be.coolantSide()) {
                return be.gelum();
            }
            return 0;
        }

        @Override
        public int getMinimumSuction() {
            return 0;
        }
    }

    /** Insert-only transactional {@link IEssentiaStorage} per side (or combined for null side). */
    private static final class CauldronStorage implements IEssentiaStorage {
        private final EssentiaCauldronBlockEntity be;
        @Nullable
        private final Direction side;
        private final SnapshotJournal<int[]> journal;

        private CauldronStorage(EssentiaCauldronBlockEntity be, @Nullable Direction side) {
            this.be = be;
            this.side = side;
            this.journal = journalFor(be);
        }

        @Override
        public AspectList contents() {
            Level level = be.getLevel();
            if (level == null) {
                return AspectList.EMPTY;
            }
            AspectList list = AspectList.EMPTY;
            if ((side == null || side == be.primarySide()) && be.auram() > 0) {
                Holder<IAspect> p = ThaumaturgeBridge.primaryAspect(level);
                if (p != null) {
                    list = list.add(p, be.auram());
                }
            }
            if ((side == null || side == be.coolantSide()) && be.gelum() > 0) {
                Holder<IAspect> c = ThaumaturgeBridge.coolantAspect(level);
                if (c != null) {
                    list = list.add(c, be.gelum());
                }
            }
            return list;
        }

        @Override
        public int insert(Holder<IAspect> aspect, int maxAmount, TransactionContext transaction) {
            Level level = be.getLevel();
            if (level == null || aspect == null || maxAmount <= 0 || be.isRedstonePaused()) {
                return 0;
            }
            boolean allowPrimary = side == null || side == be.primarySide();
            boolean allowCoolant = side == null || side == be.coolantSide();
            if (allowPrimary && ThaumaturgeBridge.isPrimary(level, aspect)) {
                int accept = Math.min(maxAmount, be.primarySpace());
                if (accept <= 0) {
                    return 0;
                }
                journal.updateSnapshots(transaction);
                be.restoreTanksRaw(be.auram() + accept, be.gelum());
                return accept;
            }
            if (allowCoolant && ThaumaturgeBridge.isCoolant(level, aspect)) {
                int accept = Math.min(maxAmount, be.coolantSpace());
                if (accept <= 0) {
                    return 0;
                }
                journal.updateSnapshots(transaction);
                be.restoreTanksRaw(be.auram(), be.gelum() + accept);
                return accept;
            }
            return 0;
        }

        @Override
        public int extract(Holder<IAspect> aspect, int maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public long contentRevision() {
            return be.contentRevision();
        }
    }
}
