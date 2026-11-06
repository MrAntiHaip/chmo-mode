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

    public static int getPlayerTier(ServerPlayerEntity player) { return ChmoMod.getPlayerTier(player); }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TitleAbilityManager::onServerTick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            int tier = getPlayerTier(player); ServerWorld world = player.getServerWorld();
            if (tier >= 3 && source.isOf(DamageTypes.FALL) && player.isSneaking()) {
                double bounce = Math.min(2.2, Math.sqrt(amount) * 0.35); player.setVelocity(player.getVelocity().x, bounce, player.getVelocity().z);
                world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 35, 1.5, 0.2, 1.5, 0.1);
                for (LivingEntity t : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(4.5), e -> e != player)) { t.addVelocity(0, bounce * 0.85, 0); }
                return false;
            }
            if (tier >= 11 && player.getHealth() - amount <= 0.0f && tryRewindTime(player, true)) return false;
            if (tier >= 1 && amount >= 1.0f && !source.isOf(DamageTypes.FALL)) { Vec3d l = player.getRotationVector(); player.addVelocity(l.x * 0.7, 0.25, l.z * 0.7); world.spawnParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY()+0.5, player.getZ(), 10, 0.3, 0.3, 0.3, 0.05); }
            if (tier >= 5 && source.getAttacker() instanceof LivingEntity a) a.setOnFireFor(5);
            if (tier >= 9 && player.getHealth() <= 8.0f && amount > 1.5f) { ServerPlayerEntity p = findPartner(player); if (p != null && p.getHealth() > 4.0f) { p.damage(world.getDamageSources().generic(), amount * 0.5f); player.heal(amount * 0.5f); } }
            return true;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp) || !(entity instanceof LivingEntity t)) return ActionResult.PASS;
            if (getPlayerTier(sp) >= 6 && world.random.nextFloat() < 0.25f) {
                Vec3d pp = sp.getPos(), tp = t.getPos(); sp.teleport((ServerWorld)world, tp.x, tp.y, tp.z, sp.getYaw(), sp.getPitch()); t.teleport(pp.x, pp.y, pp.z); sp.heal(2.0f);
                world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.4f);
            } return ActionResult.PASS;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return;
            if (getPlayerTier(sp) >= 7 && (state.isOf(Blocks.STONE) || state.isOf(Blocks.DEEPSLATE))) {
                ServerWorld sw = (ServerWorld) world;
                BlockPos.iterate(pos.add(-8,-8,-8), pos.add(8,8,8)).forEach(cp -> { if (isValuableOre(sw.getBlockState(cp).getBlock())) sw.spawnParticles(sp, ParticleTypes.END_ROD, true, cp.getX()+0.5, cp.getY()+0.5, cp.getZ()+0.5, 3, 0.1, 0.1, 0.1, 0.01); });
                for (BlockPos o : new BlockPos[]{pos.up(), pos.down(), pos.north(), pos.south(), pos.east(), pos.west()}) if (sw.getBlockState(o).getBlock() == Blocks.STONE || sw.getBlockState(o).getBlock() == Blocks.DEEPSLATE) sw.breakBlock(o, true, sp);
            }
        });
    }

    private static void onServerTick(MinecraftServer server) {
        long ticks = server.getTicks();
        tempBlocks.entrySet().removeIf(e -> { if (ticks >= e.getValue()) { if (e.getKey().world().getBlockState(e.getKey().pos()).isOf(Blocks.CYAN_STAINED_GLASS)) e.getKey().world().setBlockState(e.getKey().pos(), Blocks.AIR.getDefaultState()); return true; } return false; });
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            int tier = getPlayerTier(player); ServerWorld world = player.getServerWorld();
            if (tier >= 1 && ticks % 80 == 0 && ((world.isDay() && world.isSkyVisible(player.getBlockPos())) || player.isTouchingWater())) player.getHungerManager().add(1, 0.2f);
            if (tier >= 2) {
                if (ticks % 20 == 0) for (AnimalEntity a : world.getEntitiesByClass(AnimalEntity.class, player.getBoundingBox().expand(8.0), x -> true)) a.getNavigation().startMovingTo(player, 1.25);
                if (ticks % 200 == 0) { List<ChickenEntity> c = world.getEntitiesByClass(ChickenEntity.class, player.getBoundingBox().expand(8.0), x -> true); if (!c.isEmpty()) c.get(world.random.nextInt(c.size())).dropItem(new Item[]{Items.IRON_INGOT, Items.COAL, Items.EMERALD}[world.random.nextInt(3)]); }
            }
            if (tier >= 4) for (Entity e : world.getOtherEntities(player, player.getBoundingBox().expand(9.0), x -> x instanceof ItemEntity || x instanceof ExperienceOrbEntity)) e.setVelocity(e.getVelocity().multiply(0.5).add(player.getPos().add(0,0.5,0).subtract(e.getPos()).normalize().multiply(0.35)));
            if (tier >= 5 && player.isSprinting() && ticks % 60 == 0) smeltOne(player);
            if (tier >= 8) {
                for (ProjectileEntity p : world.getEntitiesByClass(ProjectileEntity.class, player.getBoundingBox().expand(3.5), x -> x.getOwner() != player)) if (p.getVelocity().lengthSquared() > 0.01) { p.setVelocity(p.getVelocity().multiply(-1.5)); p.setOwner(player); }
                for (CreeperEntity c : world.getEntitiesByClass(CreeperEntity.class, player.getBoundingBox().expand(4.5), x -> true)) c.getNavigation().startMovingTo(player.getX() + (c.getX()-player.getX())*2, c.getY(), c.getZ() + (c.getZ()-player.getZ())*2, 1.5);
            }
            if (tier >= 10 && grabbedEntities.containsKey(player.getUuid())) { Entity g = world.getEntityById(grabbedEntities.get(player.getUuid())); if (g == null || !g.isAlive()) grabbedEntities.remove(player.getUuid()); else { g.setVelocity(player.getEyePos().add(player.getRotationVector().multiply(3.8)).subtract(g.getPos()).multiply(0.45)); g.fallDistance = 0; } }
            if (tier >= 11 && ticks % 10 == 0) { Deque<PlayerSnapshot> d = timeHistory.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>()); if (d.size() >= 10) d.pollFirst(); d.addLast(new PlayerSnapshot(world, player.getPos(), player.getHealth(), player.getHungerManager().getFoodLevel(), player.getYaw(), player.getPitch())); }
            if (tier >= 12 && player.isSneaking() && !player.isOnGround()) for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) { BlockPos p = player.getBlockPos().down().add(dx,0,dz); if (world.getBlockState(p).isAir()) { world.setBlockState(p, Blocks.CYAN_STAINED_GLASS.getDefaultState()); tempBlocks.put(new GlobalBlockPos(world, p), ticks+60); } }
        }
    }

    public static void triggerActiveAbility(ServerPlayerEntity player) {
        int tier = getPlayerTier(player); ServerWorld world = player.getServerWorld(); long now = world.getServer().getTicks();
        if (tier >= 10 && grabbedEntities.containsKey(player.getUuid())) { Entity g = world.getEntityById(grabbedEntities.remove(player.getUuid())); if (g != null) g.setVelocity(player.getRotationVector().multiply(2.8).add(0,0.5,0)); return; }
        if (now < abilityCooldowns.getOrDefault(player.getUuid(), 0L)) { player.sendMessage(Text.literal("⏳ Перезарядка...").formatted(Formatting.RED), true); return; }
        if (tier >= 12) { HitResult h = player.raycast(45, 0, false); Vec3d t = h.getPos(); for(int i=0;i<3;i++){ LightningEntity l = EntityType.LIGHTNING_BOLT.create(world); if(l!=null){ l.refreshPositionAfterTeleport(t.x, t.y, t.z); world.spawnEntity(l); }} abilityCooldowns.put(player.getUuid(), now+200); }
        else if (tier >= 11) { if (tryRewindTime(player, false)) abilityCooldowns.put(player.getUuid(), now+400); }
        else if (tier >= 10) { Vec3d lp = player.getEyePos().add(player.getRotationVector().multiply(3.5)); List<LivingEntity> ts = world.getEntitiesByClass(LivingEntity.class, Box.of(lp, 4, 4, 4), e -> e != player); if (!ts.isEmpty()) grabbedEntities.put(player.getUuid(), ts.get(0).getId()); }
        else if (tier >= 4) { ServerPlayerEntity p = findPartner(player); if (p != null && p.squaredDistanceTo(player) <= 144 && !p.getMainHandStack().isEmpty()) { player.getInventory().offerOrDrop(p.getMainHandStack().split(1)); abilityCooldowns.put(player.getUuid(), now+300); } else { player.setVelocity(player.getRotationVector().multiply(1.6).add(0,0.35,0)); abilityCooldowns.put(player.getUuid(), now+120); } }
        else { player.setVelocity(player.getRotationVector().multiply(1.2).add(0,0.3,0)); abilityCooldowns.put(player.getUuid(), now+100); }
    }

    private static boolean tryRewindTime(ServerPlayerEntity p, boolean death) { long n = p.getServer().getTicks(); if (death && n < abilityCooldowns.getOrDefault(p.getUuid(), 0L)) return false; Deque<PlayerSnapshot> d = timeHistory.get(p.getUuid()); if (d == null || d.isEmpty()) return false; PlayerSnapshot s = d.peekFirst(); p.teleport(s.world(), s.pos().x, s.pos().y, s.pos().z, s.yaw(), s.pitch()); p.setHealth(Math.max(6.0f, s.health())); p.getHungerManager().setFoodLevel(s.food()); p.extinguish(); p.fallDistance = 0; abilityCooldowns.put(p.getUuid(), n+900); return true; }
    private static void smeltOne(ServerPlayerEntity p) { Map<Item, Item> r = Map.of(Items.RAW_IRON, Items.IRON_INGOT, Items.RAW_GOLD, Items.GOLD_INGOT, Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP); for (int i=0; i<p.getInventory().size(); i++) { ItemStack st = p.getInventory().getStack(i); if (r.containsKey(st.getItem())) { st.decrement(1); p.getInventory().offerOrDrop(new ItemStack(r.get(st.getItem()))); return; } } }
    private static boolean isValuableOre(Block b) { return b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE || b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE; }
    private static ServerPlayerEntity findPartner(ServerPlayerEntity p) { for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList()) if (!o.getUuid().equals(p.getUuid())) return o; return null; }
}
