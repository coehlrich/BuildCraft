package buildcraft.factory.block;

import java.util.List;
import java.util.Map;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.factory.BCFactoryGuis;
import buildcraft.factory.tile.TileChute;
import buildcraft.lib.block.BlockBCTile_Neptune;
import buildcraft.lib.block.IBlockWithFacing;

public class BlockChute extends BlockBCTile_Neptune implements IBlockWithFacing {
    public static final Map<EnumFacing, IProperty<Boolean>> CONNECTED_MAP = BuildCraftProperties.CONNECTED_MAP;

    public BlockChute(Material material, String id) {
        super(material, id);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileChute();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            BCFactoryGuis.CHUTE.openGUI(player, pos);
        }
        return true;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    protected void addProperties(List<IProperty<?>> properties) {
        super.addProperties(properties);
        properties.addAll(CONNECTED_MAP.values());
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        for (EnumFacing side : EnumFacing.values()) {
            state = state.withProperty(CONNECTED_MAP.get(side), side != state.getValue(getFacingProperty()) && TileChute.hasInventoryAtPosition(world, pos.offset(side), side));
        }
        return state;
    }

    // IBlockWithFacing

    @Override
    public boolean canPlacedVertical() {
        return true;
    }
}
