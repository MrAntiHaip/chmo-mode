package com.chmomod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;

public class ChmoMod implements ModInitializer {

    @Override
    public void onInitialize() {
        // 1. Учёт убийства враждебных мобов (общий счётчик)
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (entity instanceof ServerPlayerEntity player && killedEntity instanceof HostileEntity) {
                ChmoState state = ChmoState.getServerState(world.getServer());
                state.addMobKill(player.getUuid());
            }
        });

        // 2. Ежесекундное обновление выбранной способности
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ChmoState state = ChmoState.getServerState(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                TitleAbilityManager.tickPlayerAbility(player, state);
            }
        });

        // 3. Обработка компаса:
        // Shift + ПКМ -> открыть меню выбора способностей
        // Обычный ПКМ -> использовать активную способность (например, Орбитальную пушку)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.COMPASS)) {
                if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                    ChmoState state = ChmoState.getServerState(serverPlayer.getServer());
                    if (serverPlayer.isSneaking()) {
                        TitleAbilityManager.openCompassMenu(serverPlayer, state);
                    } else {
                        TitleAbilityManager.useActiveCompassAbility(serverPlayer, state);
                    }
                }
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });

        // 4. Команды /quest (сдать задание) и /questinfo (посмотреть текущие требования)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("quest")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    tryCompleteNextQuest(player);
                    return 1;
                }));

            dispatcher.register(CommandManager.literal("questinfo")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    showQuestInfo(player);
                    return 1;
                }));
        });
    }

    // Проверка и сдача заданий со 2-го по 12-е
    public static void tryCompleteNextQuest(ServerPlayerEntity player) {
        ChmoState state = ChmoState.getServerState(player.getServer());
        int currentLevel = state.getTitleLevel(player.getUuid());

        if (currentLevel >= 12) {
            player.sendMessage(Text.literal("Вы уже достигли максимального 12-го титула!").formatted(Formatting.GOLD), false);
            return;
        }

        int nextQuest = currentLevel + 1;
        int totalKills = state.getMobKills(player.getUuid());
        boolean completed = false;

        switch (nextQuest) {
            case 2 -> { // Задание 2 (Лошпед): стак булыжника (64 шт.)
                if (hasItems(player, Items.COBBLESTONE, 64)) {
                    removeItems(player, Items.COBBLESTONE, 64);
                    completed = true;
                }
            }
            case 3 -> { // Задание 3: 16 угля и 1 хлеб
                if (hasItems(player, Items.COAL, 16) && hasItems(player, Items.BREAD, 1)) {
                    removeItems(player, Items.COAL, 16);
                    removeItems(player, Items.BREAD, 1);
                    completed = true;
                }
            }
            case 4 -> { // Задание 4: 16 железных слитков и 16 редстоун пыли
                if (hasItems(player, Items.IRON_INGOT, 16) && hasItems(player, Items.REDSTONE, 16)) {
                    removeItems(player, Items.IRON_INGOT, 16);
                    removeItems(player, Items.REDSTONE, 16);
                    completed = true;
                }
            }
            case 5 -> { // Задание 5: убить 10 враждебных мобов + 32 стейка
                if (totalKills >= 10 && hasItems(player, Items.COOKED_BEEF, 32)) {
                    removeItems(player, Items.COOKED_BEEF, 32);
                    completed = true;
                }
            }
            case 6 -> { // Задание 6: 2 золотых блока и убийство 20 враждебных мобов
                if (totalKills >= 20 && hasItems(player, Items.GOLD_BLOCK, 2)) {
                    removeItems(player, Items.GOLD_BLOCK, 2);
                    completed = true;
                }
            }
            case 7 -> { // Задание 7: 5 редстоун блоков + убить 30 мобов
                if (totalKills >= 30 && hasItems(player, Items.REDSTONE_BLOCK, 5)) {
                    removeItems(player, Items.REDSTONE_BLOCK, 5);
                    completed = true;
                }
            }
            case 8 -> { // Задание 8: 42 лазурита и убить 40 мобов
                if (totalKills >= 40 && hasItems(player, Items.LAPIS_LAZULI, 42)) {
                    removeItems(player, Items.LAPIS_LAZULI, 42);
                    completed = true;
                }
            }
            case 9 -> { // Задание 9: 24 алмаза + убить 50 мобов
                if (totalKills >= 50 && hasItems(player, Items.DIAMOND, 24)) {
                    removeItems(player, Items.DIAMOND, 24);
                    completed = true;
                }
            }
            case 10 -> { // Задание 10: 10 обсидиана и 32 незерака
                if (hasItems(player, Items.OBSIDIAN, 10) && hasItems(player, Items.NETHERRACK, 32)) {
                    removeItems(player, Items.OBSIDIAN, 10);
                    removeItems(player, Items.NETHERRACK, 32);
                    completed = true;
                }
            }
            case 11 -> { // Задание 11: 6 огненных стержней и убить 60 мобов
                if (totalKills >= 60 && hasItems(player, Items.BLAZE_ROD, 6)) {
                    removeItems(player, Items.BLAZE_ROD, 6);
                    completed = true;
                }
            }
            case 12 -> { // Задание 12: 6 очей эндера + убить 70 мобов
                if (totalKills >= 70 && hasItems(player, Items.ENDER_EYE, 6)) {
                    removeItems(player, Items.ENDER_EYE, 6);
                    completed = true;
                }
            }
        }

        if (completed) {
            state.setTitleLevel(player.getUuid(), nextQuest);
            // Если до этого вообще не была выбрана способность, включаем первую полученную,
            // иначе оставляем выбор за игроком через компас!
            if (state.getSelectedAbility(player.getUuid()) == 0) {
                state.setSelectedAbility(player.getUuid(), nextQuest);
            }
            player.sendMessage(Text.literal("Задание выполнено! Новый титул: " + TitleAbilityManager.TITLE_NAMES[nextQuest]).formatted(Formatting.GREEN), false);
            player.sendMessage(Text.literal("Новая способность разблокирована в компасе (Shift + ПКМ с компасом для смены).").formatted(Formatting.AQUA), false);
        } else {
            player.sendMessage(Text.literal("Требования для задания №" + nextQuest + " ещё не выполнены!").formatted(Formatting.RED), false);
            showQuestInfo(player);
        }
    }

    public static void showQuestInfo(ServerPlayerEntity player) {
        ChmoState state = ChmoState.getServerState(player.getServer());
        int nextQuest = state.getTitleLevel(player.getUuid()) + 1;
        int kills = state.getMobKills(player.getUuid());

        if (nextQuest > 12) {
            player.sendMessage(Text.literal("Все задания выполнены!").formatted(Formatting.GOLD), false);
            return;
        }

        String req = switch (nextQuest) {
            case 2 -> "64 булыжника (1 стак)";
            case 3 -> "16 угля и 1 хлеб";
            case 4 -> "16 железных слитков и 16 редстоун пыли";
            case 5 -> "32 стейка и убить 10 враждебных мобов (Убито: " + kills + "/10)";
            case 6 -> "2 золотых блока и убить 20 враждебных мобов (Убито: " + kills + "/20)";
            case 7 -> "5 редстоун блоков и убить 30 враждебных мобов (Убито: " + kills + "/30)";
            case 8 -> "42 лазурита и убить 40 враждебных мобов (Убито: " + kills + "/40)";
            case 9 -> "24 алмаза и убить 50 враждебных мобов (Убито: " + kills + "/50)";
            case 10 -> "10 обсидиана и 32 незерака";
            case 11 -> "6 огненных стержней и убить 60 враждебных мобов (Убито: " + kills + "/60)";
            case 12 -> "6 очей эндера и убить 70 враждебных мобов (Убито: " + kills + "/70)";
            default -> "";
        };

        player.sendMessage(Text.literal("Задание №" + nextQuest + " (" + TitleAbilityManager.TITLE_NAMES[nextQuest] + "): " + req).formatted(Formatting.YELLOW), false);
    }

    private static boolean hasItems(PlayerEntity player, Item item, int count) {
        int found = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                found += stack.getCount();
                if (found >= count) return true;
            }
        }
        return false;
    }

    private static void removeItems(PlayerEntity player, Item item, int count) {
        int remaining = count;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.decrement(take);
                remaining -= take;
                if (remaining <= 0) break;
            }
        }
    }
}
