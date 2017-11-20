package com.bergerkiller.bukkit.hangrail;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicHorizontal;

public class RailLogicHanging extends RailLogicHorizontal {
    private final RailTypeHanging rail;

    public RailLogicHanging(BlockFace direction, RailTypeHanging rail) {
        super(direction);
        this.rail = rail;
    }

    @Override
    public void getFixedPosition(Vector position, IntVector3 railPos) {
        super.getFixedPosition(position, railPos);
        position.setY(position.getY() + (double) this.rail.getOffset());
    }

}
