package com.chmomod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;

import java.util.List;

public class ChmoMod implements ModInitializer {

    public record TierDef(
            int level,
            String title,
            Item icon,
            Item reqItem,
            int reqCount,
            int reqKills,
            String taskDesc,
            String abilityName,
            String abilityDesc,
            Item rewardItem,
            int rewardCount
    ) {}

    public static final List<TierDef> TIERS = List.of(
            new TierDef(1, "1. Инфузория", Items.KELP, Items.DIRT, 16, 0,
                    "Сдать 16 Земли", "Фотосинтез и рефлекс таракана",
                    "Сытость растет днем/в воде + рывок при уроне", Items.BREAD, 8),
            new TierDef(2, "2. Лошпед", Items.EGG, Items.COBBLESTONE, 32, 0,
                    "Сдать 32 Булыжника", "Карманный грабеж кур",
                    "Животные ходят за тобой, курицы несут руду и изумруды", Items.STONE_PICKAXE, 1),
            new TierDef(3, "3. Плесень", Items.SLIME_BALL, Items.COAL, 16, 0,
                    "Сдать 16 Угля", "Пузо-батут",
                    "Падение на Shift гасит урон и подбрасывает всех вокруг", Items.TORCH, 32),
            new TierDef(4, "4. Суетолог", Items.SUGAR, Items.IRON_INGOT, 12, 0,
                    "Сдать 12 Железных слитков", "Пылесос жадности + Карманник",
                    "Магнит лута 9 блоков + [Shift+ПКМ] кража из рук напарника", Items.IRON_PICKAXE, 1),
            new TierDef(5, "5. Подгорелый", Items.BLAZE_POWDER, Items.COOKED_BEEF, 16, 5,
                    "Сдать 16 Стейков и убить 5 мобов", "Карманная домна",
                    "Руда и еда плавятся прямо в инвентаре во время бега", Items.SHIELD, 1),
            new TierDef(6, "6. Шнырь", Items.ENDER_PEARL, Items.GOLD_INGOT, 16, 10,
                    "Сдать 16 Золота и убить 10 мобов", "Квантовый кусь",
                    "Удары с шансом 25% меняют тебя местами с врагом и лечат", Items.GOLDEN_APPLE, 4),
            new TierDef(7, "7. Гоблин-шахтер", Items.AMETHYST_SHARD, Items.REDSTONE, 32, 15,
                    "Сдать 32 Редстоуна и убить 15 мобов", "Эхолокация и взрывная кирка",
                    "Руды звенят сквозь стены + камень копается буром", Items.DIAMOND_PICKAXE, 1),
            new TierDef(8, "8. Токсик", Items.PUFFERFISH, Items.LAPIS_LAZULI, 24, 20,
                    "Сдать 24 Лазурита и убить 20 мобов", "Реверс снарядов",
                    "Стрелы летят обратно в стрелков, криперы гаснут рядом", Items.BOW, 1),
            new TierDef(9, "9. Абьюзер", Items.TNT, Items.DIAMOND, 5, 25,
                    "Сдать 5 Алмазов и убить 25 мобов", "Паразит напарника",
                    "При низком ХП 50% твоего урона переносится на напарника", Items.DIAMOND_SWORD, 1),
            new TierDef(10, "10. Сверхпроводник", Items.LIGHTNING_ROD, Items.OBSIDIAN, 10, 30,
                    "Сдать 10 Обсидиана и убить 30 мобов", "Грави-пушка (Телекинез)",
                    "[Shift+ПКМ] хватает мобов/напарника в воздух и швыряет", Items.ENDER_PEARL, 8),
            new TierDef(11, "11. Темпорал", Items.CLOCK, Items.BLAZE_ROD, 6, 40,
                    "Сдать 6 Огненных стержней и убить 40 мобов", "Откат времени (Эхо 5 сек)",
                    "Спасает от смерти и лавы перемоткой на 5 секунд назад", Items.TOTEM_OF_UNDYING, 1),
            new TierDef(12, "12. C.H.M.O", Items.NETHER_STAR, Items.ENDER_EYE, 8, 50,
                    "Сдать 8 Очей Эндера и убить 50 мобов", "Абсолютный Доминатор",
                    "Бег по воздуху на Shift + [Shift+ПКМ] Орбитальный удар молниями", Items.NETHERITE_CHESTPLATE, 1)
    );

    public static int getPlayerTier(ServerPlayerEntity player) {
        ChmoState state = ChmoState.getServerState(player.getServer());
        return state.playerTiers.getOrDefault(player.getUuid(), 1);
    }

    @Override
    public void onInitialize() {
        TitleAbilityManager.register();

        // Учет убийств монстров
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (entity instanceof ServerPlayerEntity player && killedEntity instanceof HostileEntity) {
                ChmoState state = ChmoState.getServerState(world.getServer());
                int kills = state.mobKills.getOrDefault(player.getUuid(), 0) + 1;
                state.mobKills.put(player.getUuid(), kills);
                state.markDirty();
            }
        });

        // Выдача компаса при респавне
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ensureCompass(newPlayer));

        // Проверка наличия компаса раз в секунду + обновление имени в Табе с титулом
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 == 0) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    ensureCompass(player);
                }
            }
        });

        // Клик компасом: ПКМ = Меню, Shift + ПКМ = Активная суперспособность титула
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (isChmoCompass(stack)) {
                if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                    if (serverPlayer.isSneaking()) {
                        TitleAbilityManager.triggerActiveAbility(serverPlayer);
                    } else {
                        openChmoMenu(serverPlayer);
                    }
                }
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });
    }

    private static boolean isChmoCompass(ItemStack stack) {
        return stack.isOf(Items.COMPASS) && stack.hasNbt() && stack.getNbt().getBoolean("ChmoCompass");
    }

    private static void ensureCompass(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (isChmoCompass(player.getInventory().getStack(i))) return;
        }
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.getOrCreateNbt().putBoolean("ChmoCompass", true);
        compass.addEnchantment(Enchantments.VANISHING_CURSE, 1);
        compass.setCustomName(Text.literal("🧭 Компас Судьбы C.H.M.O (ПКМ — Меню | Shift+ПКМ — Ульта)").formatted(Formatting.AQUA, Formatting.BOLD));
        player.getInventory().insertStack(compass);
    }

    public static void openChmoMenu(ServerPlayerEntity player) {
        ChmoState state = ChmoState.getServerState(player.getServer());
        int currentTier = state.playerTiers.getOrDefault(player.getUuid(), 1);
        int kills = state.mobKills.getOrDefault(player.getUuid(), 0);
        String role = state.playerRoles.getOrDefault(player.getUuid(), "Не выбрана");

        SimpleInventory inv = new SimpleInventory(54);

        // Заполняем верхний ряд: выбор роли Дима / Ульяна и инфо
        inv.setStack(0, makeGuiItem(Items.DIAMOND_HELMET, "👦 Выбрать роль: ДИМА", Formatting.AQUA,
                List.of("Текущая роль: " + role, "Нажми, чтобы играть за Диму!")));
        inv.setStack(1, makeGuiItem(Items.GOLDEN_HELMET, "👧 Выбрать роль: УЛЬЯНА", Formatting.LIGHT_PURPLE,
                List.of("Текущая роль: " + role, "Нажми, чтобы играть за Ульяну!")));

        inv.setStack(4, makeGuiItem(Items.NETHER_STAR, "📊 Твой статус: " + TIERS.get(currentTier - 1).title(), Formatting.GOLD,
                List.of("Роль: " + role,
                        "Убито монстров: " + kills,
                        "Активная способность: " + TIERS.get(currentTier - 1).abilityName(),
                        "Совет: зажми Shift + ПКМ компасом в воздухе для ульты!")));

        inv.setStack(8, makeGuiItem(Items.BLAZE_ROD, "⚡️ АКТИВИРОВАТЬ СПОСОБНОСТЬ ТИТУЛА", Formatting.YELLOW,
                List.of("Способность: " + TIERS.get(currentTier - 1).abilityName(),
                        TIERS.get(currentTier - 1).abilityDesc(),
                        "Нажми сюда или используй Shift + ПКМ компасом!")));

        // Слоты 19..25 и 28..32 — 12 ступеней прокачки
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32};
        for (int i = 0; i < TIERS.size(); i++) {
            TierDef t = TIERS.get(i);
            boolean unlocked = currentTier >= t.level();
            boolean isNext = currentTier + 1 == t.level();

            Item icon = unlocked ? t.icon() : (isNext ? Items.GOLD_BLOCK : Items.GRAY_DYE);
            Formatting color = unlocked ? Formatting.GREEN : (isNext ? Formatting.YELLOW : Formatting.GRAY);
            String status = unlocked ? "✅ ПОЛУЧЕНО" : (isNext ? "⏳ НАЖМИ, ЧТОБЫ ПРОКАЧАТЬ" : "🔒 ЗАБЛОКИРОВАНО");

            inv.setStack(slots[i], makeGuiItem(icon, t.title() + " [" + status + "]", color, List.of(
                    "📋 Задание: " + t.taskDesc() + " (Убийств: " + kills + "/" + t.reqKills() + ")",
                    "✨ Способность: " + t.abilityName(),
                    "💡 " + t.abilityDesc(),
                    "🎁 Награда: " + t.rewardCount() + "x " + t.rewardItem().getName().getString()
            )));
        }

        // Нижний ряд: Кооп-функции и пранки для Димы и Ульяны
        inv.setStack(45, makeGuiItem(Items.ENDER_PEARL, "🌀 Телепорт к напарнику", Formatting.AQUA,
                List.of("Мгновенно переносит тебя к Диме/Ульяне")));
        inv.setStack(47, makeGuiItem(Items.SPYGLASS, "📡 Радар напарника", Formatting.GREEN,
                List.of("Показывает точные координаты и дистанцию до второго игрока")));
        inv.setStack(49, makeGuiItem(Items.GOLDEN_CARROT, "👁 Ночное зрение (Вкл/Выкл)", Formatting.GOLD,
                List.of("Бесплатная подсветка пещер и темноты")));
        inv.setStack(51, makeGuiItem(Items.CREEPER_HEAD, "💣 ПРАНК: Фейковый Крипер", Formatting.RED,
                List.of("Включает за спиной напарника шипение крипера и взрыв частиц!")));
        inv.setStack(53, makeGuiItem(Items.SHULKER_SHELL, "🎈 ПРАНК: Полет в космос", Formatting.LIGHT_PURPLE,
                List.of("Подбрасывает напарника левитацией под потолок на 6 секунд!")));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, p) ->
                new ChmoGuiHandler(syncId, playerInventory, inv),
                Text.literal("🧭 Путь до C.H.M.O | " + role + " (Ур. " + currentTier + "/12)")
        ));
    }

    private static ItemStack makeGuiItem(Item item, String title, Formatting color, List<String> loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(title).formatted(color, Formatting.BOLD));
        NbtList lore = new NbtList();
        for (String line : loreLines) {
            lore.add(NbtString.of(Text.Serializer.toJson(Text.literal(line).formatted(Formatting.WHITE))));
        }
        stack.getOrCreateSubNbt("display").put("Lore", lore);
        return stack;
    }

    private static class ChmoGuiHandler extends GenericContainerScreenHandler {
        private final SimpleInventory guiInv;

        public ChmoGuiHandler(int syncId, PlayerInventory playerInventory, SimpleInventory guiInv) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, guiInv, 6);
            this.guiInv = guiInv;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
            if (slotIndex >= 0 && slotIndex < 54) {
                if (playerEntity instanceof ServerPlayerEntity player) {
                    handleMenuClick(player, slotIndex);
                }
                return; // Блокируем взятие предметов из меню
            }
            // Запрещаем выкидывать вечный компас
            if (slotIndex >= 54 && slotIndex < this.slots.size()) {
                Slot s = this.slots.get(slotIndex);
                if (s != null && isChmoCompass(s.getStack())) {
                    return;
                }
            }
            super.onSlotClick(slotIndex, button, actionType, playerEntity);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }
    }

    private static void handleMenuClick(ServerPlayerEntity player, int slot) {
        ChmoState state = ChmoState.getServerState(player.getServer());
        int currentTier = state.playerTiers.getOrDefault(player.getUuid(), 1);
        int kills = state.mobKills.getOrDefault(player.getUuid(), 0);

        if (slot == 0) {
            state.playerRoles.put(player.getUuid(), "Дима");
            state.markDirty();
            player.sendMessage(Text.literal("👦 Ты выбрал роль: ДИМА!").formatted(Formatting.AQUA, Formatting.BOLD), false);
            openChmoMenu(player);
            return;
        }
        if (slot == 1) {
            state.playerRoles.put(player.getUuid(), "Ульяна");
            state.markDirty();
            player.sendMessage(Text.literal("👧 Ты выбрала роль: УЛЬЯНА!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
            openChmoMenu(player);
            return;
        }
        if (slot == 8) {
            player.closeHandledScreen();
            TitleAbilityManager.triggerActiveAbility(player);
            return;
        }

        // Проверка клика по 12 титулам
        int[] tierSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32};
        for (int i = 0; i < tierSlots.length; i++) {
            if (slot == tierSlots[i]) {
                TierDef target = TIERS.get(i);
                if (target.level() <= currentTier) {
                    player.sendMessage(Text.literal("✅ Этот титул и его способность уже разблокированы!").formatted(Formatting.GREEN), false);
                    return;
                }
                if (target.level() > currentTier + 1) {
                    player.sendMessage(Text.literal("🔒 Сначала разблокируй предыдущий титул!").formatted(Formatting.RED), false);
                    return;
                }
                if (kills < target.reqKills()) {
                    player.sendMessage(Text.literal("⚔️ Недостаточно убийств монстров: " + kills + "/" + target.reqKills()).formatted(Formatting.RED), false);
                    return;
                }
                if (!consumeItems(player, target.reqItem(), target.reqCount())) {
                    player.sendMessage(Text.literal("❌ Не хватает ресурсов для титула: нужно " + target.reqCount() + "x " + target.reqItem().getName().getString()).formatted(Formatting.RED), false);
                    return;
                }

                // Повышаем титул!
                state.playerTiers.put(player.getUuid(), target.level());
                state.markDirty();

                player.getInventory().offerOrDrop(new ItemStack(target.rewardItem(), target.rewardCount()));
                player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);

                String role = state.playerRoles.getOrDefault(player.getUuid(), player.getName().getString());
                player.getServer().getPlayerManager().broadcast(
                        Text.literal("🏆 " + role + " получил титул [" + target.title() + "] и способность «" + target.abilityName() + "»!")
                                .formatted(Formatting.GOLD, Formatting.BOLD), false);

                if (target.level() == 12 && state.winnerUuid == null) {
                    state.winnerUuid = player.getUuid();
                    state.markDirty();
                    player.getServer().getPlayerManager().broadcast(
                            Text.literal("👑 АБСОЛЮТНЫЙ ПОБЕДИТЕЛЬ ГОНКИ — " + role + " СТАНОВИТСЯ C.H.M.O! 👑")
                                    .formatted(Formatting.AQUA, Formatting.BOLD), false);
                }

                openChmoMenu(player);
                return;
            }
        }

        ServerPlayerEntity partner = findPartner(player);

        if (slot == 45) {
            player.closeHandledScreen();
            if (partner != null) {
                player.teleport(partner.getServerWorld(), partner.getX(), partner.getY(), partner.getZ(), partner.getYaw(), partner.getPitch());
                player.sendMessage(Text.literal("🌀 Телепортация к напарнику выполнена!").formatted(Formatting.AQUA), true);
            } else {
                player.sendMessage(Text.literal("Напарник сейчас не в сети!").formatted(Formatting.RED), true);
            }
        } else if (slot == 47) {
            player.closeHandledScreen();
            if (partner != null) {
                int dist = (int) Math.sqrt(player.squaredDistanceTo(partner));
                player.sendMessage(Text.literal("📡 Напарник на координатах: X=" + partner.getBlockX() + " Y=" + partner.getBlockY() + " Z=" + partner.getBlockZ() + " (" + dist + " м)").formatted(Formatting.GREEN), false);
            } else {
                player.sendMessage(Text.literal("Напарник не найден на сервере!").formatted(Formatting.RED), true);
            }
        } else if (slot == 49) {
            if (player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                player.sendMessage(Text.literal("👁 Ночное зрение выключено").formatted(Formatting.GRAY), true);
            } else {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 999999, 0, false, false));
                player.sendMessage(Text.literal("👁 Ночное зрение включено!").formatted(Formatting.GOLD), true);
            }
        } else if (slot == 51) {
            player.closeHandledScreen();
            if (partner != null) {
                partner.getServerWorld().playSound(null, partner.getBlockPos(), SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.5f, 1.0f);
                partner.sendMessage(Text.literal("💥 Тссссс... Кажется, сзади крипер!").formatted(Formatting.GREEN), true);
                player.sendMessage(Text.literal("😈 Пранк с крипером отправлен напарнику!").formatted(Formatting.GOLD), true);
            }
        } else if (slot == 53) {
            player.closeHandledScreen();
            if (partner != null) {
                partner.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 120, 2));
                partner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0));
                partner.sendMessage(Text.literal("🎈 Тебя отправили в стратосферу пранком из компаса!").formatted(Formatting.LIGHT_PURPLE), true);
                player.sendMessage(Text.literal("😈 Напарник запущен в воздух!").formatted(Formatting.GOLD), true);
            }
        }
    }

    private static boolean consumeItems(ServerPlayerEntity player, Item item, int count) {
        int available = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(item)) available += s.getCount();
        }
        if (available < count) return false;

        int remaining = count;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(item)) {
                int take = Math.min(remaining, s.getCount());
                s.decrement(take);
                remaining -= take;
            }
        }
        return true;
    }

    private static ServerPlayerEntity findPartner(ServerPlayerEntity player) {
        for (ServerPlayerEntity other : player.getServer().getPlayerManager().getPlayerList()) {
            if (!other.getUuid().equals(player.getUuid())) return other;
        }
        return null;
    }
}
