package com.bergerkiller.bukkit.hangrail;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicHorizontal;

public class RailLogicHanging extends RailLogicHorizontal {
    private final RailTypeHanging rail;

    public RailLogicHanging(BlockFace direction, RailTypeHanging rail) {
        super(direction);
        this.rail = rail;
    }

    @Override
    public Vector getFixedPosition(CommonMinecart<?> entity, double x, double y, double z, IntVector3 railPos) {
        Vector pos = super.getFixedPosition(entity, x, y, z, railPos);
        pos.setY(pos.getY() + (double) this.rail.getOffset());
        return pos;
    }

}
