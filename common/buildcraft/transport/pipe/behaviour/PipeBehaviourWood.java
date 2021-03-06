package buildcraft.transport.pipe.behaviour;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.*;
import buildcraft.api.transport.pipe.IPipe.ConnectedType;

import buildcraft.lib.inventory.filter.StackFilter;
import buildcraft.transport.BCTransportConfig;

public class PipeBehaviourWood extends PipeBehaviourDirectional implements IMjRedstoneReceiver, IDebuggable {

    private final MjBattery mjBattery = new MjBattery(2 * MjAPI.MJ);

    public PipeBehaviourWood(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourWood(IPipe pipe, NBTTagCompound nbt) {
        super(pipe, nbt);
        mjBattery.deserializeNBT(nbt.getCompoundTag("mjBattery"));
    }

    @Override
    public NBTTagCompound writeToNbt() {
        NBTTagCompound nbt = super.writeToNbt();
        nbt.setTag("mjBattery", mjBattery.serializeNBT());
        return nbt;
    }

    @Override
    public int getTextureIndex(EnumFacing face) {
        return (face != null && face == getCurrentDir()) ? 1 : 0;
    }

    @Override
    public boolean canConnect(EnumFacing face, PipeBehaviour other) {
        return !(other instanceof PipeBehaviourWood);
    }

    @Override
    protected boolean canFaceDirection(EnumFacing dir) {
        return pipe.getConnectedType(dir) == ConnectedType.TILE;
    }

    @PipeEventHandler
    public void fluidSideCheck(PipeEventFluid.SideCheck sideCheck) {
        if (currentDir.face != null) {
            sideCheck.disallow(currentDir.face);
        }
    }

    @Override
    public void onTick() {
        if (pipe.getHolder().getPipeWorld().isRemote) {
            return;
        }

        if (currentDir == EnumPipePart.CENTER) {
            advanceFacing();
        } else if (!canFaceDirection(getCurrentDir())) {
            currentDir = EnumPipePart.CENTER;
        }

        mjBattery.tick(pipe.getHolder().getPipeWorld(), pipe.getHolder().getPipePos());
        long potential = mjBattery.getStored();
        if (potential > 0) {
            if (pipe.getFlow() instanceof IFlowItems) {
                IFlowItems flow = (IFlowItems) pipe.getFlow();
                int maxItems = (int) (potential / BCTransportConfig.mjPerItem);
                if (maxItems > 0) {
                    int extracted = extractItems(flow, getCurrentDir(), maxItems);
                    if (extracted > 0) {
                        mjBattery.extractPower(extracted * BCTransportConfig.mjPerItem);
                        return;
                    }
                }
            } else if (pipe.getFlow() instanceof IFlowFluid) {
                IFlowFluid flow = (IFlowFluid) pipe.getFlow();
                int maxMillibuckets = (int) (potential / BCTransportConfig.mjPerMillibucket);
                if (maxMillibuckets > 0) {
                    FluidStack extracted = extractFluid(flow, getCurrentDir(), maxMillibuckets);
                    if (extracted != null && extracted.amount > 0) {
                        mjBattery.extractPower(extracted.amount * BCTransportConfig.mjPerMillibucket);
                        return;
                    }
                }
            }
            mjBattery.extractPower(0, 5 * MjAPI.MJ / 100);
        }
    }

    protected int extractItems(IFlowItems flow, EnumFacing dir, int count) {
        return flow.tryExtractItems(count, dir, null, StackFilter.ALL);
    }

    protected FluidStack extractFluid(IFlowFluid flow, EnumFacing dir, int millibuckets) {
        return flow.tryExtractFluid(millibuckets, dir, null);
    }

    // IMjRedstoneReceiver

    @Override
    public boolean canConnect(IMjConnector other) {
        return true;
    }

    @Override
    public long getPowerRequested() {
        return MjAPI.MJ;
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        return mjBattery.addPowerChecking(microJoules, simulate);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return getCapability(capability, facing) != null;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == MjAPI.CAP_REDSTONE_RECEIVER) return (T) this;
        else if (capability == MjAPI.CAP_RECEIVER) return (T) this;
        else if (capability == MjAPI.CAP_CONNECTOR) return (T) this;
        return null;
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, EnumFacing side) {
        left.add("Power = " + mjBattery.getDebugString());
        left.add("Facing = " + currentDir);
    }
}
