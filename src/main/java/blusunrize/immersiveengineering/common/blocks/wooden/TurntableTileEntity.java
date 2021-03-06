/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.blocks.wooden;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.common.blocks.IEBaseTileEntity;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IHammerInteraction;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.INeighbourChangeTile;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IStateBasedDirectional;
import blusunrize.immersiveengineering.common.util.RotationUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.EnumProperty;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class TurntableTileEntity extends IEBaseTileEntity implements IStateBasedDirectional, INeighbourChangeTile, IHammerInteraction
{
	public static TileEntityType<TurntableTileEntity> TYPE;
	private boolean redstone = false;
	public boolean invert = false;

	public TurntableTileEntity()
	{
		super(TYPE);
	}

	@Override
	public void readCustomNBT(CompoundNBT nbt, boolean descPacket)
	{
		redstone = nbt.getBoolean("redstone");
		invert = nbt.getBoolean("invert");
	}

	@Override
	public void writeCustomNBT(CompoundNBT nbt, boolean descPacket)
	{
		nbt.putBoolean("redstone", redstone);
		nbt.putBoolean("invert", invert);
	}

	@Override
	public void onNeighborBlockChange(BlockPos otherPos)
	{
		boolean r = this.world.isBlockPowered(pos);
		if(r!=this.redstone)
		{
			this.redstone = r;
			if(this.redstone)
			{
				BlockPos target = pos.offset(getFacing());
				RotationUtil.rotateBlock(this.world, target, invert);
			}
		}
	}

	@Override
	public PlacementLimitation getFacingLimitation()
	{
		return PlacementLimitation.PISTON_LIKE;
	}

	@Override
	public boolean mirrorFacingOnPlacement(LivingEntity placer)
	{
		return placer.isSneaking();
	}

	@Override
	public boolean canHammerRotate(Direction side, Vec3d hit, LivingEntity entity)
	{
		return !entity.isSneaking();
	}

	@Override
	public boolean canRotate(Direction axis)
	{
		return true;
	}

	@Override
	public boolean hammerUseSide(Direction side, PlayerEntity player, Vec3d hitVec)
	{
		if(player.isSneaking())
		{
			if(!world.isRemote)
			{
				invert = !invert;
				markDirty();
				world.addBlockEvent(getPos(), this.getBlockState().getBlock(), 254, 0);
			}
			return true;
		}
		return false;
	}

	@Override
	public EnumProperty<Direction> getFacingProperty()
	{
		return IEProperties.FACING_ALL;
	}
}