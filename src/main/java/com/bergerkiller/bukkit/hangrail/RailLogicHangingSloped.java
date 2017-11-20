package com.bergerkiller.bukkit.hangrail;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicSloped;

public class RailLogicHangingSloped extends RailLogicSloped {
    private final RailTypeHanging rail;

    public RailLogicHangingSloped(BlockFace direction, RailTypeHanging rail) {
        super(direction);
        this.rail = rail;
    }

    @Override
    public void getFixedPosition(Vector position, IntVector3 railPos) {
        super.getFixedPosition(position, railPos);
        if (this.rail.isBelowRail()) {
            position.setY(position.getY() + (double) this.rail.getOffset() - 1.0);
        } else {
            position.setY(position.getY() + (double) this.rail.getOffset());
        }
    }

}
