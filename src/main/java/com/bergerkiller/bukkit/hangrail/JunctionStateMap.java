package com.bergerkiller.bukkit.hangrail;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.Logging;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.config.CompressedDataReader;
import com.bergerkiller.bukkit.common.config.CompressedDataWriter;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.offline.OfflineWorldMap;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;

/**
 * Tracks the switched junction positions of rails with more than 2 possible
 * directions.
 */
public class JunctionStateMap {
    private final Map<OfflineBlock, State> states = new HashMap<>();
    private final Task saveTask;
    private final File saveFile;
    private CompletableFuture<Void> asyncSaveTask;
    private boolean changed = false;

    public JunctionStateMap(JavaPlugin plugin) {
        saveFile = (new File(plugin.getDataFolder(), "junctions.dat")).getAbsoluteFile();
        saveTask = new Task(plugin) {
            @Override
            public void run() {
                if (changed) {
                    if (asyncSaveTask.isDone()) {
                        changed = false;

                        // Start an asynchronous task of saving the data
                        final List<State> statesToSave = new ArrayList<>(states.values());
                        asyncSaveTask = CompletableFuture.runAsync(() -> {
                            saveToDisk(statesToSave);
                        }, getPluginAsyncExecutor(getPlugin()));
                    } else {
                        // Still saving, try again later
                        saveTask.start(100);
                    }
                }
            }
        };
        asyncSaveTask = CompletableFuture.completedFuture(null);
    }

    /**
     * Loads data from disk, if a junction state file exists
     */
    public void load() {
        changed = false;
        states.clear();

        if (saveFile.exists() && !(new CompressedDataReader(saveFile) {
            @Override
            public void read(DataInputStream stream) throws IOException {
                int version = (int) stream.readByte() & 0xFF;
                if (version == 0x1) {
                    readVersion1(stream);
                } else {
                    throw new IOException("Unsupported data version: " + version);
                }
            }

            private void readVersion1(DataInputStream stream) throws IOException {
                int numWorlds = stream.readInt();
                for (int n = 0; n < numWorlds; n++) {
                    OfflineWorld world = OfflineWorld.of(StreamUtil.readUUID(stream));
                    int numStates = stream.readInt();
                    for (int s = 0; s < numStates; s++) {
                        State state = State.readFrom(world, stream);
                        states.put(state.block, state);
                    }
                }
            }
        }.read())) {
            saveTask.getPlugin().getLogger().log(Level.SEVERE, "Failed to read junction states");
            saveFile.delete();
        }
    }

    /**
     * If there are still changes to be saved, saves them. Waits for any ongoing
     * saving to disk to complete. Finally, clears all stored data.
     */
    public void shutdown() {
        // Stop the task and wait for any asynchronous saving to complete
        saveTask.stop();
        try {
            asyncSaveTask.get();
        } catch (Throwable t) {
            saveTask.getPlugin().getLogger().log(Level.SEVERE, "Failed to save junction states", t);
        }

        // If still changed, save sync, right now
        if (changed) {
            changed = false;
            saveToDisk(new ArrayList<>(states.values()));
        }

        // Wipe
        states.clear();
    }

    /**
     * Schedules the next save if changes occurred before hang rail was fully enabled
     */
    public void startSavingIfChanged() {
        if (changed) {
            saveTask.start(100);
        }
    }

    public void remove(Block block) {
        states.remove(OfflineBlock.of(block));
    }

    public State get(Block block) {
        return states.get(OfflineBlock.of(block));
    }

    public void set(Block block, BlockFace from, BlockFace to) {
        // Protect against from==to. Probably won't happen, but whatever.
        if (from == to) {
            from = to.getOppositeFace();
        }

        // Update in map
        OfflineBlock offBlock = OfflineBlock.of(block);
        State prev = states.put(offBlock, new State(offBlock, from, to));
        if (prev != null && prev.from == from && prev.to == to) {
            return; // Not changed, don't save.
        }

        // Start saving if needed
        if (!changed) {
            changed = true;
            if (saveTask.getPlugin().isEnabled()) {
                saveTask.start(100); // Save after 5s
            }
        }
    }

    private void saveToDisk(List<State> states) {
        // Firstly, convert the list of save states to a mapping by World
        final OfflineWorldMap<List<State>> byWorld = new OfflineWorldMap<>();
        for (State state : states) {
            byWorld.computeIfAbsent(state.block.getWorld(), u -> new ArrayList<>())
                .add(state);
        }

        // Write to the temp file
        File tempFile = new File(this.saveFile.getParentFile(),
                this.saveFile.getName() + "." + System.currentTimeMillis());
        if (!(new CompressedDataWriter(tempFile) {
            @Override
            public void write(DataOutputStream stream) throws IOException {
                stream.writeByte(0x1); // Version
                stream.writeInt(byWorld.size()); // For every world
                for (Map.Entry<OfflineWorld, List<State>> e : byWorld.entrySet()) {
                    List<State> worldStates = e.getValue();
                    StreamUtil.writeUUID(stream, e.getKey().getUniqueId());
                    stream.writeInt(worldStates.size()); // For every state in this world
                    for (State state : worldStates) {
                        state.writeTo(stream);
                    }
                }
            }
        }.write())) {
            saveTask.getPlugin().getLogger().log(Level.SEVERE, "Failed to save junction states");
            return;
        }

        try {
            atomicReplace(tempFile, this.saveFile);
        } catch (IOException ex) {
            tempFile.delete();
            saveTask.getPlugin().getLogger().log(Level.SEVERE, "Failed to save junction states", ex);
        }
    }

    // Ported from BKCommonLib CommonUtil
    private static Executor getPluginAsyncExecutor(Plugin plugin) {
        return task -> {
            if (!plugin.isEnabled()) {
                Logging.LOGGER.warning("Failed to execute asynchronous task for plugin " + plugin.getName() + " because plugin is disabled");
            } else {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
            }
        };
    }

    // Ported from BKCommonLib StreamUtil
    private static void atomicReplace(File fromFile, File toFile) throws IOException {
        // First try a newer Java's Files.move as this allows for an atomic move with overwrite
        // If this doesn't work, only then do we try our custom non-atomic methods
        try {
            Files.move(fromFile.toPath(), toFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (AtomicMoveNotSupportedException | UnsupportedOperationException unsupportedIgnored) {
            // Efficient move using this method is not supported, use a fallback
        }

        // More dangerous: delete target file, then move the temp file to it
        // This operation is not atomic and could fail
        if (toFile.delete() && fromFile.renameTo(toFile)) {
            return;
        }

        // Even more risky: copy the data by using file streams
        // This could result in partial data in the destination file :(
        if (StreamUtil.tryCopyFile(fromFile, toFile)) {
            fromFile.delete();
            return;
        }

        // No idea anymore
        throw new IOException("Failed to move " + fromFile.getAbsolutePath() + " to " + toFile.getAbsolutePath());
    }

    /**
     * Switched state of a hangrail rail block
     */
    public static final class State {
        public final OfflineBlock block;
        public final BlockFace from;
        public final BlockFace to;

        public State(OfflineBlock block, BlockFace from, BlockFace to) {
            this.block = block;
            this.from = from;
            this.to = to;
        }

        public void writeTo(DataOutputStream stream) throws IOException {
            block.getPosition().write(stream);
            stream.writeByte(FaceUtil.faceToNotch(from));
            stream.writeByte(FaceUtil.faceToNotch(to));
        }

        public static State readFrom(OfflineWorld world, DataInputStream stream) throws IOException {
            IntVector3 pos = IntVector3.read(stream);
            BlockFace from = FaceUtil.notchToFace((int) stream.readByte() & 0xFF);
            BlockFace to = FaceUtil.notchToFace((int) stream.readByte() & 0xFF);
            return new State(world.getBlockAt(pos), from, to);
        }
    }
}
