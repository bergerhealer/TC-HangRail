package com.bergerkiller.bukkit.hangrail;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

public class TCHangRail extends JavaPlugin {
    private final List<RailTypeHanging> hangingTypes = new ArrayList<RailTypeHanging>();

    @Override
    public void onEnable() {
        // Load rail type configuration
        FileConfiguration config = new FileConfiguration(this);
        config.load();
        if (!config.contains("types")) {
            ConfigurationNode fence = config.getNode("types").getNode("1");
            fence.set("block", Material.IRON_FENCE.toString());
            fence.set("offset", -2);
        }

        config.setHeader("types", "Define the block types and their applied offsets that will act as hang rails");
        config.addHeader("types", "The key of each block is ignored, and can be set to anything you like");
        config.addHeader("types", "For each type the block and offset settings can be configured");
        config.addHeader("types", "Block data can be specified using a colon (:), for example 'WOOL:RED'");
        config.addHeader("types", "Omitting data means data of the block is ignored entirely");
        config.addHeader("types", "The offset is up/down relative to the block. >0=above, <0=below");
        ConfigurationNode types = config.getNode("types");

        for (ConfigurationNode type : types.getNodes()) {
            String blockName = type.get("block", "MISSING");
            ItemParser block = ItemParser.parse(type.get("block", ""));
            if (!block.hasType()) {
                this.getLogger().log(Level.WARNING, "Block type '" + blockName + "' does not exist!");
                continue;
            }
            int offset = type.get("offset", -2);
            RailTypeHanging rail = new RailTypeHanging(block.getType(), block.getData(), offset);
            RailType.register(rail, false);
            this.hangingTypes.add(rail);
        }
        config.save();
    }

    @Override
    public void onDisable() {
        for (RailTypeHanging hanging : this.hangingTypes) {
            RailType.unregister(hanging);
        }
        this.hangingTypes.clear();
    }
}
