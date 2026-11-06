package com.chmomod;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChmoState extends PersistentState {
    // Текущий уровень титула (от 1 до 12)
    public final Map<UUID, Integer> titleLevels = new HashMap<>();
    // Выбранная способность в компасе (0 = выключено, 1..12 = номер способности)
    public final Map<UUID, Integer> selectedAbility = new HashMap<>();
    // Общий счётчик убитых враждебных мобов
    public final Map<UUID, Integer> mobKills = new HashMap<>();

    public int getTitleLevel(UUID uuid) {
        return titleLevels.getOrDefault(uuid, 1);
    }

    public void setTitleLevel(UUID uuid, int level) {
        titleLevels.put(uuid, level);
        markDirty();
    }

    public int getSelectedAbility(UUID uuid) {
        return selectedAbility.getOrDefault(uuid, 0);
    }

    public void setSelectedAbility(UUID uuid, int abilityId) {
        selectedAbility.put(uuid, abilityId);
        markDirty();
    }

    public int getMobKills(UUID uuid) {
        return mobKills.getOrDefault(uuid, 0);
    }

    public void addMobKill(UUID uuid) {
        mobKills.put(uuid, getMobKills(uuid) + 1);
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound levelsNbt = new NbtCompound();
        titleLevels.forEach((uuid, lvl) -> levelsNbt.putInt(uuid.toString(), lvl));
        nbt.put("TitleLevels", levelsNbt);

        NbtCompound abilityNbt = new NbtCompound();
        selectedAbility.forEach((uuid, ab) -> abilityNbt.putInt(uuid.toString(), ab));
        nbt.put("SelectedAbilities", abilityNbt);

        NbtCompound killsNbt = new NbtCompound();
        mobKills.forEach((uuid, kills) -> killsNbt.putInt(uuid.toString(), kills));
        nbt.put("MobKills", killsNbt);

        return nbt;
    }

    public static ChmoState createFromNbt(NbtCompound tag) {
        ChmoState state = new ChmoState();

        NbtCompound levelsNbt = tag.getCompound("TitleLevels");
        for (String key : levelsNbt.getKeys()) {
            state.titleLevels.put(UUID.fromString(key), levelsNbt.getInt(key));
        }

        NbtCompound abilityNbt = tag.getCompound("SelectedAbilities");
        for (String key : abilityNbt.getKeys()) {
            state.selectedAbility.put(UUID.fromString(key), abilityNbt.getInt(key));
        }

        NbtCompound killsNbt = tag.getCompound("MobKills");
        for (String key : killsNbt.getKeys()) {
            state.mobKills.put(UUID.fromString(key), killsNbt.getInt(key));
        }

        return state;
    }

    public static ChmoState getServerState(MinecraftServer server) {
        PersistentStateManager manager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return manager.getOrCreate(ChmoState::createFromNbt, ChmoState::new, "chmomod_state");
    }
}
