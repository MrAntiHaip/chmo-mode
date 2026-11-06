package com.chmomod;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChmoState extends PersistentState {
    public Map<UUID, Integer> playerTiers = new HashMap<>();
    public Map<UUID, Integer> mobKills = new HashMap<>();
    public Map<UUID, String> playerRoles = new HashMap<>();
    public UUID winnerUuid = null;

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound tiers = new NbtCompound();
        playerTiers.forEach((k, v) -> tiers.putInt(k.toString(), v));
        nbt.put("tiers", tiers);

        NbtCompound kills = new NbtCompound();
        mobKills.forEach((k, v) -> kills.putInt(k.toString(), v));
        nbt.put("kills", kills);

        NbtCompound roles = new NbtCompound();
        playerRoles.forEach((k, v) -> roles.putString(k.toString(), v));
        nbt.put("roles", roles);

        if (winnerUuid != null) nbt.putString("winner", winnerUuid.toString());
        return nbt;
    }

    public static ChmoState createFromNbt(NbtCompound nbt) {
        ChmoState state = new ChmoState();
        NbtCompound tiers = nbt.getCompound("tiers");
        tiers.getKeys().forEach(k -> state.playerTiers.put(UUID.fromString(k), tiers.getInt(k)));
        
        NbtCompound kills = nbt.getCompound("kills");
        kills.getKeys().forEach(k -> state.mobKills.put(UUID.fromString(k), kills.getInt(k)));

        NbtCompound roles = nbt.getCompound("roles");
        roles.getKeys().forEach(k -> state.playerRoles.put(UUID.fromString(k), roles.getString(k)));

        if (nbt.contains("winner")) state.winnerUuid = UUID.fromString(nbt.getString("winner"));
        return state;
    }

    public static ChmoState getServerState(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        ChmoState state = manager.getOrCreate(ChmoState::createFromNbt, ChmoState::new, "chmomod_data");
        state.markDirty();
        return state;
    }
}
