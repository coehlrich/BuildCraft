package buildcraft.transport.wire;

import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pipe.IPipeHolder;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

public class MessageWireSystemsPowered implements IMessage {
    private Map<Integer, Boolean> hashesPowered = new HashMap<>();

    public MessageWireSystemsPowered() {
    }

    public MessageWireSystemsPowered(Map<Integer, Boolean> hashesPowered) {
        this.hashesPowered = hashesPowered;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(hashesPowered.size());
        hashesPowered.forEach((wiresHashCode, powered) -> {
            buf.writeInt(wiresHashCode);
            buf.writeBoolean(powered);
        });
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hashesPowered.clear();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            hashesPowered.put(buf.readInt(), buf.readBoolean());
        }
    }

    public static final IMessageHandler<MessageWireSystemsPowered, IMessage> HANDLER = (message, ctx) -> {
        message.hashesPowered.entrySet().stream()
                .map(hashPowered ->
                        Pair.of(
                                ClientWireSystems.INSTANCE.wireSystems.get(hashPowered.getKey()),
                                hashPowered.getValue()
                        )
                )
                .flatMap(systemPowered ->
                        systemPowered.getLeft().elements.stream()
                                .map(element ->
                                        Pair.of(element, systemPowered.getRight())
                                )
                )
                .forEach(elementPowered -> {
                    WireSystem.WireElement element = elementPowered.getLeft();
                    boolean powered = elementPowered.getRight();
                    if (element.type == WireSystem.WireElement.Type.WIRE_PART) {
                        TileEntity tile = Minecraft.getMinecraft().world.getTileEntity(element.blockPos);
                        if (tile instanceof IPipeHolder) {
                            IPipeHolder holder = (IPipeHolder) tile;
                            IWireManager iWireManager = holder.getWireManager();
                            if (iWireManager instanceof WireManager) {
                                WireManager wireManager = (WireManager) iWireManager;
                                if (wireManager.getColorOfPart(element.wirePart) != null) {
                                    if (powered) {
                                        wireManager.poweredClient.add(element.wirePart);
                                    } else {
                                        wireManager.poweredClient.remove(element.wirePart);
                                    }
                                }
                            }
                        }
                    }
                });
        return null;
    };
}
