package com.bergerkiller.bukkit.hangrail;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.collections.BlockFaceSet;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailTypeHorizontal;

public class RailTypeHanging extends RailTypeHorizontal {
    private final JunctionStateMap junctionMap;
    private final ItemParser typeInfo;
    private final int offset;
    private final int signOffset;
    private final BlockFace signDirection;
    private final RailLogicHangingSloped[] logic_sloped;
    private final RailLogicHanging[] logic_horizontal;

    public RailTypeHanging(JunctionStateMap junctionMap, ItemParser typeInfo, int offset, int signOffset, BlockFace signDirection) {
        this.junctionMap = junctionMap;
        this.typeInfo = typeInfo;
        this.offset = offset;
        this.signOffset = signOffset;
        if (signDirection == BlockFace.SELF) {
            this.signDirection = this.isBelowRail() ? BlockFace.UP : BlockFace.DOWN;
        } else {
            this.signDirection = signDirection;
        }

        this.logic_sloped = new RailLogicHangingSloped[4];
        for (int i = 0; i < 4; i++) {
            this.logic_sloped[i] = new RailLogicHangingSloped(FaceUtil.notchToFace(i << 1), this);
        }

        this.logic_horizontal = new RailLogicHanging[8];
        for (int i = 0; i < 8; i++) {
            this.logic_horizontal[i] = new RailLogicHanging(FaceUtil.notchToFace(i), this);
        }
    }

    /**
     * Gets whether the below-rail logic should be executed (offset < 0)
     * 
     * @return True if minecart is below rail
     */
    public boolean isBelowRail() {
        return this.offset < 0;
    }

    /**
     * Gets the block offset above/below this rail the Minecart is positioned
     * 
     * @return offset
     */
    public int getOffset() {
        return this.offset;
    }

    /**
     * Gets the hanging rail logic to go into the direction specified
     * 
     * @param direction to go to
     * @return Horizontal rail logic for that direction
     */
    public RailLogicHanging getLogicHorizontal(BlockFace direction) {
        return this.logic_horizontal[FaceUtil.faceToNotch(direction)];
    }

    /**
     * Gets the sloped hanging rail logic to go into the direction specified
     * 
     * @param direction to go to
     * @return Sloped hanging rail logic for that direction
     */
    public RailLogicHangingSloped getLogicSloped(BlockFace direction) {
        return this.logic_sloped[FaceUtil.faceToNotch(direction) >> 1];
    }

    @Override
    public boolean isRail(BlockData blockData) {
        return typeInfo.match(blockData);
    }

    @Override
    public Block findRail(Block pos) {
        if (this.isBelowRail()) {
            // Minecart is below the rails, then the block most down has priority
            if (isRail(pos.getWorld(), pos.getX(), pos.getY() - this.offset, pos.getZ())) {
                return pos.getRelative(0, -this.offset, 0);
            }

            // Now check the rail one higher (slopes)
            if (isRail(pos.getWorld(), pos.getX(), pos.getY() - this.offset + 1, pos.getZ())) {
                Block rail = pos.getRelative(0, -this.offset + 1, 0);
                if (findSlope(rail) != null) {
                    return rail;
                }
            }
        } else {
            // Now check the rails at the offset position (horizontal)
            if (isRail(pos.getWorld(), pos.getX(), pos.getY() - this.offset, pos.getZ())) {
                Block rail = pos.getRelative(0, -this.offset, 0);
                if (!isRail(rail, BlockFace.UP)) {
                    return rail;
                }
            }

            // Minecart is above the rails, then the block most up has priority (slope)
            if (isRail(pos.getWorld(), pos.getX(), pos.getY() - this.offset - 1, pos.getZ())) {
                return pos.getRelative(0, -this.offset - 1, 0);
            }
        }
        return null;
    }

    @Override
    public Block findMinecartPos(Block trackBlock) {
        return trackBlock.getRelative(0, this.offset, 0);
    }

    @Override
    public BlockFace getSignColumnDirection(Block railsBlock) {
        return this.signDirection;
    }

    @Override
    public Block getSignColumnStart(Block railsBlock) {
        if (this.signOffset == 0) {
            return railsBlock;
        } else {
            return railsBlock.getRelative(0, this.signOffset, 0);
        }
    }

    @Override
    public BlockFace getDirection(Block railsBlock) {
        BlockFace sloped = findSlope(railsBlock);
        return sloped == null ? getHorizontalDirection(railsBlock, BlockFace.SELF) : sloped;
    }

    @Override
    public BlockFace[] getPossibleDirections(Block trackBlock) {
        return this.findPossibleJunctionFaces(trackBlock).getFaces();
    }

    @Override
    public List<RailJunction> getJunctions(Block railBlock) {
        // For slopes we also use the logic path to find the two ends
        // This generally will never be reached, so whatever
        BlockFace slopeFace = this.findSlope(railBlock);
        if (slopeFace != null) {
            RailPath slopedPath = this.getLogicSloped(slopeFace).getPath();
            if (slopeFace == BlockFace.NORTH) {
                slopeFace = BlockFace.SOUTH;
            } else if (slopeFace == BlockFace.WEST) {
                slopeFace = BlockFace.EAST;
            }

            List<RailJunction> result = new ArrayList<>(2);
            result.add(new RailJunction(faceToJuncName(slopeFace), slopedPath.getEndPosition()));
            result.add(new RailJunction(faceToJuncName(slopeFace.getOppositeFace()), slopedPath.getStartPosition()));
            return result;
        }

        // Retrieve known possible directions and turn them into junctions
        BlockFaceSet faceSet = this.findPossibleJunctionFaces(railBlock);
        List<RailJunction> result = new ArrayList<>(faceSet.getFaces().length);
        if (faceSet.north()) result.add(new RailJunction("n", getLogicHorizontal(BlockFace.NORTH).getPath().getStartPosition()));
        if (faceSet.east()) result.add(new RailJunction("e", getLogicHorizontal(BlockFace.EAST).getPath().getEndPosition()));
        if (faceSet.south()) result.add(new RailJunction("s", getLogicHorizontal(BlockFace.NORTH).getPath().getEndPosition()));
        if (faceSet.west()) result.add(new RailJunction("w", getLogicHorizontal(BlockFace.EAST).getPath().getStartPosition()));
        return result;
    }

    @Override
    public void switchJunction(Block railBlock, RailJunction fromJunc, RailJunction toJunc) {
        junctionMap.set(railBlock, juncToFace(fromJunc), juncToFace(toJunc));
    }

    private static final BlockFace juncToFace(RailJunction junction) {
        switch (junction.name()) {
        case "n":
            return BlockFace.NORTH;
        case "e":
            return BlockFace.EAST;
        case "s":
            return BlockFace.SOUTH;
        case "w":
            return BlockFace.WEST;
        default:
            return BlockFace.NORTH;
        }
    }

    private static final String faceToJuncName(BlockFace face) {
        switch (face) {
        case NORTH:
            return "n";
        case EAST:
            return "e";
        case SOUTH:
            return "s";
        case WEST:
            return "w";
        default:
            return "1";
        }
    }

    @Override
    public RailLogic getLogic(MinecartMember<?> member, Block railsBlock, BlockFace direction) {
        BlockFace sloped = findSlope(railsBlock);
        if (sloped != null) {
            return this.getLogicSloped(sloped);
        }
        // Check what sides have a connecting hanging rail
        BlockFace dir = getHorizontalDirection(railsBlock, direction);
        if (dir == BlockFace.SELF) {
            // Use the Minecart direction to figure this one out
            // This is similar to the Crossing rail type
            dir = FaceUtil.toRailsDirection(direction);
        }
        return this.getLogicHorizontal(dir);
    }

    private BlockFace findSlope(Block railsBlock) {
        if (this.isBelowRail()) {
            // Minecart is below the rails ('hang rail')
            // Do sloped logic specific to that, here
            Block below = railsBlock.getRelative(BlockFace.DOWN);
            for (BlockFace face : FaceUtil.AXIS) {
                if (isRail(below, face)) {
                    // Check that the block below is not blocked (pillar)
                    if (!isRailVert(below, face, -1)) {
                        return face.getOppositeFace();
                    }
                }
            }
        } else {
            // Minecart is above the rails ('floating rail')
            // Do sloped logic specific to that, here.
            Block above = railsBlock.getRelative(BlockFace.UP);
            for (BlockFace face : FaceUtil.AXIS) {
                if (isRail(above, face)) {
                    // Check that the block above is not blocked (pillar)
                    if (!isRailVert(above, face, 1)) {
                        return face;
                    }
                }
            }
        }
        return null;
    }

    private final boolean isRailVert(Block block, BlockFace offset, int dy) {
        return isRail(block.getWorld(),
                block.getX() + offset.getModX(),
                block.getY() + dy,
                block.getZ() + offset.getModZ());
    }

    private BlockFaceSet findPossibleJunctionFaces(Block railsBlock) {
        BlockFaceSet result = BlockFaceSet.NONE;
        result = result.setNorth(isRail(railsBlock, BlockFace.NORTH));
        result = result.setEast(isRail(railsBlock, BlockFace.EAST));
        result = result.setSouth(isRail(railsBlock, BlockFace.SOUTH));
        result = result.setWest(isRail(railsBlock, BlockFace.WEST));

        // Curve towards a downwards or upwards slope in a T-split
        boolean north_south = result.north() || result.south();
        boolean east_west = result.east() || result.west();
        if (north_south != east_west) {
            if (isBelowRail()) {
                // Rails block is bottommost block, check above
                if (north_south) {
                    result = result.setEast(isRailVert(railsBlock, BlockFace.EAST, 1));
                    result = result.setWest(isRailVert(railsBlock, BlockFace.WEST, 1));
                } else {
                    result = result.setNorth(isRailVert(railsBlock, BlockFace.NORTH, 1));
                    result = result.setSouth(isRailVert(railsBlock, BlockFace.SOUTH, 1));
                }
            } else {
                // Rails block is topmost block, check below
                if (north_south) {
                    result = result.setEast(isRailVert(railsBlock, BlockFace.EAST, -1));
                    result = result.setWest(isRailVert(railsBlock, BlockFace.WEST, -1));
                } else {
                    result = result.setNorth(isRailVert(railsBlock, BlockFace.NORTH, -1));
                    result = result.setSouth(isRailVert(railsBlock, BlockFace.SOUTH, -1));
                }
            }
        }
        return result;
    }

    private BlockFace getHorizontalDirection(Block railsBlock, BlockFace movingDirection) {
        BlockFaceSet faceSet = findPossibleJunctionFaces(railsBlock);
        BlockFace[] faces = faceSet.getFaces();

        // Only one possible direction, go straight (north or east)
        if (faces.length == 1) {
            switch (faces[0]) {
            case NORTH:
            case SOUTH:
                return BlockFace.NORTH;
            case EAST:
            case WEST:
                return BlockFace.EAST;
            default:
                return faces[0]; // Never reached
            }
        }

        // By default go north or east if such a junction is encountered
        BlockFace defaultDirection;
        if (faces.length == 4) {
            defaultDirection = BlockFace.SELF;
        } else if (faceSet.north() && faceSet.south()) {
            defaultDirection = BlockFace.NORTH;
        } else if (faceSet.east() && faceSet.west()) {
            defaultDirection = BlockFace.EAST;
        } else {
            defaultDirection = BlockFace.SELF; // Weird. Shouldn't happen.
        }

        // Only two possible directions, follow whatever this direction is
        if (faces.length == 2) {
            if (defaultDirection != BlockFace.SELF) {
                // north + south -> south
                return defaultDirection;
            } else {
                // north + west -> south_east
                return FaceUtil.combine(faces[0], faces[1]).getOppositeFace();
            }
        }

        // Ask junction map what direction to take here
        JunctionStateMap.State junctionState = junctionMap.get(railsBlock);

        // Verify both from and to are valid directions to take still
        // The blocks may have changed since then
        if (junctionState != null && (!faceSet.get(junctionState.from) || !faceSet.get(junctionState.to))) {
            junctionState = null;
            junctionMap.remove(railsBlock);
        }

        // Instead of using the default direction, pick what's been set here.
        if (junctionState != null) {
            if (junctionState.from == junctionState.to.getOppositeFace()) {
                // Straight track. Make sure it's either EAST or NORTH.
                defaultDirection = junctionState.to;
                if (defaultDirection == BlockFace.WEST || defaultDirection == BlockFace.SOUTH) {
                    defaultDirection = junctionState.from;
                }
            } else {
                // Curved track. Combine and done.
                return FaceUtil.combine(junctionState.from, junctionState.to).getOppositeFace();
            }
        }

        // When taking a T-junction from the edge, returns an appropriate sub-
        // cardinal direction.
        if (defaultDirection == BlockFace.EAST) {
            if (movingDirection == BlockFace.NORTH) {
                return BlockFace.NORTH_WEST;
            } else if (movingDirection == BlockFace.SOUTH) {
                return BlockFace.SOUTH_WEST;
            }
        } else if (defaultDirection == BlockFace.NORTH) {
            if (movingDirection == BlockFace.EAST) {
                return BlockFace.SOUTH_EAST;
            } else if (movingDirection == BlockFace.WEST) {
                return BlockFace.SOUTH_WEST;
            }
        }
        return defaultDirection;
    }
}
