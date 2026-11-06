package com.chmomod;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChmoState extends PersistentState {
    public final Map<UUID, Integer> playerTiers = new HashMap<>();
    public final Map<UUID, String> playerRoles = new HashMap<>();
    public final Map<UUID, Integer> mobKills = new HashMap<>();
    public final Map<UUID, Integer> selectedAbility = new HashMap<>();
    public UUID winnerUuid = null;

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound tiersNbt = new NbtCompound();
        playerTiers.forEach((uuid, tier) -> tiersNbt.putInt(uuid.toString(), tier));
        nbt.put("PlayerTiers", tiersNbt);

        NbtCompound rolesNbt = new NbtCompound();
        playerRoles.forEach((uuid, role) -> rolesNbt.putString(uuid.toString(), role));
        nbt.put("PlayerRoles", rolesNbt);

        NbtCompound killsNbt = new NbtCompound();
        mobKills.forEach((uuid, kills) -> killsNbt.putInt(uuid.toString(), kills));
        nbt.put("MobKills", killsNbt);

        NbtCompound abilityNbt = new NbtCompound();
        selectedAbility.forEach((uuid, abil) -> abilityNbt.putInt(uuid.toString(), abil));
        nbt.put("SelectedAbility", abilityNbt);

        if (winnerUuid != null) {
            nbt.putString("WinnerUuid", winnerUuid.toString());
        }
        return nbt;
    }

    public static ChmoState createFromNbt(NbtCompound nbt) {
        ChmoState state = new ChmoState();
        NbtCompound tiersNbt = nbt.getCompound("PlayerTiers");
        for (String key : tiersNbt.getKeys()) {
            state.playerTiers.put(UUID.fromString(key), tiersNbt.getInt(key));
        }
        NbtCompound rolesNbt = nbt.getCompound("PlayerRoles");
        for (String key : rolesNbt.getKeys()) {
            state.playerRoles.put(UUID.fromString(key), rolesNbt.getString(key));
        }
        NbtCompound killsNbt = nbt.getCompound("MobKills");
        for (String key : killsNbt.getKeys()) {
            state.mobKills.put(UUID.fromString(key), killsNbt.getInt(key));
        }
        if (nbt.contains("SelectedAbility")) {
            NbtCompound abilityNbt = nbt.getCompound("SelectedAbility");
            for (String key : abilityNbt.getKeys()) {
                state.selectedAbility.put(UUID.fromString(key), abilityNbt.getInt(key));
            }
        }
        if (nbt.contains("WinnerUuid")) {
            state.winnerUuid = UUID.fromString(nbt.getString("WinnerUuid"));
        }
        return state;
    }

    public static ChmoState getServerState(MinecraftServer server) {
        var stateManager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        ChmoState state = stateManager.getOrCreate(ChmoState::createFromNbt, ChmoState::new, "chmo_mod_data_v3");
        state.markDirty();
        return state;
    }
}
