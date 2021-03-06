/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.blocks.metal;

import blusunrize.immersiveengineering.api.ApiUtils;
import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.api.TargetingInfo;
import blusunrize.immersiveengineering.api.wires.*;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IAdvancedSelectionBounds;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IHasDummyBlocks;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IMirrorAble;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IStateBasedDirectional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.EnumProperty;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static blusunrize.immersiveengineering.api.wires.WireType.MV_CATEGORY;

public class TransformerTileEntity extends ImmersiveConnectableTileEntity implements IStateBasedDirectional, IMirrorAble,
		IHasDummyBlocks, IAdvancedSelectionBounds
{
	public static TileEntityType<TransformerTileEntity> TYPE;
	private static final int RIGHT_INDEX = 0;
	private static final int LEFT_INDEX = 1;
	private WireType leftType;
	private WireType rightType;
	public int dummy = 0;
	protected Set<String> acceptableLowerWires = ImmutableSet.of(WireType.LV_CATEGORY);

	public TransformerTileEntity()
	{
		super(TYPE);
	}

	public TransformerTileEntity(TileEntityType<? extends TransformerTileEntity> type)
	{
		super(type);
	}

	public static boolean _Immovable()
	{
		return true;
	}
	@Override
	public boolean canConnect()
	{
		return true;
	}

	@Override
	public void writeCustomNBT(CompoundNBT nbt, boolean descPacket)
	{
		super.writeCustomNBT(nbt, descPacket);
		if(leftType!=null)
			nbt.putString("leftType", leftType.getUniqueName());
		if(rightType!=null)
			nbt.putString("rightType", rightType.getUniqueName());
		nbt.putInt("dummy", dummy);
	}

	@Override
	public void readCustomNBT(CompoundNBT nbt, boolean descPacket)
	{
		super.readCustomNBT(nbt, descPacket);
		if(nbt.contains("leftType"))
			leftType = ApiUtils.getWireTypeFromNBT(nbt, "leftType");
		else
			leftType = null;
		if(nbt.contains("rightType"))
			rightType = ApiUtils.getWireTypeFromNBT(nbt, "rightType");
		else
			rightType = null;
		dummy = nbt.getInt("dummy");
	}

	@Override
	public BlockPos getConnectionMaster(WireType cableType, TargetingInfo target)
	{
		return getPos().add(0, -dummy, 0);
	}

	@Override
	public boolean canConnectCable(WireType cableType, ConnectionPoint target, Vec3i offset)
	{
		if(dummy==2)
		{
			TileEntity master = world.getTileEntity(getPos().add(0, -dummy, 0));
			return master instanceof TransformerTileEntity&&((TransformerTileEntity)master).canConnectCable(cableType, target,
					new Vec3i(0, 2, 0));
		}
		switch(target.getIndex())
		{
			case LEFT_INDEX:
				return canAttach(cableType, leftType, rightType);
			case RIGHT_INDEX:
				return canAttach(cableType, rightType, leftType);
		}
		return false;
	}

	private boolean canAttach(WireType toAttach, @Nullable WireType atConn, @Nullable WireType other)
	{
		if(atConn!=null)
			return false;
		String higherCat = getHigherWiretype();
		String attachCat = toAttach.getCategory();
		if(other==null)
			return higherCat.equals(attachCat)||acceptableLowerWires.contains(attachCat);
		boolean isHigher = higherCat.equals(toAttach.getCategory());
		boolean isOtherHigher = higherCat.equals(other.getCategory());
		if(isHigher^isOtherHigher)
		{
			if(isHigher)
				return true;
			else
				return acceptableLowerWires.contains(attachCat);
		}
		else
			return false;
	}

	@Override
	public void connectCable(WireType cableType, ConnectionPoint target, IImmersiveConnectable other, ConnectionPoint otherTarget)
	{
		if(dummy!=0)
		{
			TileEntity master = world.getTileEntity(getPos().add(0, -dummy, 0));
			if(master instanceof TransformerTileEntity)
				((TransformerTileEntity)master).connectCable(cableType, target, other, otherTarget);
			return;
		}
		switch(target.getIndex())
		{
			case LEFT_INDEX:
				this.leftType = cableType;
				break;
			case RIGHT_INDEX:
				this.rightType = cableType;
				break;
		}
		updateMirrorState();
	}

	@Override
	public void removeCable(Connection connection, ConnectionPoint attachedPoint)
	{
		WireType type = connection!=null?connection.type: null;
		if(type==null)
			leftType = rightType = null;
		else
		{
			switch(attachedPoint.getIndex())
			{
				case LEFT_INDEX:
					leftType = null;
					break;
				case RIGHT_INDEX:
					rightType = null;
					break;
			}
		}
		updateMirrorState();
		this.markContainingBlockForUpdate(null);
	}

	@Override
	public Vec3d getConnectionOffset(@Nonnull Connection con, ConnectionPoint here)
	{
		return getConnectionOffset(con, con.getEndFor(pos).getIndex()==RIGHT_INDEX);
	}

	private Vec3d getConnectionOffset(Connection con, boolean right)
	{
			double conRadius = con.type.getRenderDiameter()/2;
			double offset = getHigherWiretype().equals(con.type.getCategory())?getHigherOffset(): getLowerOffset();
			if(getFacing()==Direction.NORTH)
				return new Vec3d(right?.8125: .1875, 2+offset-conRadius, .5);
			if(getFacing()==Direction.SOUTH)
				return new Vec3d(right?.1875: .8125, 2+offset-conRadius, .5);
			if(getFacing()==Direction.WEST)
				return new Vec3d(.5, 2+offset-conRadius, right?.1875: .8125);
			if(getFacing()==Direction.EAST)
				return new Vec3d(.5, 2+offset-conRadius, right?.8125: .1875);
		return new Vec3d(.5, .5, .5);
	}

	@Nullable
	@Override
	public ConnectionPoint getTargetedPoint(TargetingInfo target, Vec3i offset)
	{
			if(offset.getY()!=2)
				return null;
			if(getFacing()==Direction.NORTH)
				if(target.hitX < .5)
					return new ConnectionPoint(pos, LEFT_INDEX);
				else
					return new ConnectionPoint(pos, RIGHT_INDEX);
			else if(getFacing()==Direction.SOUTH)
				if(target.hitX < .5)
					return new ConnectionPoint(pos, RIGHT_INDEX);
				else
					return new ConnectionPoint(pos, LEFT_INDEX);
			else if(getFacing()==Direction.WEST)
				if(target.hitZ < .5)
					return new ConnectionPoint(pos, RIGHT_INDEX);
				else
					return new ConnectionPoint(pos, LEFT_INDEX);
			else if(getFacing()==Direction.EAST)
				if(target.hitZ < .5)
					return new ConnectionPoint(pos, LEFT_INDEX);
				else
					return new ConnectionPoint(pos, RIGHT_INDEX);
		return null;
	}

	private void updateMirrorState()
	{
		if(dummy!=0)
		{
			TileEntity master = world.getTileEntity(pos.down(dummy));
			if(master instanceof TransformerTileEntity)
				((TransformerTileEntity)master).updateMirrorState();
		}
		else
		{
			if(rightType!=null||leftType!=null)
			{
				String higher = getHigherWiretype();
				boolean intendedState = (rightType!=null&&higher.equals(rightType.getCategory()))||
						(leftType!=null&&!higher.equals(leftType.getCategory()));
				setMirrored(intendedState);
			}
		}
	}

	@Override
	public EnumProperty<Direction> getFacingProperty()
	{
		return IEProperties.FACING_HORIZONTAL;
	}

	@Override
	public PlacementLimitation getFacingLimitation()
	{
		return PlacementLimitation.HORIZONTAL;
	}

	@Override
	public boolean mirrorFacingOnPlacement(LivingEntity placer)
	{
		return false;
	}

	@Override
	public boolean canHammerRotate(Direction side, Vec3d hit, LivingEntity entity)
	{
		return false;
	}

	@Override
	public boolean canRotate(Direction axis)
	{
		return false;
	}

	@Override
	public boolean isDummy()
	{
		return dummy!=0;
	}

	@Override
	public void placeDummies(BlockItemUseContext ctx, BlockState state)
	{
		state = state.with(IEProperties.MULTIBLOCKSLAVE, true);
		for(int i = 1; i <= 2; i++)
		{
			world.setBlockState(pos.add(0, i, 0), state);
			((TransformerTileEntity)world.getTileEntity(pos.add(0, i, 0))).dummy = i;
			((TransformerTileEntity)world.getTileEntity(pos.add(0, i, 0))).setFacing(this.getFacing());
		}
	}

	@Override
	public void breakDummies(BlockPos pos, BlockState state)
	{
		for(int i = 0; i <= 2; i++)
			world.removeBlock(getPos().add(0, -dummy, 0).add(0, i, 0), false);
	}

	@Override
	public float[] getBlockBounds()
	{
		if(dummy==2)
			return new float[]{getFacing().getAxis()==Axis.Z?0: .3125f, 0, getFacing().getAxis()==Axis.X?0: .3125f, getFacing().getAxis()==Axis.Z?1: .6875f, this instanceof TransformerHVTileEntity?.75f: .5625f, getFacing().getAxis()==Axis.X?1: .6875f};
		return null;
	}

	boolean cachedMirrored = false;
	private List<AxisAlignedBB> advSelectionBoxes = null;

	@Override
	public List<AxisAlignedBB> getAdvancedSelectionBounds()
	{
		boolean mirrored = getIsMirrored();
		if(dummy==2&&(advSelectionBoxes==null||cachedMirrored!=mirrored))
		{
			double offsetA = mirrored?getHigherOffset(): getLowerOffset();
			double offsetB = mirrored?getLowerOffset(): getHigherOffset();
			if(getFacing()==Direction.NORTH)
				advSelectionBoxes = Lists.newArrayList(new AxisAlignedBB(0, 0, .3125, .375, offsetB, .6875).offset(getPos().getX(), getPos().getY(), getPos().getZ()), new AxisAlignedBB(.625, 0, .3125, 1, offsetA, .6875).offset(getPos().getX(), getPos().getY(), getPos().getZ()));
			if(getFacing()==Direction.SOUTH)
				advSelectionBoxes = Lists.newArrayList(new AxisAlignedBB(0, 0, .3125, .375, offsetA, .6875).offset(getPos().getX(), getPos().getY(), getPos().getZ()), new AxisAlignedBB(.625, 0, .3125, 1, offsetB, .6875).offset(getPos().getX(), getPos().getY(), getPos().getZ()));
			if(getFacing()==Direction.WEST)
				advSelectionBoxes = Lists.newArrayList(new AxisAlignedBB(.3125, 0, 0, .6875, offsetA, .375).offset(getPos().getX(), getPos().getY(), getPos().getZ()), new AxisAlignedBB(.3125, 0, .625, .6875, offsetB, 1).offset(getPos().getX(), getPos().getY(), getPos().getZ()));
			if(getFacing()==Direction.EAST)
				advSelectionBoxes = Lists.newArrayList(new AxisAlignedBB(.3125, 0, 0, .6875, offsetB, .375).offset(getPos().getX(), getPos().getY(), getPos().getZ()), new AxisAlignedBB(.3125, 0, .625, .6875, offsetA, 1).offset(getPos().getX(), getPos().getY(), getPos().getZ()));
			cachedMirrored = mirrored;
		}
		else if(dummy!=2)
		{
			advSelectionBoxes = null;
		}
		return advSelectionBoxes;
	}

	@Override
	public Set<BlockPos> getIgnored(IImmersiveConnectable other)
	{
		return ImmutableSet.of(pos.up(2));
	}

	protected float getLowerOffset()
	{
		return .5F;
	}

	protected float getHigherOffset()
	{
		return .5625F;
	}

	public String getHigherWiretype()
	{
		return MV_CATEGORY;
	}

	@Override
	public Collection<ConnectionPoint> getConnectionPoints()
	{
		if(isDummy())
			return ImmutableList.of();
		else
			return ImmutableList.of(new ConnectionPoint(pos, RIGHT_INDEX), new ConnectionPoint(pos, LEFT_INDEX));
	}

	@Override
	public Iterable<? extends Connection> getInternalConnections()
	{
		if(isDummy())
			return ImmutableList.of();
		else
			return ImmutableList.of(new Connection(pos, LEFT_INDEX, RIGHT_INDEX));
	}
}