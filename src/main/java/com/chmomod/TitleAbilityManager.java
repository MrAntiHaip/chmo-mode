package com.chmomod;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TitleAbilityManager {

    // Названия титулов (1..12)
    public static final String[] TITLE_NAMES = {
        "",
        "1. Чмо",
        "2. Лошпед",
        "3. Новичок",
        "4. Шахтёр",
        "5. Охотник",
        "6. Богач",
        "7. Инженер",
        "8. Чародей",
        "9. Алмазник",
        "10. Покоритель Ада",
        "11. Ифрит",
        "12. Властелин (Орбитальная пушка)"
    };

    // Разные цвета для каждой способности в компасе
    public static final Item[] ABILITY_COLORS = {
        Items.AIR,
        Items.BROWN_DYE,      // 1
        Items.LIGHT_GRAY_DYE, // 2 - Лошпед (Скорость I)
        Items.YELLOW_DYE,     // 3 - Прыгучесть
        Items.ORANGE_DYE,     // 4 - Спешка
        Items.RED_DYE,        // 5 - Сила I
        Items.GOLD_INGOT,     // 6 - Регенерация
        Items.REDSTONE,       // 7 - Скорость II
        Items.BLUE_DYE,       // 8 - Ночное зрение + Водное дыхание
        Items.CYAN_DYE,       // 9 - Сопротивление
        Items.PURPLE_DYE,     // 10 - Огнестойкость
        Items.BLAZE_POWDER,   // 11 - Сила II + Прыгучесть II
        Items.MAGENTA_DYE     // 12 - Орбитальная пушка
    };

    // Вызывается каждый тик сервера: накладывает ТОЛЬКО выбранную способность (без стаканья)
    public static void tickPlayerAbility(ServerPlayerEntity player, ChmoState state) {
        int selected = state.getSelectedAbility(player.getUuid());
        int unlocked = state.getTitleLevel(player.getUuid());

        // Если ничего не выбрано или способность выше уровня игрока — ничего не даём
        if (selected <= 1 || selected > unlocked) {
            return;
        }

        // Накладываем эффект на 40 тиков (2 сек), чтобы при смене способности в компасе старая сразу спадала
        switch (selected) {
            case 2 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, true, false));
            case 3 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 40, 1, true, false));
            case 4 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 40, 1, true, false));
            case 5 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 0, true, false));
            case 6 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 0, true, false));
            case 7 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 1, true, false));
            case 8 -> {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 220, 0, true, false));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 40, 0, true, false));
            }
            case 9 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 0, true, false));
            case 10 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, true, false));
            case 11 -> {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 1, true, false));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 40, 1, true, false));
            }
            case 12 -> {
                // Пассивный бонус 12 уровня + активная Орбитальная пушка на ПКМ компасом
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 40, 1, true, false));
            }
        }
    }

    // Использование активной способности по ПКМ компасом (например, Орбитальная пушка)
    public static void useActiveCompassAbility(ServerPlayerEntity player, ChmoState state) {
        int selected = state.getSelectedAbility(player.getUuid());
        int unlocked = state.getTitleLevel(player.getUuid());

        if (selected == 0) {
            player.sendMessage(Text.literal("У вас не выбрана способность! Нажмите Shift + ПКМ с компасом, чтобы открыть меню.").formatted(Formatting.YELLOW), true);
            return;
        }

        if (selected > unlocked) {
            return;
        }

        // Пример: если выбрана способность с Орбитальной пушкой (например, 12-й титул, или любой, где она у вас привязана)
        if (selected == 12) {
            if (player.getItemCooldownManager().isCoolingDown(Items.COMPASS)) {
                return;
            }
            HitResult hit = player.raycast(60.0D, 0.0F, false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                World world = player.getWorld();

                // Удар орбитальной пушки (молнии + взрыв без разрушения блоков или с ним)
                for (int i = 0; i < 3; i++) {
                    LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
                    if (lightning != null) {
                        lightning.refreshPositionAfterTeleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        world.spawnEntity(lightning);
                    }
                }
                world.createExplosion(player, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 4.0F, World.ExplosionSourceType.MOB);
                player.getItemCooldownManager().set(Items.COMPASS, 100); // КД 5 секунд
                player.sendMessage(Text.literal("Орбитальный удар нанесён!").formatted(Formatting.LIGHT_PURPLE), true);
            } else {
                player.sendMessage(Text.literal("Цель слишком далеко!").formatted(Formatting.RED), true);
            }
        } else {
            player.sendMessage(Text.literal("Сейчас активна пассивная способность: " + TITLE_NAMES[selected] + " (Shift + ПКМ — сменить)").formatted(Formatting.GREEN), true);
        }
    }

    // Открытие GUI внутри компаса для включения/выключения разблокированных способностей
    public static void openCompassMenu(ServerPlayerEntity player, ChmoState state) {
        int unlockedLevel = state.getTitleLevel(player.getUuid());
        int currentSelected = state.getSelectedAbility(player.getUuid());

        SimpleInventory inv = new SimpleInventory(27);

        for (int lvl = 2; lvl <= 12; lvl++) {
            int slot = lvl - 2; // Слоты с 0 по 10
            if (lvl <= unlockedLevel) {
                boolean isSelected = (currentSelected == lvl);
                ItemStack icon = new ItemStack(ABILITY_COLORS[lvl]);
                String status = isSelected ? " [ВКЛЮЧЕНА]" : " [Нажмите, чтобы включить]";
                Formatting color = isSelected ? Formatting.GREEN : Formatting.AQUA;
                icon.setCustomName(Text.literal(TITLE_NAMES[lvl] + status).formatted(color));
                inv.setStack(slot, icon);
            } else {
                ItemStack locked = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
                locked.setCustomName(Text.literal(TITLE_NAMES[lvl] + " [ЗАБЛОКИРОВАНО]").formatted(Formatting.DARK_GRAY));
                inv.setStack(slot, locked);
            }
        }

        // Кнопка полного отключения способностей в 26-м слоте
        ItemStack disableAll = new ItemStack(Items.BARRIER);
        disableAll.setCustomName(Text.literal("Отключить все способности").formatted(Formatting.RED));
        inv.setStack(26, disableAll);

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Выбор способности (Компас)");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
                return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inv, 3) {
                    @Override
                    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clicker) {
                        if (slotIndex >= 0 && slotIndex < 27 && clicker instanceof ServerPlayerEntity serverPlayer) {
                            if (slotIndex == 26) {
                                state.setSelectedAbility(serverPlayer.getUuid(), 0);
                                clearAbilityEffects(serverPlayer);
                                serverPlayer.sendMessage(Text.literal("Все способности отключены.").formatted(Formatting.YELLOW), false);
                                serverPlayer.closeHandledScreen();
                                return;
                            }

                            int clickedLevel = slotIndex + 2;
                            if (clickedLevel >= 2 && clickedLevel <= 12) {
                                int maxLvl = state.getTitleLevel(serverPlayer.getUuid());
                                if (clickedLevel <= maxLvl) {
                                    int current = state.getSelectedAbility(serverPlayer.getUuid());
                                    clearAbilityEffects(serverPlayer);

                                    if (current == clickedLevel) {
                                        // Если нажали на уже включённую — выключаем
                                        state.setSelectedAbility(serverPlayer.getUuid(), 0);
                                        serverPlayer.sendMessage(Text.literal("Способность «" + TITLE_NAMES[clickedLevel] + "» выключена.").formatted(Formatting.YELLOW), false);
                                    } else {
                                        // Включаем новую (старая автоматически заменяется и не стакается)
                                        state.setSelectedAbility(serverPlayer.getUuid(), clickedLevel);
                                        serverPlayer.sendMessage(Text.literal("Активирована способность: " + TITLE_NAMES[clickedLevel]).formatted(Formatting.GREEN), false);
                                    }
                                    serverPlayer.closeHandledScreen();
                                } else {
                                    serverPlayer.sendMessage(Text.literal("Эта способность ещё не разблокирована!").formatted(Formatting.RED), false);
                                }
                            }
                        }
                        // Не вызываем super.onSlotClick, чтобы предметы нельзя было забрать из меню компаса
                    }
                };
            }
        });
    }

    private static void clearAbilityEffects(ServerPlayerEntity player) {
        player.removeStatusEffect(StatusEffects.SPEED);
        player.removeStatusEffect(StatusEffects.JUMP_BOOST);
        player.removeStatusEffect(StatusEffects.HASTE);
        player.removeStatusEffect(StatusEffects.STRENGTH);
        player.removeStatusEffect(StatusEffects.REGENERATION);
        player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        player.removeStatusEffect(StatusEffects.WATER_BREATHING);
        player.removeStatusEffect(StatusEffects.RESISTANCE);
        player.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
        player.removeStatusEffect(StatusEffects.ABSORPTION);
    }
}
