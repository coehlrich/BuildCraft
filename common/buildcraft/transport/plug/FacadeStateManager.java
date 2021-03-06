package buildcraft.transport.plug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.BlockSlime;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import buildcraft.api.facades.FacadeType;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.ItemStackKey;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.net.PacketBufferBC;

import buildcraft.builders.snapshot.FakeWorld;

public class FacadeStateManager {
    public static final SortedMap<IBlockState, FacadeBlockStateInfo> validFacadeStates = new TreeMap<>(BlockUtil.blockStateComparator());
    public static final Map<ItemStackKey, List<FacadeBlockStateInfo>> stackFacades = new HashMap<>();
    public static FacadeBlockStateInfo defaultState, previewState;

    private static EnumActionResult isValidFacadeBlock(Block block) {
        if (block instanceof IFluidBlock || block instanceof BlockSlime) {
            return EnumActionResult.FAIL;
        }
        if (block instanceof BlockGlass || block instanceof BlockStainedGlass) {
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    private static boolean isValidFacadeState(IBlockState state) {
        if (state.getBlock().hasTileEntity(state)) {
            return false;
        }
        return state.isFullCube();
    }

    private static ItemStack getRequiredStack(IBlockState state) {
        FakeWorld world = FakeWorld.INSTANCE;
        world.clear();
        world.setBlockState(BlockPos.ORIGIN, state);
        ItemStack stack = ItemStack.EMPTY;
        try {
            stack = state.getBlock().getPickBlock(state, new RayTraceResult(VecUtil.VEC_HALF, null, BlockPos.ORIGIN), world, BlockPos.ORIGIN, null);
        } catch (Exception ignored) {
            /* Some mods require a non-null player, but we don't have one to give. If a mod's block does require a
             * player entity then we won't support it, as it may require a different stack depending on the player. */
        }
        world.clear();
        if (stack.isEmpty()) {
            return StackUtil.EMPTY;
        } else {
            return stack;
        }
    }

    public static void postInit() {
        defaultState = new FacadeBlockStateInfo(Blocks.AIR.getDefaultState(), StackUtil.EMPTY, ImmutableSet.of());
        for (Block block : ForgeRegistries.BLOCKS) {
            EnumActionResult result = isValidFacadeBlock(block);
            if (result == EnumActionResult.FAIL) {
                continue;
            }
            Map<IBlockState, ItemStack> usedStates = new HashMap<>();
            Map<ItemStackKey, Map<IProperty<?>, Comparable<?>>> varyingProperties = new HashMap<>();
            for (IBlockState state : block.getBlockState().getValidStates()) {
                state = block.getStateFromMeta(block.getMetaFromState(state));
                if (usedStates.containsKey(state)) {
                    continue;
                }
                if (result == EnumActionResult.PASS && !isValidFacadeState(state)) {
                    continue;
                }
                ItemStack stack = getRequiredStack(state);
                usedStates.put(state, stack);
                ItemStackKey stackKey = new ItemStackKey(stack);
                Map<IProperty<?>, Comparable<?>> vars = varyingProperties.get(stackKey);
                if (vars == null) {
                    vars = new HashMap<>();
                    vars.putAll(state.getProperties());
                    varyingProperties.put(stackKey, vars);
                } else {
                    for (Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
                        IProperty prop = entry.getKey();
                        Comparable<?> value = entry.getValue();
                        if (vars.get(prop) != value) {
                            vars.put(prop, null);
                        }
                    }
                }
            }
            for (Entry<IBlockState, ItemStack> entry : usedStates.entrySet()) {
                IBlockState state = entry.getKey();
                ItemStack stack = entry.getValue();
                Map<IProperty<?>, Comparable<?>> vars = varyingProperties.get(new ItemStackKey(stack));
                vars.values().removeIf(Objects::nonNull);
                FacadeBlockStateInfo info = new FacadeBlockStateInfo(state, stack, ImmutableSet.copyOf(vars.keySet()));
                validFacadeStates.put(state, info);
                if (!info.requiredStack.isEmpty()) {
                    ItemStackKey stackKey = new ItemStackKey(info.requiredStack);
                    stackFacades.computeIfAbsent(stackKey, k -> new ArrayList<>()).add(info);
                }
            }
        }
        previewState = validFacadeStates.get(Blocks.BRICK_BLOCK.getDefaultState());
    }

    public static class FacadeBlockStateInfo {
        public final IBlockState state;
        public final ItemStack requiredStack;
        public final ImmutableSet<IProperty<?>> varyingProperties;
        public final boolean isTransparent;
        public final boolean isVisible;
        public final boolean[] isSideSolid = new boolean[6];

        public FacadeBlockStateInfo(IBlockState state, ItemStack requiredStack, ImmutableSet<IProperty<?>> varyingProperties) {
            this.state = state;
            this.requiredStack = requiredStack;
            this.varyingProperties = varyingProperties;
            this.isTransparent = !state.isOpaqueCube();
            this.isVisible = !requiredStack.isEmpty();
            FakeWorld world = FakeWorld.INSTANCE;
            world.clear();
            world.setBlockState(BlockPos.ORIGIN, state);
            for (EnumFacing side : EnumFacing.VALUES) {
                isSideSolid[side.ordinal()] = state.isSideSolid(world, BlockPos.ORIGIN, side);
            }
        }
    }

    public static class FacadePhasedState {
        public final FacadeBlockStateInfo stateInfo;
        public final boolean isHollow;
        @Nullable
        public final EnumDyeColor activeColour;

        public FacadePhasedState(FacadeBlockStateInfo stateInfo, boolean isHollow, EnumDyeColor activeColour) {
            this.stateInfo = stateInfo;
            this.isHollow = isHollow;
            this.activeColour = activeColour;
        }

        public static FacadePhasedState readFromNbt(NBTTagCompound nbt) {
            FacadeBlockStateInfo stateInfo = defaultState;
            if (nbt.hasKey("state")) {
                try {
                    IBlockState blockState = NBTUtil.readBlockState(nbt.getCompoundTag("state"));
                    stateInfo = validFacadeStates.get(blockState);
                    if (stateInfo == null) {
                        stateInfo = defaultState;
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            boolean isHollow = nbt.getBoolean("isHollow");
            EnumDyeColor colour = NBTUtilBC.readEnum(nbt.getTag("activeColour"), EnumDyeColor.class);
            return new FacadePhasedState(stateInfo, isHollow, colour);
        }

        public NBTTagCompound writeToNbt() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setTag("state", NBTUtil.writeBlockState(new NBTTagCompound(), stateInfo.state));
            nbt.setBoolean("isHollow", isHollow);
            if (activeColour != null) {
                nbt.setTag("activeColour", NBTUtilBC.writeEnum(activeColour));
            }
            return nbt;
        }

        public static FacadePhasedState readFromBuffer(PacketBufferBC buf) {
            IBlockState state = MessageUtil.readBlockState(buf);
            boolean isHollow = buf.readBoolean();
            EnumDyeColor colour = MessageUtil.readEnumOrNull(buf, EnumDyeColor.class);
            FacadeBlockStateInfo info = validFacadeStates.get(state);
            if (info == null) {
                info = defaultState;
            }
            return new FacadePhasedState(info, isHollow, colour);
        }

        public void writeToBuffer(PacketBufferBC buf) {
            MessageUtil.writeBlockState(buf, stateInfo.state);
            buf.writeBoolean(isHollow);
            MessageUtil.writeEnumOrNull(buf, activeColour);
        }

        public FacadePhasedState withSwappedIsHollow() {
            return new FacadePhasedState(stateInfo, !isHollow, activeColour);
        }

        public FacadePhasedState withColour(EnumDyeColor colour) {
            return new FacadePhasedState(stateInfo, isHollow, colour);
        }

        public boolean isSideSolid(EnumFacing side) {
            return stateInfo.isSideSolid[side.ordinal()];
        }
    }

    public static class FullFacadeInstance {
        public final FacadePhasedState[] phasedStates;
        public final FacadeType type;

        public FullFacadeInstance(FacadePhasedState[] phasedStates) {
            if (phasedStates == null) throw new NullPointerException("phasedStates");
            if (phasedStates.length == 0) throw new IllegalArgumentException("phasedStates.length was 0");
            // Maximum of 17 states - 16 for each colour, 1 for no colour
            if (phasedStates.length > 17) throw new IllegalArgumentException("phasedStates.length was > 17");
            this.phasedStates = phasedStates;
            if (phasedStates.length == 1) {
                type = FacadeType.Basic;
            } else {
                type = FacadeType.Phased;
            }
        }

        public static FullFacadeInstance createSingle(FacadeBlockStateInfo info, boolean isHollow) {
            return new FullFacadeInstance(new FacadePhasedState[] { new FacadePhasedState(info, isHollow, null) });
        }

        public static FullFacadeInstance readFromNbt(NBTTagCompound nbt, String subTag) {
            NBTTagList list = nbt.getTagList(subTag, Constants.NBT.TAG_COMPOUND);
            if (list.hasNoTags()) {
                return new FullFacadeInstance(new FacadePhasedState[] { new FacadePhasedState(defaultState, false, null) });
            }
            FacadePhasedState[] states = new FacadePhasedState[list.tagCount()];
            for (int i = 0; i < list.tagCount(); i++) {
                states[i] = FacadePhasedState.readFromNbt(list.getCompoundTagAt(i));
            }
            return new FullFacadeInstance(states);
        }

        public void writeToNbt(NBTTagCompound nbt, String subTag) {
            NBTTagList list = new NBTTagList();
            for (FacadePhasedState state : phasedStates) {
                list.appendTag(state.writeToNbt());
            }
            nbt.setTag(subTag, list);
        }

        public static FullFacadeInstance readFromBuffer(PacketBufferBC buf) {
            int count = buf.readFixedBits(5);
            FacadePhasedState[] states = new FacadePhasedState[count];
            for (int i = 0; i < count; i++) {
                states[i] = FacadePhasedState.readFromBuffer(buf);
            }
            return new FullFacadeInstance(states);
        }

        public void writeToBuffer(PacketBufferBC buf) {
            buf.writeFixedBits(phasedStates.length, 5);
            for (FacadePhasedState phasedState : phasedStates) {
                phasedState.writeToBuffer(buf);
            }
        }

        public boolean canAddColour(EnumDyeColor colour) {
            for (FacadePhasedState state : phasedStates) {
                if (state.activeColour == colour) {
                    return false;
                }
            }
            return true;
        }

        @Nullable
        public FullFacadeInstance withState(FacadePhasedState state) {
            if (canAddColour(state.activeColour)) {
                FacadePhasedState[] newStates = Arrays.copyOf(phasedStates, phasedStates.length + 1);
                newStates[newStates.length - 1] = state;
                return new FullFacadeInstance(newStates);
            } else {
                return null;
            }
        }

        public FacadePhasedState getCurrentStateForStack() {
            int count = phasedStates.length;
            if (count == 1) {
                return phasedStates[0];
            } else {
                int now = (int) (System.currentTimeMillis() % 100_000);
                return phasedStates[(now / 500) % count];
            }
        }

        public FullFacadeInstance withSwappedIsHollow() {
            FacadePhasedState[] newStates = Arrays.copyOf(phasedStates, phasedStates.length);
            for (int i = 0; i < newStates.length; i++) {
                newStates[i] = newStates[i].withSwappedIsHollow();
            }
            return new FullFacadeInstance(newStates);
        }

        public boolean areAllStatesSolid(EnumFacing side) {
            for (FacadePhasedState state : phasedStates) {
                if (!state.isSideSolid(side)) {
                    return false;
                }
            }
            return true;
        }
    }
}
