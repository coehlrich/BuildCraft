/* Copyright (c) 2016 AlexIIL and the BuildCraft team
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. */
package buildcraft.core.block;

import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import buildcraft.api.enums.EnumDecoratedBlock;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.lib.block.BlockBCBase_Neptune;

public class BlockDecoration extends BlockBCBase_Neptune {
    public static final IProperty<EnumDecoratedBlock> DECORATED_TYPE = BuildCraftProperties.DECORATED_BLOCK;

    public BlockDecoration(String id) {
        super(Material.IRON, id);
        setDefaultState(getDefaultState().withProperty(DECORATED_TYPE, EnumDecoratedBlock.DESTROY));
    }

    // IBlockState

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, DECORATED_TYPE);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        IBlockState state = getDefaultState();
        return state.withProperty(DECORATED_TYPE, EnumDecoratedBlock.fromMeta(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(DECORATED_TYPE).ordinal();
    }

    // Other

    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List<ItemStack> list) {
        for (EnumDecoratedBlock type : EnumDecoratedBlock.values()) {
            list.add(new ItemStack(this, 1, type.ordinal()));
        }
    }

    @Override
    public int damageDropped(IBlockState state) {
        return state.getValue(DECORATED_TYPE).ordinal();
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumDecoratedBlock type = state.getValue(DECORATED_TYPE);
        return type.lightValue;
    }
}