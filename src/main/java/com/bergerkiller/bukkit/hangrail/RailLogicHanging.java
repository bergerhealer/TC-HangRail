package com.bergerkiller.bukkit.hangrail;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicHorizontal;

public class RailLogicHanging extends RailLogicHorizontal {
    private final RailTypeHanging rail;

    public RailLogicHanging(BlockFace direction, RailTypeHanging rail) {
        super(direction);
        this.rail = rail;
    }

    @Override
    protected RailPath createPath() {
        Vector offset = new Vector(0.0, this.rail.getOffset(), 0.0);
        return RailPath.offset(super.createPath(), offset);
    }

}
