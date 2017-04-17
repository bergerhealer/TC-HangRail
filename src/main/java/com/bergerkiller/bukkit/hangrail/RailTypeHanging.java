package com.bergerkiller.bukkit.hangrail;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailTypeHorizontal;
import com.bergerkiller.bukkit.tc.rails.type.RailTypeRegular;

public class RailTypeHanging extends RailTypeHorizontal {

	@Override
	public boolean isRail(BlockData blockData) {
		return blockData.getType() == Material.IRON_FENCE;
	}

	@Override
	public Block findRail(Block pos) {
		if (isRail(pos.getWorld(), pos.getX(), pos.getY() + 2, pos.getZ())) {
			return pos.getRelative(0, 2, 0);
		}
		if (isRail(pos.getWorld(), pos.getX(), pos.getY() + 3, pos.getZ())) {
			Block rail = pos.getRelative(0, 3, 0);
			if (findSlope(rail) != null) {
				return rail;
			}
		}
		return null;
	}

	@Override
	public IntVector3 findRail(MinecartMember<?> member, World world, IntVector3 pos) {
		// Try to find the rail at the current position or one below
		if (isRail(world, pos.x, pos.y + 2, pos.z)) {
			return pos.add(BlockFace.UP, 2);
		}
		if (isRail(world, pos.x, pos.y + 3, pos.z)) {
			IntVector3 railPos = pos.add(BlockFace.UP, 3);
			if (findSlope(railPos.toBlock(world)) != null) {
				return railPos;
			}
		}
		return null;
	}

	@Override
	public Block findMinecartPos(Block trackBlock) {
	    return trackBlock.getRelative(0, -2, 0);
	}

	@Override
	public Block getNextPos(Block currentTrack, BlockFace currentDirection) {
		BlockFace sloped = findSlope(currentTrack);
		if (sloped != null) {
			return RailTypeRegular.getNextPos(currentTrack.getRelative(0, -3, 0), currentDirection, sloped, true);
		}
		BlockFace dir = getHorizontalDirection(currentTrack);
		if (dir == BlockFace.SELF) {
			dir = currentDirection;
		}
		return RailTypeRegular.getNextPos(currentTrack.getRelative(0, -2, 0), currentDirection, dir, false);
	}

	@Override
	public BlockFace[] getPossibleDirections(Block trackBlock) {
		return RailTypeRegular.getPossibleDirections(getDirection(trackBlock));
	}

	@Override
	public BlockFace getSignColumnDirection(Block railsBlock) {
		return BlockFace.UP;
	}

	@Override
	public BlockFace getDirection(Block railsBlock) {
		BlockFace sloped = findSlope(railsBlock);
		return sloped == null ? getHorizontalDirection(railsBlock) : sloped;
	}

	@Override
	public RailLogic getLogic(MinecartMember<?> member, Block railsBlock) {
		BlockFace sloped = findSlope(railsBlock);
		if (sloped != null) {
			return RailLogicHangingSloped.get(sloped);
		}
		// Check what sides have a connecting hanging rail
		BlockFace dir = getHorizontalDirection(railsBlock);
		if (dir == BlockFace.SELF) {
			// Use the Minecart direction to figure this one out
			// This is similar to the Crossing rail type
			dir = FaceUtil.toRailsDirection(member.getDirectionTo());
		}
		return RailLogicHanging.get(dir);
	}

	private BlockFace findSlope(Block railsBlock) {
		Block below = railsBlock.getRelative(BlockFace.DOWN);
		for (BlockFace face : FaceUtil.AXIS) {
			if (isRail(below, face)) {
				// Check that the block below is not blocked (pillar)
				if (!isRail(below.getRelative(face.getModX(), -1, face.getModZ()))) {
					return face.getOppositeFace();
				}
			}
		}
		return null;
	}

	private BlockFace getHorizontalDirection(Block railsBlock) {
		boolean north = isRail(railsBlock, BlockFace.NORTH);
		boolean east = isRail(railsBlock, BlockFace.EAST);
		boolean south = isRail(railsBlock, BlockFace.SOUTH);
		boolean west = isRail(railsBlock, BlockFace.WEST);
		// X-crossing: use direction we came from
		if (north && south && east && west) {
			return BlockFace.SELF;
		}
		// NORTH and SOUTH only
		if (north && south) {
			return BlockFace.NORTH;
		}
		// EAST and WEST only
		if (east && west) {
			return BlockFace.EAST;
		}
		// Along NORTH-EAST
		if (north && east) {
			return BlockFace.SOUTH_WEST;
		}
		// Along NORTH-WEST
		if (north && west) {
			return BlockFace.SOUTH_EAST;
		}
		// Along SOUTH-EAST
		if (south && east) {
			return BlockFace.NORTH_WEST;
		}
		// Along SOUTH-WEST
		if (south && west) {
			return BlockFace.NORTH_EAST;
		}
		// See if there is one possible neighbor
		if (north || south) {
			return BlockFace.NORTH;
		}
		if (east || west) {
			return BlockFace.EAST;
		}
		// No neighbors at all - stick to the direction we came from
		return BlockFace.SELF;
	}
}
