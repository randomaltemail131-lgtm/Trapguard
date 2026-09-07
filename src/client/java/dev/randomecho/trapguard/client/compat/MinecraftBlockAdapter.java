package dev.randomecho.trapguard.client.compat;

import dev.randomecho.trapguard.core.model.BlockDescriptor;
import dev.randomecho.trapguard.core.model.IntPos;
import dev.randomecho.trapguard.core.monitor.BlockReader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * All routine Minecraft block-state translation lives here so future ports have a small surface area.
 */
public final class MinecraftBlockAdapter {
    private MinecraftBlockAdapter() {}

    public static IntPos toCore(BlockPos pos) {
        return new IntPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos toMinecraft(IntPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    public static Optional<IntPos> targetedBlock(Minecraft client) {
        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        return Optional.of(toCore(blockHit.getBlockPos()));
    }

    public static String currentDimension(Minecraft client) {
        if (client.level == null) return "";
        return client.level.dimension().identifier().toString();
    }

    /** Stable-enough scope for keeping traps from one multiplayer server off another. */
    public static String currentServerScope(Minecraft client) {
        var server = client.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "server:" + server.ip.trim().toLowerCase(Locale.ROOT);
        }
        if (client.isLocalServer()) {
            var integratedServer = client.getSingleplayerServer();
            if (integratedServer != null) {
                // Scope local traps to the save directory rather than treating every singleplayer
                // world as the same world. The folder name is stable within this Minecraft instance
                // and avoids storing the user's full filesystem path in traps.json.
                var root = integratedServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                var folder = root.getFileName();
                if (folder != null && !folder.toString().isBlank()) {
                    return "singleplayer:" + folder;
                }
                return "singleplayer:" + integratedServer.getWorldData().getLevelName();
            }
            // Transitional fallback while an integrated server is still being attached.
            return "singleplayer";
        }
        return "unknown";
    }

    public static BlockDescriptor describe(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        Map<String, String> properties = new TreeMap<>();
        for (Property<?> property : state.getProperties()) {
            properties.put(property.getName(), propertyValueName(state, property));
        }
        return new BlockDescriptor(id, properties);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(BlockState state, Property property) {
        Comparable value = state.getValue(property);
        return property.getName(value);
    }


    public static boolean isChunkLoaded(Minecraft client, int chunkX, int chunkZ) {
        return client.level != null && client.level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    public static BlockReader reader(Minecraft client) {
        return pos -> {
            if (client.level == null) return BlockReader.ReadResult.unloaded();

            BlockPos blockPos = toMinecraft(pos);
            int chunkX = blockPos.getX() >> 4;
            int chunkZ = blockPos.getZ() >> 4;

            // Do not use ClientLevel#getBlockState for the load check. When a client chunk is
            // absent, world-level lookups can resolve through the client's empty/fallback chunk,
            // which makes an unloaded watched position appear to be AIR. getChunkNow() returns
            // null when the actual client chunk is not present and never forces a chunk load.
            LevelChunk chunk = client.level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) return BlockReader.ReadResult.unloaded();

            return BlockReader.ReadResult.loaded(describe(chunk.getBlockState(blockPos)));
        };
    }
}
