package com.white.wirerider.content.toolbox;

import com.white.wirerider.WireRiderMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

import java.util.ArrayList;
import java.util.List;

public class ZiplineToolboxEntity extends Entity implements IEntityWithComplexSpawn {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK =
        SynchedEntityData.defineId(ZiplineToolboxEntity.class, EntityDataSerializers.ITEM_STACK);

    private final List<Vec3> points = new ArrayList<>();
    private double[] segmentLengths = new double[0];
    private double[] cumulativeLengths = new double[0];
    private double totalLength = 0.0;
    private double progress = 0.0;
    private boolean forward = true;
    private boolean reachedEnd = false;
    private int hangTimer = 0;

    public ZiplineToolboxEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.blocksBuilding = true;
    }

    public ZiplineToolboxEntity(Level level, List<Vec3> points, ItemStack item, Vec3 startPos, Vec3 lookDir) {
        this(WireRiderMod.ZIPLINE_TOOLBOX.get(), level);
        this.points.addAll(points);
        computeLengths();
        setItem(item);

        double bestDist = 0.0;
        double minDistSq = Double.MAX_VALUE;
        if (this.totalLength > 0.0) {
            for (int i = 0; i <= 60; i++) {
                double p = (this.totalLength * i) / 60.0;
                Vec3 v = getPositionAt(p);
                double dSq = v.distanceToSqr(startPos);
                if (dSq < minDistSq) {
                    minDistSq = dSq;
                    bestDist = p;
                }
            }
        }
        this.progress = bestDist;

        Vec3 tangent = getTangentAt(bestDist);
        if (lookDir != null && lookDir.lengthSqr() > 0.01) {
            double dot = lookDir.dot(tangent);
            this.forward = (dot >= 0);
        } else {
            this.forward = (tangent.y <= 0);
        }

        Vec3 wirePos = getPositionAt(this.progress);
        setPos(wirePos.x, wirePos.y - 0.42, wirePos.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM_STACK, ItemStack.EMPTY);
    }

    public ItemStack getItem() {
        return this.entityData.get(DATA_ITEM_STACK);
    }

    public void setItem(ItemStack stack) {
        this.entityData.set(DATA_ITEM_STACK, stack);
    }

    public double getProgress() {
        return progress;
    }

    public boolean isForward() {
        return forward;
    }

    public double getTotalLength() {
        return totalLength;
    }

    public void computeLengths() {
        if (points.size() < 2) {
            totalLength = 0;
            segmentLengths = new double[0];
            cumulativeLengths = new double[0];
            return;
        }
        int segCount = points.size() - 1;
        segmentLengths = new double[segCount];
        cumulativeLengths = new double[points.size()];
        cumulativeLengths[0] = 0.0;
        double sum = 0.0;
        for (int i = 0; i < segCount; i++) {
            double d = points.get(i).distanceTo(points.get(i + 1));
            segmentLengths[i] = d;
            sum += d;
            cumulativeLengths[i + 1] = sum;
        }
        totalLength = sum;
    }

    public Vec3 getPositionAt(double dist) {
        if (points.isEmpty()) return position();
        if (points.size() == 1 || dist <= 0.0) return points.get(0);
        if (dist >= totalLength) return points.get(points.size() - 1);

        int segIndex = 0;
        while (segIndex < segmentLengths.length - 1 && cumulativeLengths[segIndex + 1] < dist) {
            segIndex++;
        }
        double segLen = segmentLengths[segIndex];
        if (segLen <= 1.0E-6) return points.get(segIndex);
        double localDist = dist - cumulativeLengths[segIndex];
        double t = localDist / segLen;
        Vec3 pA = points.get(segIndex);
        Vec3 pB = points.get(segIndex + 1);
        return pA.lerp(pB, t);
    }

    public Vec3 getTangentAt(double dist) {
        if (points.size() < 2) return new Vec3(0, 0, 1);
        int segIndex = 0;
        while (segIndex < segmentLengths.length - 1 && cumulativeLengths[segIndex + 1] < dist) {
            segIndex++;
        }
        Vec3 pA = points.get(segIndex);
        Vec3 pB = points.get(segIndex + 1);
        Vec3 diff = pB.subtract(pA);
        if (diff.lengthSqr() < 1.0E-6) return new Vec3(0, 0, 1);
        return diff.normalize();
    }

    @Override
    public void tick() {
        super.tick();

        if (this.points.size() < 2 || this.totalLength <= 0.0) {
            if (!level().isClientSide()) {
                dropAndDiscard();
            }
            return;
        }

        if (!reachedEnd) {
            Vec3 tangent = getTangentAt(progress);
            double currentSpeed = 0.42;
            double slope = forward ? -tangent.y : tangent.y;
            if (slope > 0) {
                currentSpeed += slope * 0.45;
            }
            if (level().isRainingAt(blockPosition())) {
                currentSpeed += 0.08;
            }

            if (forward) {
                progress += currentSpeed;
                if (progress >= totalLength) {
                    progress = totalLength;
                    reachedEnd = true;
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.LANTERN_HIT, SoundSource.BLOCKS, 0.8F, 1.2F);
                }
            } else {
                progress -= currentSpeed;
                if (progress <= 0.0) {
                    progress = 0.0;
                    reachedEnd = true;
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.LANTERN_HIT, SoundSource.BLOCKS, 0.8F, 1.2F);
                }
            }

            if (tickCount % 5 == 0) {
                float pitch = 1.0F + (float) (currentSpeed * 0.35F);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.4F, pitch);
            }
        } else {
            hangTimer++;
            if (!level().isClientSide() && hangTimer == 1) {
                BlockPos below = blockPosition().below();
                BlockPos below2 = below.below();
                if (isContainerOrDeposit(below) || isContainerOrDeposit(below2)) {
                    dropAndDiscard();
                    return;
                }
            }
            if (!level().isClientSide() && hangTimer > 12000) {
                dropAndDiscard();
                return;
            }
        }

        Vec3 wirePos = getPositionAt(progress);
        setPos(wirePos.x, wirePos.y - 0.42, wirePos.z);
    }

    private boolean isContainerOrDeposit(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        if (state.isAir()) return false;
        if (state.getBlock() instanceof HopperBlock) return true;
        BlockEntity be = level().getBlockEntity(pos);
        if (be instanceof HopperBlockEntity || be instanceof Container) return true;
        if (be != null) {
            String name = be.getClass().getName();
            if (name.contains("Chute") || name.contains("Depot") || name.contains("Belt") || name.contains("Funnel")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide() && !isRemoved()) {
            dropAndDiscard();
            return true;
        }
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide()) {
            ItemStack item = getItem();
            if (!item.isEmpty()) {
                if (!player.getInventory().add(item)) {
                    player.drop(item, false);
                }
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            discard();
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    public void dropAndDiscard() {
        if (!level().isClientSide()) {
            ItemStack item = getItem();
            if (!item.isEmpty()) {
                ItemEntity entity = new ItemEntity(level(), getX(), getY(), getZ(), item);
                entity.setDefaultPickUpDelay();
                level().addFreshEntity(entity);
            }
        }
        discard();
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, getItem());
        buffer.writeVarInt(points.size());
        for (Vec3 p : points) {
            buffer.writeDouble(p.x);
            buffer.writeDouble(p.y);
            buffer.writeDouble(p.z);
        }
        buffer.writeDouble(progress);
        buffer.writeBoolean(forward);
        buffer.writeBoolean(reachedEnd);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        setItem(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        int size = buffer.readVarInt();
        points.clear();
        for (int i = 0; i < size; i++) {
            points.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        }
        computeLengths();
        progress = buffer.readDouble();
        forward = buffer.readBoolean();
        reachedEnd = buffer.readBoolean();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Item", 10)) {
            setItem(ItemStack.parseOptional(registryAccess(), tag.getCompound("Item")));
        }
        this.progress = tag.getDouble("Progress");
        this.forward = tag.getBoolean("Forward");
        this.reachedEnd = tag.getBoolean("ReachedEnd");
        this.hangTimer = tag.getInt("HangTimer");
        if (tag.contains("Points", 9)) {
            ListTag list = tag.getList("Points", 10);
            this.points.clear();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag pt = list.getCompound(i);
                this.points.add(new Vec3(pt.getDouble("x"), pt.getDouble("y"), pt.getDouble("z")));
            }
            computeLengths();
        }
        if (this.totalLength > 0.0) {
            Vec3 wirePos = getPositionAt(this.progress);
            setPos(wirePos.x, wirePos.y - 0.42, wirePos.z);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ItemStack item = getItem();
        if (!item.isEmpty()) {
            tag.put("Item", item.saveOptional(registryAccess()));
        }
        tag.putDouble("Progress", this.progress);
        tag.putBoolean("Forward", this.forward);
        tag.putBoolean("ReachedEnd", this.reachedEnd);
        tag.putInt("HangTimer", this.hangTimer);
        ListTag list = new ListTag();
        for (Vec3 p : this.points) {
            CompoundTag pt = new CompoundTag();
            pt.putDouble("x", p.x);
            pt.putDouble("y", p.y);
            pt.putDouble("z", p.z);
            list.add(pt);
        }
        tag.put("Points", list);
    }
}
