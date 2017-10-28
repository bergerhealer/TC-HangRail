package com.bergerkiller.bukkit.hangrail;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicSloped;

public class RailLogicHangingSloped extends RailLogicSloped {
    private final RailTypeHanging rail;

    public RailLogicHangingSloped(BlockFace direction, RailTypeHanging rail) {
        super(direction);
        this.rail = rail;
    }

    @Override
    public Vector getFixedPosition(CommonMinecart<?> entity, double x, double y, double z, IntVector3 railPos) {
        Vector pos = super.getFixedPosition(entity, x, y, z, railPos);
        if (this.rail.isBelowRail()) {
            pos.setY(pos.getY() + (double) this.rail.getOffset() - 1.0);
        } else {
            pos.setY(pos.getY() + (double) this.rail.getOffset());
        }
        return pos;
    }

}
