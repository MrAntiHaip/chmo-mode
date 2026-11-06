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
import net.minecraft.particle.ParticleTypes;
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
            Item reqItem2,
            int reqCount2,
            int reqKills,
            String taskDesc,
            String abilityName,
            String abilityDesc,
            Item rewardItem,
            int rewardCount
    ) {}

    public record ActiveAbilityOption(
            int reqTier,
            Item dyeItem,
            String name,
            String desc,
            Formatting color
    ) {}

    public static final List<ActiveAbilityOption> ACTIVE_ABILITIES = List.of(
            new ActiveAbilityOption(1, Items.LIGHT_BLUE_DYE, "🐟 Рывок Инфузории (1 ур.)",
                    "Быстрый рывок вперед по взгляду", Formatting.AQUA),
            new ActiveAbilityOption(4, Items.YELLOW_DYE, "🦝 Карманник / Турбо-рывок (4 ур.)",
                    "Крадет предмет из рук напарника вблизи или делает мощный рывок", Formatting.YELLOW),
            new ActiveAbilityOption(10, Items.MAGENTA_DYE, "🧲 Грави-пушка: Телекинез (10 ур.)",
                    "Хватает моба или напарника в прицел и швыряет со взрывом", Formatting.LIGHT_PURPLE),
            new ActiveAbilityOption(11, Items.ORANGE_DYE, "⏪ Откат времени: Эхо 5 сек (11 ур.)",
                    "Перематывает твои координаты, ХП и сытость на 5 секунд назад", Formatting.GOLD),
            new ActiveAbilityOption(12, Items.LIME_DYE, "⚡️ Орбитальный удар C.H.M.O (12 ур.)",
                    "Бьет серией молний в точку взгляда и подбрасывает врагов", Formatting.GREEN)
    );

    public static final List<TierDef> TIERS = List.of(
            new TierDef(1, "1. Инфузория", Items.KELP,
                    Items.DIRT, 16, null, 0, 0,
                    "Сдать 16 Земли", "Фотосинтез и рефлекс таракана",
                    "Сытость растет днем/в воде + рывок при уроне", Items.BREAD, 8),
            new TierDef(2, "2. Лошпед", Items.EGG,
                    Items.COBBLESTONE, 64, null, 0, 0,
                    "Сдать 64 Булыжника (1 стак)", "Карманный грабеж кур",
                    "Животные ходят за тобой, курицы несут руду и изумруды", Items.STONE_PICKAXE, 1),
            new TierDef(3, "3. Плесень", Items.SLIME_BALL,
                    Items.COAL, 16, Items.BREAD, 1, 0,
                    "Сдать 16 Угля и 1 Хлеб", "Пузо-батут",
                    "Падение на Shift гасит урон и подбрасывает всех вокруг", Items.TORCH, 32),
            new TierDef(4, "4. Суетолог", Items.SUGAR,
                    Items.IRON_INGOT, 16, Items.REDSTONE, 16, 0,
                    "Сдать 16 Железных слитков и 16 Редстоун пыли", "Пылесос жадности + Карманник",
                    "Магнит лута 9 блоков + активка: кража из рук напарника", Items.IRON_PICKAXE, 1),
            new TierDef(5, "5. Подгорелый", Items.BLAZE_POWDER,
                    Items.COOKED_BEEF, 32, null, 0, 10,
                    "Сдать 32 Стейка и убить 10 враждебных мобов", "Карманная домна",
                    "Руда и еда плавятся прямо в инвентаре во время бега", Items.SHIELD, 1),
            new TierDef(6, "6. Шнырь", Items.ENDER_PEARL,
                    Items.GOLD_BLOCK, 2, null, 0, 20,
                    "Сдать 2 Золотых блока и убить 20 мобов", "Квантовый кусь",
                    "Удары с шансом 25% меняют тебя местами с врагом и лечат", Items.GOLDEN_APPLE, 4),
            new TierDef(7, "7. Гоблин-шахтер", Items.AMETHYST_SHARD,
                    Items.REDSTONE_BLOCK, 5, null, 0, 30,
                    "Сдать 5 Редстоун блоков и убить 30 мобов", "Эхолокация и взрывная кирка",
                    "Руды звенят сквозь стены + камень копается буром", Items.DIAMOND_PICKAXE, 1),
            new TierDef(8, "8. Токсик", Items.PUFFERFISH,
                    Items.LAPIS_LAZULI, 42, null, 0, 40,
                    "Сдать 42 Лазурита и убить 40 мобов", "Реверс снарядов",
                    "Стрелы летят обратно в стрелков, криперы гаснут рядом", Items.BOW, 1),
            new TierDef(9, "9. Абьюзер", Items.TNT,
                    Items.DIAMOND, 24, null, 0, 50,
                    "Сдать 24 Алмаза и убить 50 мобов", "Паразит напарника",
                    "При низком ХП 50% твоего урона переносится на напарника", Items.DIAMOND_SWORD, 1),
            new TierDef(10, "10. Сверхпроводник", Items.LIGHTNING_ROD,
                    Items.OBSIDIAN, 10, Items.NETHERRACK, 32, 50,
                    "Сдать 10 Обсидиана и 32 Незерака", "Грави-пушка (Телекинез)",
                    "Активка компаса хватает мобов/напарника в воздух и швыряет", Items.ENDER_PEARL, 8),
            new TierDef(11, "11. Темпорал", Items.CLOCK,
                    Items.BLAZE_ROD, 6, null, 0, 60,
                    "Сдать 6 Огненных стержней и убить 60 мобов", "Откат времени (Эхо 5 сек)",
                    "Спасает от смерти и лавы перемоткой на 5 секунд назад", Items.TOTEM_OF_UNDYING, 1),
            new TierDef(12, "12. C.H.M.O", Items.NETHER_STAR,
                    Items.ENDER_EYE, 6, null, 0, 70,
                    "Сдать 6 Очей Эндера и убить 70 мобов", "Абсолютный Доминатор",
                    "Бег по воздуху на Shift + активка: Орбитальный удар молниями", Items.NETHERITE_CHESTPLATE, 1)
    );

    public static int getPlayerTier(ServerPlayerEntity player) {
        ChmoState state = ChmoState.getServerState(player.getServer());
        return state.playerTiers.getOrDefault(player.getUuid(), 1);
    }

    public static int getDefaultActiveAbilityForTier(int tier) {
        if (tier >= 12) return 12;
        if (tier >= 11) return 11;
        if (tier >= 10) return 10;
        if (tier >= 4) return 4;
        return 1;
    }

    public static int getSelectedAbility(ServerPlayerEntity player) {
        ChmoState state = ChmoState.getServerState(player.getServer());
        int tier = state.playerTiers.getOrDefault(player.getUuid(), 1);
        int selected = state.selectedAbility.getOrDefault(player.getUuid(), getDefaultActiveAbilityForTier(tier));
        if (selected > tier) {
            selected = getDefaultActiveAbilityForTier(tier);
        }
        return selected;
    }

    @Override
    public void onInitialize() {
        TitleAbilityManager.register();

        // Учет убийств враждебных монстров (общий счетчик)
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (entity instanceof ServerPlayerEntity player && killedEntity instanceof HostileEntity) {
                ChmoState state = ChmoState.getServerState(world.getServer());
                int kills = state.mobKills.getOrDefault(player.getUuid(), 0) + 1;
                state.mobKills.put(player.getUuid(), kills);
                state.markDirty();
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ensureCompass(newPlayer));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 == 0) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    ensureCompass(player);
                }
            }
        });

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
        compass.setCustomName(Text.literal("🧭 Компас Судьбы C.H.M.O (ПКМ — Меню | Shift+ПКМ — Способность)").formatted(Formatting.AQUA, Formatting.BOLD));
        player.getInventory().insertStack(compass);
    }

    public static void openChmoMenu(ServerPlayerEntity player) {
        ChmoState state = ChmoState.getServerState(player.getServer());
        int currentTier = state.playerTiers.getOrDefault(player.getUuid(), 1);
        int kills = state.mobKills.getOrDefault(player.getUuid(), 0);
        String role = state.playerRoles.getOrDefault(player.getUuid(), "Не выбрана");
        int selectedAbil = getSelectedAbility(player);

        SimpleInventory inv = new SimpleInventory(54);

        // ВЕРХНИЙ РЯД (0..8): Выбор роли, Рулетка (3 вида ставок), Статус и запуск способности
        inv.setStack(0, makeGuiItem(Items.DIAMOND_HELMET, "👦 Выбрать роль: ДИМА", Formatting.AQUA,
                List.of("Текущая роль: " + role, "Нажми, чтобы играть за Диму!"), false));
        inv.setStack(1, makeGuiItem(Items.GOLDEN_HELMET, "👧 Выбрать роль: УЛЬЯНА", Formatting.LIGHT_PURPLE,
                List.of("Текущая роль: " + role, "Нажми, чтобы играть за Ульяну!"), false));

        // 3 кнопки РУЛЕТКИ (слоты 2, 3, 5)
        inv.setStack(2, makeGuiItem(Items.COPPER_INGOT, "🎰 РУЛЕТКА БОМЖА (Ставка: 16 Булыжника)", Formatting.GOLD,
                List.of("Попытай удачу за 16 булыжника!",
                        "Шанс выбить: уголь, железо, стейки, хлеб",
                        "Или получить мусор / пинок под зад!"), false));

        inv.setStack(3, makeGuiItem(Items.IRON_INGOT, "🎰 ЖЕЛЕЗНАЯ РУЛЕТКА (Ставка: 4 Железа)", Formatting.AQUA,
                List.of("Серьезная ставка: 4 Железных слитка!",
                        "Призы: Алмазы, Золотые яблоки, Изумруды, Динамит, Редстоун",
                        "Или проигрыш: гнилая плоть, рыба-фугу, палка!"), true));

        inv.setStack(4, makeGuiItem(Items.NETHER_STAR, "📊 Статус: " + TIERS.get(currentTier - 1).title(), Formatting.GOLD,
                List.of("Роль: " + role,
                        "Всего убито монстров: " + kills,
                        "Выбранная способность компаса: тир " + selectedAbil,
                        "Меняй активную способность красителями внизу!"), false));

        inv.setStack(5, makeGuiItem(Items.DIAMOND, "🎰 VIP-РУЛЕТКА КАЗИНО (Ставка: 2 Алмаза)", Formatting.LIGHT_PURPLE,
                List.of("ИГРА ВА-БАНК за 2 Алмаза!",
                        "Джекпот: Тотем бессмертия, Незерит, Зачар. яблоко, Око Эндера, 6 Алмазов!",
                        "Анти-приз: кусок грязи, слепота и полет в воздух!"), true));

        inv.setStack(8, makeGuiItem(Items.BLAZE_ROD, "⚡️ ЮЗАНУТЬ ВЫБРАННУЮ СПОСОБНОСТЬ", Formatting.YELLOW,
                List.of("Сейчас выбрана способность тира: " + selectedAbil,
                        "Нажми сюда или жми Shift + ПКМ компасом в воздухе!"), true));

        // РЯДЫ 2 и 3 (Слоты 10..15 и 19..24) — 12 ступеней прокачки
        int[] tierSlots = {10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24};
        for (int i = 0; i < TIERS.size(); i++) {
            TierDef t = TIERS.get(i);
            boolean unlocked = currentTier >= t.level();
            boolean isNext = currentTier + 1 == t.level();

            Item icon = unlocked ? t.icon() : (isNext ? Items.GOLD_BLOCK : Items.GRAY_DYE);
            Formatting color = unlocked ? Formatting.GREEN : (isNext ? Formatting.YELLOW : Formatting.GRAY);
            String status = unlocked ? "✅ ПОЛУЧЕНО" : (isNext ? "⏳ НАЖМИ, ЧТОБЫ ПРОКАЧАТЬ" : "🔒 ЗАБЛОКИРОВАНО");

            inv.setStack(tierSlots[i], makeGuiItem(icon, t.title() + " [" + status + "]", color, List.of(
                    "📋 Задание: " + t.taskDesc(),
                    "⚔️ Убийств мобов (общие): " + kills + "/" + t.reqKills(),
                    "✨ Способность: " + t.abilityName(),
                    "💡 " + t.abilityDesc(),
                    "🎁 Награда: " + t.rewardCount() + "x " + t.rewardItem().getName().getString()
            ), unlocked));
        }

        // РЯД 5 (Слоты 38..42) — КРАСИТЕЛИ: ПЕРЕКЛЮЧАТЕЛЬ АКТИВНОЙ СПОСОБНОСТИ КОМПАСА
        int[] dyeSlots = {38, 39, 40, 41, 42};
        for (int i = 0; i < ACTIVE_ABILITIES.size(); i++) {
            ActiveAbilityOption opt = ACTIVE_ABILITIES.get(i);
            boolean unlocked = currentTier >= opt.reqTier();
            boolean isSelected = unlocked && (selectedAbil == opt.reqTier());

            Item dyeIcon = unlocked ? opt.dyeItem() : Items.GRAY_DYE;
            String statusTag = isSelected ? " [✅ АКТИВНА В КОМПАСЕ]" : (unlocked ? " [Нажми для выбора]" : " [🔒 Нужен " + opt.reqTier() + " тир]");
            Formatting titleColor = isSelected ? Formatting.GREEN : (unlocked ? opt.color() : Formatting.DARK_GRAY);

            inv.setStack(dyeSlots[i], makeGuiItem(dyeIcon, opt.name() + statusTag, titleColor, List.of(
                    "💡 Что делает: " + opt.desc(),
                    unlocked ? (isSelected ? "👉 Сейчас работает при Shift+ПКМ компасом!" : "Нажми, чтобы привязать эту способность к компасу!")
                             : "Прокачайся до " + opt.reqTier() + " титула, чтобы открыть!"
            ), isSelected));
        }

        // РЯД 6 (45..53): Кооп-функции и пранки для Димы и Ульяны
        inv.setStack(45, makeGuiItem(Items.ENDER_PEARL, "🌀 Телепорт к напарнику", Formatting.AQUA,
                List.of("Мгновенно переносит тебя к Диме/Ульяне"), false));
        inv.setStack(47, makeGuiItem(Items.SPYGLASS, "📡 Радар напарника", Formatting.GREEN,
                List.of("Показывает точные координаты и дистанцию до второго игрока"), false));
        inv.setStack(49, makeGuiItem(Items.GOLDEN_CARROT, "👁 Ночное зрение (Вкл/Выкл)", Formatting.GOLD,
                List.of("Бесплатная подсветка пещер и темноты"), false));
        inv.setStack(51, makeGuiItem(Items.CREEPER_HEAD, "💣 ПРАНК: Фейковый Крипер", Formatting.RED,
                List.of("Включает за спиной напарника шипение крипера!"), false));
        inv.setStack(53, makeGuiItem(Items.SHULKER_SHELL, "🎈 ПРАНК: Полет в космос", Formatting.LIGHT_PURPLE,
                List.of("Подбрасывает напарника левитацией под потолок на 6 секунд!"), false));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, p) ->
                new ChmoGuiHandler(syncId, playerInventory, inv),
                Text.literal("🧭 C.H.M.O | " + role + " (Тир " + currentTier + "/12 | Ульта: " + selectedAbil + ")")
        ));
    }

    private static ItemStack makeGuiItem(Item item, String title, Formatting color, List<String> loreLines, boolean glint) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(title).formatted(color, Formatting.BOLD));
        NbtList lore = new NbtList();
        for (String line : loreLines) {
            lore.add(NbtString.of(Text.Serializer.toJson(Text.literal(line).formatted(Formatting.WHITE))));
        }
        stack.getOrCreateSubNbt("display").put("Lore", lore);
        if (glint) {
            stack.addEnchantment(Enchantments.UNBREAKING, 1);
            stack.getOrCreateNbt().putInt("HideFlags", 1);
        }
        return stack;
    }

    private static class ChmoGuiHandler extends GenericContainerScreenHandler {
        public ChmoGuiHandler(int syncId, PlayerInventory playerInventory, SimpleInventory guiInv) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, guiInv, 6);
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
            if (slotIndex >= 0 && slotIndex < 54) {
                if (playerEntity instanceof ServerPlayerEntity player) {
                    handleMenuClick(player, slotIndex);
                }
                return;
            }
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

        // РУЛЕТКИ (Слоты 2, 3, 5)
        if (slot == 2) {
            spinRoulette(player, 1);
            return;
        }
        if (slot == 3) {
            spinRoulette(player, 2);
            return;
        }
        if (slot == 5) {
            spinRoulette(player, 3);
            return;
        }

        if (slot == 8) {
            player.closeHandledScreen();
            TitleAbilityManager.triggerActiveAbility(player);
            return;
        }

        // Переключение активной способности компаса по клику на красители (слоты 38..42)
        int[] dyeSlots = {38, 39, 40, 41, 42};
        for (int i = 0; i < dyeSlots.length; i++) {
            if (slot == dyeSlots[i]) {
                ActiveAbilityOption opt = ACTIVE_ABILITIES.get(i);
                if (currentTier < opt.reqTier()) {
                    player.sendMessage(Text.literal("🔒 Эта способность откроется только на " + opt.reqTier() + " титуле!").formatted(Formatting.RED), false);
                    player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    return;
                }
                state.selectedAbility.put(player.getUuid(), opt.reqTier());
                state.markDirty();
                player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0f, 1.5f);
                player.sendMessage(Text.literal("✨ В компас установлена способность: " + opt.name()).formatted(Formatting.GREEN, Formatting.BOLD), false);
                openChmoMenu(player);
                return;
            }
        }

        // Проверка клика по 12 титулам (слоты 10..15 и 19..24)
        int[] tierSlots = {10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24};
        for (int i = 0; i < tierSlots.length; i++) {
            if (slot == tierSlots[i]) {
                TierDef target = TIERS.get(i);
                if (target.level() <= currentTier) {
                    player.sendMessage(Text.literal("✅ Этот титул уже разблокирован!").formatted(Formatting.GREEN), false);
                    return;
                }
                if (target.level() > currentTier + 1) {
                    player.sendMessage(Text.literal("🔒 Сначала разблокируй предыдущий титул!").formatted(Formatting.RED), false);
                    return;
                }
                if (kills < target.reqKills()) {
                    player.sendMessage(Text.literal("⚔️ Недостаточно общих убийств враждебных мобов: " + kills + "/" + target.reqKills()).formatted(Formatting.RED), false);
                    return;
                }
                if (!hasItems(player, target.reqItem(), target.reqCount())) {
                    player.sendMessage(Text.literal("❌ Не хватает ресурсов: нужно " + target.reqCount() + "x " + target.reqItem().getName().getString()).formatted(Formatting.RED), false);
                    return;
                }
                if (target.reqItem2() != null && !hasItems(player, target.reqItem2(), target.reqCount2())) {
                    player.sendMessage(Text.literal("❌ Не хватает второго ресурса: нужно " + target.reqCount2() + "x " + target.reqItem2().getName().getString()).formatted(Formatting.RED), false);
                    return;
                }

                // Списываем оба ресурса после успешной проверки
                consumeItems(player, target.reqItem(), target.reqCount());
                if (target.reqItem2() != null) {
                    consumeItems(player, target.reqItem2(), target.reqCount2());
                }

                // Повышаем титул!
                state.playerTiers.put(player.getUuid(), target.level());
                if (target.level() == 4 || target.level() == 10 || target.level() == 11 || target.level() == 12) {
                    state.selectedAbility.put(player.getUuid(), target.level());
                }
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

    private static void spinRoulette(ServerPlayerEntity player, int mode) {
        var world = player.getServerWorld();
        var random = world.random;

        if (mode == 1) {
            if (!consumeItems(player, Items.COBBLESTONE, 16)) {
                player.sendMessage(Text.literal("🎰 Для Рулетки Бомжа нужно 16 Булыжника в инвентаре!").formatted(Formatting.RED), false);
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0f, 1.0f);
                return;
            }
            int roll = random.nextInt(100);
            if (roll < 15) {
                ItemStack prize = new ItemStack(Items.IRON_INGOT, 4);
                player.getInventory().offerOrDrop(prize);
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.3f);
                player.sendMessage(Text.literal("🎰 КУШ БОМЖА! Ты выиграл 4x Железных слитка!").formatted(Formatting.GREEN, Formatting.BOLD), false);
            } else if (roll < 50) {
                ItemStack[] good = {
                        new ItemStack(Items.COAL, 12),
                        new ItemStack(Items.COOKED_BEEF, 8),
                        new ItemStack(Items.BREAD, 6),
                        new ItemStack(Items.TORCH, 24)
                };
                ItemStack prize = good[random.nextInt(good.length)];
                player.getInventory().offerOrDrop(prize);
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
                player.sendMessage(Text.literal("🎰 Неплохо! Выпало: " + prize.getCount() + "x " + prize.getName().getString()).formatted(Formatting.YELLOW), false);
            } else {
                ItemStack[] trash = {
                        new ItemStack(Items.DIRT, 1),
                        new ItemStack(Items.ROTTEN_FLESH, 2),
                        new ItemStack(Items.STICK, 1),
                        new ItemStack(Items.DEAD_BUSH, 1)
                };
                ItemStack prize = trash[random.nextInt(trash.length)];
                player.getInventory().offerOrDrop(prize);
                player.addVelocity(0, 0.6, 0);
                player.velocityModified = true;
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_HURT, SoundCategory.PLAYERS, 1.0f, 0.7f);
                player.sendMessage(Text.literal("🎰 СКАМ! Вместо приза выпал хлам: " + prize.getName().getString()).formatted(Formatting.RED), false);
            }

        } else if (mode == 2) {
            if (!consumeItems(player, Items.IRON_INGOT, 4)) {
                player.sendMessage(Text.literal("🎰 Для Железной Рулетки нужно 4 Железных слитка!").formatted(Formatting.RED), false);
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0f, 1.0f);
                return;
            }
            int roll = random.nextInt(100);
            if (roll < 20) {
                ItemStack[] jackpots = {
                        new ItemStack(Items.DIAMOND, 3),
                        new ItemStack(Items.GOLDEN_APPLE, 3),
                        new ItemStack(Items.GOLD_BLOCK, 1),
                        new ItemStack(Items.REDSTONE_BLOCK, 3)
                };
                ItemStack prize = jackpots[random.nextInt(jackpots.length)];
                player.getInventory().offerOrDrop(prize);
                world.playSound(null, player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.2f);
                player.getServer().getPlayerManager().broadcast(
                        Text.literal("🎰 " + player.getName().getString() + " выбил в Железной Рулетке: " + prize.getCount() + "x " + prize.getName().getString() + "!")
                                .formatted(Formatting.AQUA, Formatting.BOLD), false);
            } else if (roll < 55) {
                ItemStack[] mid = {
                        new ItemStack(Items.IRON_INGOT, 6),
                        new ItemStack(Items.LAPIS_LAZULI, 16),
                        new ItemStack(Items.REDSTONE, 24),
                        new ItemStack(Items.TNT, 4),
                        new ItemStack(Items.COOKED_BEEF, 16)
                };
                ItemStack prize = mid[random.nextInt(mid.length)];
                player.getInventory().offerOrDrop(prize);
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
                player.sendMessage(Text.literal("🎰 Выигрыш: " + prize.getCount() + "x " + prize.getName().getString()).formatted(Formatting.GREEN), false);
            } else {
                ItemStack[] trash = {
                        new ItemStack(Items.PUFFERFISH, 1),
                        new ItemStack(Items.POISONOUS_POTATO, 2),
                        new ItemStack(Items.ROTTEN_FLESH, 5),
                        new ItemStack(Items.WOODEN_HOE, 1)
                };
                ItemStack prize = trash[random.nextInt(trash.length)];
                player.getInventory().offerOrDrop(prize);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0));
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PUFFER_FISH_BLOW_UP, SoundCategory.PLAYERS, 1.0f, 0.8f);
                player.sendMessage(Text.literal("🎰 НЕ ПОВЕЗЛО! Тебе выпала лютая дичь: " + prize.getName().getString() + " + тошнота!").formatted(Formatting.RED), false);
            }

        } else if (mode == 3) {
            if (!consumeItems(player, Items.DIAMOND, 2)) {
                player.sendMessage(Text.literal("🎰 Для VIP-Рулетки нужно 2 Алмаза в инвентаре!").formatted(Formatting.RED), false);
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0f, 1.0f);
                return;
            }
            int roll = random.nextInt(100);
            if (roll < 25) {
                ItemStack[] mega = {
                        new ItemStack(Items.NETHERITE_INGOT, 1),
                        new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1),
                        new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                        new ItemStack(Items.DIAMOND, 6),
                        new ItemStack(Items.ENDER_EYE, 2),
                        new ItemStack(Items.BLAZE_ROD, 3)
                };
                ItemStack prize = mega[random.nextInt(mega.length)];
                player.getInventory().offerOrDrop(prize);
                world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.2);
                world.playSound(null, player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.2f, 1.0f);
                player.getServer().getPlayerManager().broadcast(
                        Text.literal("💎 ДЖЕКПОТ КАЗИНО! " + player.getName().getString() + " поставил 2 алмаза и выбил " + prize.getCount() + "x " + prize.getName().getString() + "!")
                                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
            } else if (roll < 55) {
                ItemStack[] mid = {
                        new ItemStack(Items.DIAMOND, 3),
                        new ItemStack(Items.OBSIDIAN, 8),
                        new ItemStack(Items.GOLD_BLOCK, 2),
                        new ItemStack(Items.GOLDEN_APPLE, 4)
                };
                ItemStack prize = mid[random.nextInt(mid.length)];
                player.getInventory().offerOrDrop(prize);
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
                player.sendMessage(Text.literal("🎰 Окуп! Ты получил: " + prize.getCount() + "x " + prize.getName().getString()).formatted(Formatting.GREEN), false);
            } else {
                player.getInventory().offerOrDrop(new ItemStack(Items.DIRT, 1));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 40, 3));
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8f, 0.7f);
                player.getServer().getPlayerManager().broadcast(
                        Text.literal("🤡 " + player.getName().getString() + " слил 2 Алмаза в VIP-Рулетке и получил 1 Землю!")
                                .formatted(Formatting.RED), false);
            }
        }
    }

    private static boolean hasItems(ServerPlayerEntity player, Item item, int count) {
        if (item == null || count <= 0) return true;
        int available = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(item)) available += s.getCount();
        }
        return available >= count;
    }

    private static boolean consumeItems(ServerPlayerEntity player, Item item, int count) {
        if (!hasItems(player, item, count)) return false;

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
