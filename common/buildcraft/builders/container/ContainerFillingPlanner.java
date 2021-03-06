package buildcraft.builders.container;

import buildcraft.builders.addon.AddonFillingPlanner;
import buildcraft.builders.filling.IParameter;
import buildcraft.core.marker.volume.ClientVolumeBoxes;
import buildcraft.core.marker.volume.EnumAddonSlot;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.core.marker.volume.WorldSavedDataVolumeBoxes;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.misc.data.AutoId;
import buildcraft.lib.net.PacketBufferBC;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class ContainerFillingPlanner extends ContainerBC_Neptune {
    @AutoId
    public static int ID_DATA;

    public AddonFillingPlanner addon;
    public List<IParameter> parameters = new ArrayList<>();
    public boolean inverted;

    public ContainerFillingPlanner(EntityPlayer player) {
        super(player);
        Pair<VolumeBox, EnumAddonSlot> selectingBoxAndSlot = player.world.isRemote ?
                EnumAddonSlot.getSelectingBoxAndSlot(player, ClientVolumeBoxes.INSTANCE) :
                EnumAddonSlot.getSelectingBoxAndSlot(player, WorldSavedDataVolumeBoxes.get(player.world));
        addon = (AddonFillingPlanner) selectingBoxAndSlot.getLeft().addons.get(selectingBoxAndSlot.getRight());
        parameters.addAll(addon.parameters);
        inverted = addon.inverted;
    }

    public void sendDataToServer() {
        sendMessage(ID_DATA, buffer -> {
            buffer.writeInt(parameters.size());
            parameters.forEach(parameter -> IParameter.toBytes(buffer, parameter));
            buffer.writeBoolean(inverted);
        });
    }

    @Override
    public void readMessage(int id, PacketBufferBC buffer, Side side, MessageContext ctx) throws IOException {
        super.readMessage(id, buffer, side, ctx);
        if (side == Side.SERVER) {
            if (id == ID_DATA) {
                parameters.clear();
                IntStream.range(0, buffer.readInt()).mapToObj(i -> IParameter.fromBytes(buffer)).forEach(parameters::add);
                inverted = buffer.readBoolean();
                addon.parameters = parameters;
                addon.inverted = inverted;
                addon.markDirty();
                WorldSavedDataVolumeBoxes.get(player.world).markDirty();
            }
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
