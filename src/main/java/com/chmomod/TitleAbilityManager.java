package com.chmomod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class TitleAbilityManager {

    private static final Map<UUID, Integer> grabbedEntities = new HashMap<>();
    private static final Map<UUID, Deque<PlayerSnapshot>> timeHistory = new HashMap<>();
    private static final Map<UUID, Long> abilityCooldowns = new HashMap<>();
    private static final Map<GlobalBlockPos, Long> tempBlocks = new HashMap<>();

    public record PlayerSnapshot(ServerWorld world, Vec3d pos, float health, int food, float yaw, float pitch) {}
    public record GlobalBlockPos(ServerWorld world, BlockPos pos) {}

    public static int getPlayerTier(ServerPlayerEntity player) {
        return ChmoMod.getPlayerTier(player);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TitleAbilityManager::onServerTick);

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            int tier = getPlayerTier(player);
            ServerWorld world = player.getServerWorld();

            // [Титул 3] Пузо-батут: отмена урона от падения на Shift и подбрасывание всех вокруг
            if (tier >= 3 && source.isOf(DamageTypes.FALL) && player.isSneaking()) {
                double bounce = Math.min(2.2, Math.sqrt(amount) * 0.35);
                player.setVelocity(player.getVelocity().x, bounce, player.getVelocity().z);
                player.velocityModified = true;

                world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 35, 1.5, 0.2, 1.5, 0.1);
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SLIME_JUMP, SoundCategory.PLAYERS, 1.5f, 0.8f);

                for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(4.5), e -> e != player)) {
                    target.addVelocity(0, bounce * 0.85, 0);
                    target.velocityModified = true;
                }
                return false;
            }

            // [Титул 11] Временная петля: спасение от смертельного удара
            if (tier >= 11 && player.getHealth() - amount <= 0.0f) {
                if (tryRewindTime(player, true)) {
                    return false;
                }
            }

            // [Титул 1] Рефлекс таракана: рывок вперед при получении урона
            if (tier >= 1 && amount >= 1.0f && !source.isOf(DamageTypes.FALL)) {
                Vec3d look = player.getRotationVector();
                player.addVelocity(look.x * 0.7, 0.25, look.z * 0.7);
                player.velocityModified = true;
                world.spawnParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.5, player.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
            }

            // [Титул 5] Подгорание: атакующий загорается
            if (tier >= 5 && source.getAttacker() instanceof LivingEntity attacker) {
                attacker.setOnFireFor(5);
                world.spawnParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 1, player.getZ(), 8, 0.4, 0.4, 0.4, 0.1);
            }

            // [Титул 9] Паразит напарника: при ХП <= 8 делим урон с напарником
            if (tier >= 9 && player.getHealth() <= 8.0f && amount > 1.5f && !source.isOf(DamageTypes.GENERIC)) {
                ServerPlayerEntity partner = findPartner(player);
                if (partner != null && partner.isAlive() && partner.getHealth() > 4.0f) {
                    float shared = amount * 0.5f;
                    partner.damage(world.getDamageSources().generic(), shared);
                    partner.sendMessage(Text.literal("⚡️ " + player.getName().getString() + " перекинул на тебя половину урона!").formatted(Formatting.RED), true);
                    player.heal(shared);
                }
            }

            return true;
        });

        // [Титул 6] Квантовый кусь (свап местами при ударе с шансом 25%)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;

            int tier = getPlayerTier(serverPlayer);
            if (tier >= 6 && world.random.nextFloat() < 0.25f) {
                Vec3d playerPos = serverPlayer.getPos();
                Vec3d targetPos = target.getPos();

                serverPlayer.teleport((ServerWorld) world, targetPos.x, targetPos.y, targetPos.z, serverPlayer.getYaw(), serverPlayer.getPitch());
                target.teleport(playerPos.x, playerPos.y, playerPos.z);

                serverPlayer.heal(2.0f);
                serverPlayer.getHungerManager().add(2, 0.5f);

                ((ServerWorld) world).spawnParticles(ParticleTypes.PORTAL, playerPos.x, playerPos.y + 1, playerPos.z, 25, 0.4, 0.8, 0.4, 0.2);
                world.playSound(null, serverPlayer.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.4f);
                serverPlayer.sendMessage(Text.literal("🌀 Квантовый кусь! Вы поменялись местами!").formatted(Formatting.LIGHT_PURPLE), true);
            }
            return ActionResult.PASS;
        });

        // [Титул 7] Эхолокация руд и взрывной бур
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) return;
            int tier = getPlayerTier(serverPlayer);

            if (tier >= 7 && (state.isOf(Blocks.STONE) || state.isOf(Blocks.DEEPSLATE))) {
                ServerWorld sWorld = (ServerWorld) world;
                BlockPos.iterate(pos.add(-8, -8, -8), pos.add(8, 8, 8)).forEach(checkPos -> {
                    Block b = sWorld.getBlockState(checkPos).getBlock();
                    if (isValuableOre(b)) {
                        sWorld.spawnParticles(serverPlayer, ParticleTypes.END_ROD, true,
                                checkPos.getX() + 0.5, checkPos.getY() + 0.5, checkPos.getZ() + 0.5,
                                3, 0.1, 0.1, 0.1, 0.01);
                        sWorld.playSound(null, checkPos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.2f, 1.5f);
                    }
                });

                for (BlockPos offset : new BlockPos[]{pos.up(), pos.down(), pos.north(), pos.south(), pos.east(), pos.west()}) {
                    Block targetBlock = sWorld.getBlockState(offset).getBlock();
                    if (targetBlock == Blocks.STONE || targetBlock == Blocks.DEEPSLATE) {
                        sWorld.breakBlock(offset, true, serverPlayer);
                    }
                }
            }
        });
    }

    private static void onServerTick(MinecraftServer server) {
        long ticks = server.getTicks();

        Iterator<Map.Entry<GlobalBlockPos, Long>> it = tempBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GlobalBlockPos, Long> entry = it.next();
            if (ticks >= entry.getValue()) {
                GlobalBlockPos gPos = entry.getKey();
                if (gPos.world().getBlockState(gPos.pos()).isOf(Blocks.CYAN_STAINED_GLASS)) {
                    gPos.world().setBlockState(gPos.pos(), Blocks.AIR.getDefaultState());
                }
                it.remove();
            }
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            int tier = getPlayerTier(player);
            ServerWorld world = player.getServerWorld();

            // [Титул 1] Фотосинтез
            if (tier >= 1 && ticks % 80 == 0) {
                if ((world.isDay() && world.isSkyVisible(player.getBlockPos())) || player.isTouchingWater()) {
                    player.getHungerManager().add(1, 0.2f);
                }
            }

            // [Титул 2] Куриная мафия
            if (tier >= 2) {
                if (ticks % 20 == 0) {
                    for (AnimalEntity animal : world.getEntitiesByClass(AnimalEntity.class, player.getBoundingBox().expand(8.0), a -> true)) {
                        animal.getNavigation().startMovingTo(player, 1.25);
                    }
                }
                if (ticks % 200 == 0) {
                    List<ChickenEntity> chickens = world.getEntitiesByClass(ChickenEntity.class, player.getBoundingBox().expand(8.0), c -> true);
                    if (!chickens.isEmpty()) {
                        ChickenEntity c = chickens.get(world.random.nextInt(chickens.size()));
                        Item[] loot = {Items.IRON_INGOT, Items.COAL, Items.GOLD_NUGGET, Items.EMERALD, Items.GUNPOWDER, Items.COOKED_CHICKEN};
                        c.dropItem(loot[world.random.nextInt(loot.length)]);
                        world.playSound(null, c.getBlockPos(), SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.NEUTRAL, 1.0f, 0.7f);
                    }
                }
            }

            // [Титул 4] Пылесос жадности
            if (tier >= 4) {
                Box magnetBox = player.getBoundingBox().expand(9.0);
                for (Entity itemOrXp : world.getOtherEntities(player, magnetBox, e -> e instanceof ItemEntity || e instanceof ExperienceOrbEntity)) {
                    Vec3d pull = player.getPos().add(0, 0.5, 0).subtract(itemOrXp.getPos()).normalize().multiply(0.35);
                    itemOrXp.setVelocity(itemOrXp.getVelocity().multiply(0.5).add(pull));
                    itemOrXp.velocityModified = true;
                }
            }

            // [Титул 5] Карманная домна
            if (tier >= 5 && player.isSprinting() && ticks % 60 == 0) {
                smeltOneInInventory(player);
            }

            // [Титул 8] Реверс снарядов и усмирение криперов
            if (tier >= 8) {
                for (ProjectileEntity proj : world.getEntitiesByClass(ProjectileEntity.class, player.getBoundingBox().expand(3.5), p -> p.getOwner() != player)) {
                    Vec3d vel = proj.getVelocity();
                    if (vel.lengthSquared() > 0.01) {
                        proj.setVelocity(vel.multiply(-1.5));
                        proj.setOwner(player);
                        proj.velocityModified = true;
                        world.spawnParticles(ParticleTypes.CRIT, proj.getX(), proj.getY(), proj.getZ(), 5, 0.1, 0.1, 0.1, 0.1);
                        world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.5f);
                    }
                }
                for (CreeperEntity creeper : world.getEntitiesByClass(CreeperEntity.class, player.getBoundingBox().expand(4.5), c -> true)) {
                    creeper.setFuseSpeed(-1);
                }
            }

            // [Титул 10] Телекинез
            if (grabbedEntities.containsKey(player.getUuid())) {
                Entity grabbed = world.getEntityById(grabbedEntities.get(player.getUuid()));
                if (grabbed == null || !grabbed.isAlive() || grabbed.squaredDistanceTo(player) > 100) {
                    grabbedEntities.remove(player.getUuid());
                } else {
                    Vec3d targetPos = player.getEyePos().add(player.getRotationVector().multiply(3.8));
                    Vec3d delta = targetPos.subtract(grabbed.getPos());
                    grabbed.setVelocity(delta.multiply(0.45));
                    grabbed.fallDistance = 0;
                    grabbed.velocityModified = true;
                    world.spawnParticles(ParticleTypes.PORTAL, grabbed.getX(), grabbed.getY() + 0.5, grabbed.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
                }
            }

            // [Титул 11] Запись истории времени
            if (tier >= 11 && ticks % 10 == 0) {
                Deque<PlayerSnapshot> deque = timeHistory.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
                if (deque.size() >= 10) deque.pollFirst();
                deque.addLast(new PlayerSnapshot(world, player.getPos(), player.getHealth(), player.getHungerManager().getFoodLevel(), player.getYaw(), player.getPitch()));
            }

            // [Титул 12 — C.H.M.O] Небесная поступь и аура тотема
            if (tier >= 12) {
                double angle = (ticks % 40) * (Math.PI * 2 / 40);
                world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        player.getX() + Math.cos(angle) * 0.8,
                        player.getY() + 2.1,
                        player.getZ() + Math.sin(angle) * 0.8,
                        1, 0, 0, 0, 0);

                if (player.isSneaking() && !player.isOnGround()) {
                    BlockPos below = player.getBlockPos().down();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockPos p = below.add(dx, 0, dz);
                            if (world.getBlockState(p).isAir()) {
                                world.setBlockState(p, Blocks.CYAN_STAINED_GLASS.getDefaultState());
                                tempBlocks.put(new GlobalBlockPos(world, p), ticks + 60);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void triggerActiveAbility(ServerPlayerEntity player) {
        int tier = getPlayerTier(player);
        ServerWorld world = player.getServerWorld();
        long now = world.getServer().getTicks();

        if (grabbedEntities.containsKey(player.getUuid())) {
            Entity grabbed = world.getEntityById(grabbedEntities.remove(player.getUuid()));
            if (grabbed != null) {
                Vec3d launch = player.getRotationVector().multiply(2.8).add(0, 0.5, 0);
                grabbed.setVelocity(launch);
                grabbed.velocityModified = true;
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8f, 1.4f);
                player.sendMessage(Text.literal("🚀 Объект запущен в полет!").formatted(Formatting.GOLD), true);
            }
            return;
        }

        long cdEnd = abilityCooldowns.getOrDefault(player.getUuid(), 0L);
        if (now < cdEnd) {
            long secLeft = (cdEnd - now) / 20 + 1;
            player.sendMessage(Text.literal("⏳ Способность перезаряжается: " + secLeft + " сек.").formatted(Formatting.RED), true);
            return;
        }

        if (tier >= 12) {
            HitResult hit = player.raycast(45.0, 0.0f, false);
            Vec3d target = hit.getPos();
            for (int i = 0; i < 3; i++) {
                LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
                if (lightning != null) {
                    lightning.refreshPositionAfterTeleport(target.x + (world.random.nextDouble() - 0.5) * 3, target.y, target.z + (world.random.nextDouble() - 0.5) * 3);
                    world.spawnEntity(lightning);
                }
            }
            for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, Box.of(target, 8, 8, 8), ent -> ent != player)) {
                e.addVelocity(0, 1.6, 0);
                e.velocityModified = true;
            }
            player.sendMessage(Text.literal("⚡️ ОРБИТАЛЬНЫЙ УДАР C.H.M.O!").formatted(Formatting.AQUA, Formatting.BOLD), true);
            abilityCooldowns.put(player.getUuid(), now + 200);

        } else if (tier == 11) {
            if (tryRewindTime(player, false)) {
                abilityCooldowns.put(player.getUuid(), now + 400);
            }

        } else if (tier == 10) {
            Vec3d lookPos = player.getEyePos().add(player.getRotationVector().multiply(3.5));
            List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, Box.of(lookPos, 4, 4, 4), e -> e != player);
            if (!targets.isEmpty()) {
                LivingEntity target = targets.get(0);
                grabbedEntities.put(player.getUuid(), target.getId());
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.8f);
                player.sendMessage(Text.literal("🧲 Захвачен: " + target.getName().getString() + "! Нажми ещё раз для броска!").formatted(Formatting.AQUA), true);
            } else {
                player.sendMessage(Text.literal("Наведи прицел поближе к мобу или напарнику!").formatted(Formatting.GRAY), true);
            }

        } else if (tier >= 4) {
            ServerPlayerEntity partner = findPartner(player);
            if (partner != null && partner.squaredDistanceTo(player) <= 144) {
                ItemStack held = partner.getMainHandStack();
                if (!held.isEmpty() && !held.isOf(Items.COMPASS) && !held.isOf(Items.RECOVERY_COMPASS)) {
                    ItemStack stolen = held.split(1);
                    player.getInventory().offerOrDrop(stolen);
                    world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0f, 0.6f);
                    player.sendMessage(Text.literal("🦝 Ты стащил " + stolen.getName().getString() + " у напарника!").formatted(Formatting.GREEN), true);
                    partner.sendMessage(Text.literal("🦝 " + player.getName().getString() + " стащил предмет у тебя из рук!").formatted(Formatting.YELLOW), true);
                    abilityCooldowns.put(player.getUuid(), now + 300);
                } else {
                    player.sendMessage(Text.literal("У напарника в руке пусто (или зажат компас)!").formatted(Formatting.GRAY), true);
                }
            } else {
                Vec3d dash = player.getRotationVector().multiply(1.6).add(0, 0.35, 0);
                player.setVelocity(dash);
                player.velocityModified = true;
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 1.0f, 1.4f);
                player.sendMessage(Text.literal("💨 Турбо-рывок Суетолога!").formatted(Formatting.YELLOW), true);
                abilityCooldowns.put(player.getUuid(), now + 120);
            }
        } else {
            Vec3d dash = player.getRotationVector().multiply(1.2).add(0, 0.3, 0);
            player.setVelocity(dash);
            player.velocityModified = true;
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SLIME_JUMP, SoundCategory.PLAYERS, 1.0f, 1.2f);
            player.sendMessage(Text.literal("🐟 Рывок Инфузории! (Прокачай титул для новых активок)").formatted(Formatting.AQUA), true);
            abilityCooldowns.put(player.getUuid(), now + 100);
        }
    }

    private static boolean tryRewindTime(ServerPlayerEntity player, boolean isDeathSave) {
        long now = player.getServer().getTicks();
        long cd = abilityCooldowns.getOrDefault(player.getUuid(), 0L);
        if (isDeathSave && now < cd) return false;

        Deque<PlayerSnapshot> deque = timeHistory.get(player.getUuid());
        if (deque == null || deque.isEmpty()) return false;

        PlayerSnapshot snap = deque.peekFirst();
        player.teleport(snap.world(), snap.pos().x, snap.pos().y, snap.pos().z, snap.yaw(), snap.pitch());
        player.setHealth(Math.max(6.0f, snap.health()));
        player.getHungerManager().setFoodLevel(snap.food());
        player.extinguish();
        player.fallDistance = 0;

        snap.world().spawnParticles(ParticleTypes.REVERSE_PORTAL, snap.pos().x, snap.pos().y + 1, snap.pos().z, 50, 0.5, 1.0, 0.5, 0.15);
        snap.world().playSound(null, player.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.2f, 0.6f);
        player.sendMessage(Text.literal("⏪ ВРЕМЯ ПЕРЕМОТАНО НА 5 СЕКУНД НАЗАД!").formatted(Formatting.GOLD, Formatting.BOLD), true);

        abilityCooldowns.put(player.getUuid(), now + 900);
        return true;
    }

    private static void smeltOneInInventory(ServerPlayerEntity player) {
        Map<Item, Item> recipes = Map.of(
                Items.RAW_IRON, Items.IRON_INGOT,
                Items.RAW_GOLD, Items.GOLD_INGOT,
                Items.RAW_COPPER, Items.COPPER_INGOT,
                Items.BEEF, Items.COOKED_BEEF,
                Items.PORKCHOP, Items.COOKED_PORKCHOP,
                Items.CHICKEN, Items.COOKED_CHICKEN,
                Items.POTATO, Items.BAKED_POTATO,
                Items.SAND, Items.GLASS
        );
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (recipes.containsKey(stack.getItem())) {
                Item result = recipes.get(stack.getItem());
                stack.decrement(1);
                player.getInventory().offerOrDrop(new ItemStack(result));
                player.getServerWorld().spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.8, player.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
                player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.4f, 1.6f);
                return;
            }
        }
    }

    private static boolean isValuableOre(Block b) {
        return b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE
                || b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE
                || b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE
                || b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE;
    }

    private static ServerPlayerEntity findPartner(ServerPlayerEntity player) {
        for (ServerPlayerEntity other : player.getServer().getPlayerManager().getPlayerList()) {
            if (!other.getUuid().equals(player.getUuid())) return other;
        }
        return null;
    }
}
