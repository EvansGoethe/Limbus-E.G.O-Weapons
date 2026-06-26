package me.yisang.limbusweapons.event;

import me.yisang.limbusweapons.ModSounds;
import me.yisang.limbusweapons.item.DaCapoItem;
import me.yisang.limbusweapons.item.MimicryItem;
import me.yisang.limbusweapons.item.ModItems;
import me.yisang.limbusweapons.item.SolemnLamentItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class WeaponEvents {

    // ── 內部資料結構 ──────────────────────────────────────────────────────────

    private record ProjectileData(ItemEntity entity, UUID ownerId, boolean isBlack, int[] ticksAlive) {}
    private static final List<ProjectileData> activeProjectiles = Collections.synchronizedList(new ArrayList<>());

    private static final Map<UUID, Long> solemnCooldowns = new HashMap<>();

    private record BrushHit(UUID targetId, long timeMs) {}
    private static final Map<UUID, BrushHit> brushLastHit = new HashMap<>();

    private record DaCapoHit(int executeTick, PlayerEntity attacker, LivingEntity target,
                              float damage, boolean special) {}
    private static final List<DaCapoHit> dacapoQueue = Collections.synchronizedList(new ArrayList<>());

    private static final Set<UUID> processingDaCapo = Collections.synchronizedSet(new HashSet<>());

    private static int shieldTick = 0;

    // ── 注冊入口 ──────────────────────────────────────────────────────────────

    public static void register() {
        AttackEntityCallback.EVENT.register(WeaponEvents::onAttack);
        ServerTickEvents.END_SERVER_TICK.register(WeaponEvents::onServerTick);
    }

    // ── 攻擊事件 ─────────────────────────────────────────────────────────────

    private static ActionResult onAttack(PlayerEntity player, World world, Hand hand,
                                         Entity entity, EntityHitResult hitResult) {
        if (world.isClient) return ActionResult.PASS;
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;

        ItemStack stack = player.getMainHandStack();
        ServerWorld sw = (ServerWorld) world;

        if (stack.getItem() instanceof MimicryItem) {
            handleMimicry(player, sw, target);
            return ActionResult.PASS;
        }

        if (stack.getItem() instanceof DaCapoItem) {
            if (processingDaCapo.contains(player.getUuid())) return ActionResult.PASS;
            handleDaCapo(player, sw, target);
            return ActionResult.FAIL;
        }

        if (stack.getItem() instanceof me.yisang.limbusweapons.item.TwilightItem) {
            handleTwilight(player, sw, target);
            return ActionResult.FAIL;
        }

        if (stack.getItem() instanceof me.yisang.limbusweapons.item.TiantuiStarItem) {
            sw.playSound(null, target.getBlockPos(), ModSounds.TIANTUI_SLASH,
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
            return ActionResult.PASS;
        }

        return ActionResult.PASS;
    }

    // ── 擬態邏輯 ──────────────────────────────────────────────────────────────

    private static void handleMimicry(PlayerEntity player, ServerWorld world, LivingEntity target) {
        if (world.random.nextFloat() < 0.10f) {
            float bonus = 40.0f + world.random.nextFloat() * 50.0f;
            target.damage(world, world.getDamageSources().playerAttack(player), bonus);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                    target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
        }

        float healthBefore = target.getHealth();
        world.getServer().execute(() -> {
            float dealt = healthBefore - target.getHealth();
            if (dealt > 0) player.heal(dealt * 0.25f);
        });
    }

    // ── DaCapo 邏輯 ───────────────────────────────────────────────────────────

    private static void handleDaCapo(PlayerEntity player, ServerWorld world, LivingEntity target) {
        boolean special = world.random.nextFloat() < 0.40f;
        int hitCount    = special ? 3 : 5;
        float damage    = special ? 17.0f : 4.0f;
        int interval    = special ? 4 : 2;
        int currentTick = world.getServer().getTicks();

        for (int i = 0; i < hitCount; i++) {
            dacapoQueue.add(new DaCapoHit(currentTick + i * interval, player, target, damage, special));
        }
    }

    private static void processDaCapo(MinecraftServer server) {
        int tick = server.getTicks();
        dacapoQueue.removeIf(hit -> {
            if (hit.executeTick() > tick) return false;

            PlayerEntity p  = hit.attacker();
            LivingEntity tg = hit.target();

            if (tg == null || !tg.isAlive()) return true;
            if (!p.getMainHandStack().getItem().getClass().equals(DaCapoItem.class)) return true;

            ServerWorld sw = server.getWorld(p.getWorld().getRegistryKey());
            if (sw == null) return true;

            processingDaCapo.add(p.getUuid());
            try {
                tg.damage(sw, sw.getDamageSources().playerAttack(p), hit.damage());
                tg.hurtTime = 0;

                for (Entity nearby : tg.getWorld().getOtherEntities(p,
                        tg.getBoundingBox().expand(3.5))) {
                    if (!(nearby instanceof LivingEntity v)) continue;
                    if (nearby.equals(tg)) continue;
                    if (nearby instanceof PlayerEntity) continue;
                    if (nearby instanceof TameableEntity te && te.isTamed()) continue;
                    v.damage(sw, sw.getDamageSources().playerAttack(p), hit.damage() * 0.7f);
                    v.hurtTime = 0;
                }

                int color = hit.special() ? 0xFFFFFF : 0xB2B2B2;
                sw.spawnParticles(new DustParticleEffect(color, 1.2f),
                        tg.getX(), tg.getY() + 1, tg.getZ(), 15, 0.3, 0.3, 0.3, 0);

                String sound = hit.special() ? "block.anvil.place" : "block.note_block.harp";
                sw.playSound(null, tg.getBlockPos(),
                        net.minecraft.registry.Registries.SOUND_EVENT.get(
                                net.minecraft.util.Identifier.of(sound)),
                        SoundCategory.PLAYERS, 0.8f, 1.5f);
            } finally {
                processingDaCapo.remove(p.getUuid());
            }
            return true;
        });
    }

    // ── 環指筆刷邏輯 ─────────────────────────────────────────────────────────

    public static void handleRingBrush(PlayerEntity player, LivingEntity target) {
        long now = System.currentTimeMillis();
        UUID pid = player.getUuid();

        BrushHit last = brushLastHit.get(pid);
        boolean doubleHit = (last != null
                && now - last.timeMs() < 1500
                && last.targetId().equals(target.getUuid()));

        int hits = doubleHit ? 2 : 1;
        ServerWorld sw = (ServerWorld) player.getWorld();

        for (int i = 0; i < hits; i++) {
            applyBrushEffect(player, sw, target);
        }

        if (doubleHit) {
            brushLastHit.remove(pid);
        } else {
            player.setVelocity(player.getRotationVector().multiply(1.2).add(0, 0.2, 0));
            player.velocityModified = true;
            brushLastHit.put(pid, new BrushHit(target.getUuid(), now));
        }

        brushLastHit.entrySet().removeIf(e -> now - e.getValue().timeMs() > 1500);
    }

    private static final StatusEffectInstance[] BRUSH_EFFECTS = {
            new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0),
            new StatusEffectInstance(StatusEffects.SLOWNESS,  80, 1),
            new StatusEffectInstance(StatusEffects.POISON,    80, 0),
            new StatusEffectInstance(StatusEffects.WEAKNESS,  80, 1),
            new StatusEffectInstance(StatusEffects.WITHER,    80, 0),
    };

    private static void applyBrushEffect(PlayerEntity player, ServerWorld world, LivingEntity target) {
        target.damage(world, world.getDamageSources().playerAttack(player), 3.5f);
        StatusEffectInstance effect = BRUSH_EFFECTS[world.random.nextInt(BRUSH_EFFECTS.length)];
        target.addStatusEffect(new StatusEffectInstance(effect.getEffectType(), effect.getDuration(), effect.getAmplifier()));

        float r = world.random.nextFloat();
        float g = world.random.nextFloat() * 0.4f;
        float b = world.random.nextFloat() * 0.4f;
        int color = ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
        world.spawnParticles(new DustParticleEffect(color, 1.5f),
                target.getX(), target.getY() + 1, target.getZ(), 20, 0.3, 0.3, 0.3, 0);
    }

    // ── 莊嚴哀悼：開始蓄力（播放裝填音效） ─────────────────────────────────

    public static void onSolemnLamentDraw(PlayerEntity player, World world) {
        world.playSound(null, player.getBlockPos(), ModSounds.SOLEMN_LOAD,
                SoundCategory.PLAYERS, 0.6f, 1.0f);
    }

    // ── 莊嚴哀悼：放開後射擊 ─────────────────────────────────────────────────

    public static void handleSolemnLamentFire(PlayerEntity player, ServerWorld world, SolemnLamentItem weapon) {
        long now = System.currentTimeMillis();
        if (now - solemnCooldowns.getOrDefault(player.getUuid(), 0L) < 1200) return;

        ItemStack ammo = findButterfly(player);
        if (!player.getAbilities().creativeMode && ammo == null) return;

        solemnCooldowns.put(player.getUuid(), now);
        if (ammo != null) ammo.decrement(1);

        spawnSolemnProjectile(player, world, weapon.isBlack);
    }

    /** 弩式發射：彈藥已於上膛時消耗，此處只負責射出。 */
    public static void fireSolemnCharged(PlayerEntity player, ServerWorld world, SolemnLamentItem weapon) {
        spawnSolemnProjectile(player, world, weapon.isBlack);
    }

    private static void spawnSolemnProjectile(PlayerEntity player, ServerWorld world, boolean isBlack) {
        ItemStack visual = new ItemStack(ModItems.BUTTERFLY_QUARTZ);
        ItemEntity proj  = new ItemEntity(world,
                player.getX(), player.getEyeY(), player.getZ(), visual);
        proj.setPickupDelay(32767);

        Vec3d dir = player.getRotationVector().multiply(3.0);
        proj.setVelocity(dir);
        proj.setNeverDespawn();
        world.spawnEntity(proj);

        activeProjectiles.add(new ProjectileData(proj, player.getUuid(), isBlack, new int[]{0}));

        world.playSound(null, player.getBlockPos(), ModSounds.SOLEMN_SHOOT,
                SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    private static void tickProjectiles(MinecraftServer server) {
        activeProjectiles.removeIf(data -> {
            ItemEntity proj = data.entity();
            if (!proj.isAlive()) return true;

            data.ticksAlive()[0]++;
            if (data.ticksAlive()[0] > 100) { proj.discard(); return true; }

            ServerWorld sw = (ServerWorld) proj.getWorld();

            sw.spawnParticles(ParticleTypes.SQUID_INK,
                    proj.getX(), proj.getY(), proj.getZ(), 2, 0.02, 0.02, 0.02, 0.01);
            sw.spawnParticles(ParticleTypes.WHITE_ASH,
                    proj.getX(), proj.getY(), proj.getZ(), 4, 0.05, 0.05, 0.05, 0.01);

            if (proj.isOnGround()) {
                sw.spawnParticles(ParticleTypes.SQUID_INK,
                        proj.getX(), proj.getY(), proj.getZ(), 8, 0.1, 0.1, 0.1, 0.05);
                proj.discard();
                return true;
            }

            PlayerEntity owner = sw.getPlayerByUuid(data.ownerId());
            if (owner == null) { proj.discard(); return true; }

            for (Entity nearby : sw.getOtherEntities(proj, proj.getBoundingBox().expand(0.8))) {
                if (!(nearby instanceof LivingEntity target)) continue;
                if (nearby.equals(owner)) continue;

                sw.playSound(null, proj.getBlockPos(), ModSounds.SOLEMN_HIT,
                        SoundCategory.PLAYERS, 1.0f, 1.0f);
                sw.spawnParticles(ParticleTypes.SQUID_INK,
                        proj.getX(), proj.getY(), proj.getZ(), 15, 0.1, 0.1, 0.1, 0.05);

                if (data.isBlack()) {
                    target.damage(sw, sw.getDamageSources().playerAttack(owner), 8.0f);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 1));
                } else {
                    target.damage(sw, sw.getDamageSources().playerAttack(owner), 4.0f);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
                }

                proj.discard();
                return true;
            }

            return false;
        });
    }

    // ── 聖宣盾牌光環 ─────────────────────────────────────────────────────────

    private static void tickShieldAura(MinecraftServer server) {
        shieldTick++;
        if (shieldTick % 5 != 0) return;

        for (ServerWorld world : server.getWorlds()) {
            for (PlayerEntity player : world.getPlayers()) {
                boolean hasShield =
                        player.getMainHandStack().getItem() == ModItems.SOLEMN_SHIELD ||
                        player.getOffHandStack().getItem()  == ModItems.SOLEMN_SHIELD;
                if (!hasShield) continue;

                world.spawnParticles(ParticleTypes.WHITE_ASH,
                        player.getX(), player.getY() + 1, player.getZ(),
                        8, 0.4, 0.4, 0.4, 0.02);

                for (Entity e : world.getOtherEntities(player,
                        player.getBoundingBox().expand(5))) {
                    if (e instanceof LivingEntity target) {
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
                    }
                }
            }
        }
    }

    // ── 主 Tick ───────────────────────────────────────────────────────────────

    private static void onServerTick(MinecraftServer server) {
        tickShieldAura(server);
        processDaCapo(server);
        tickProjectiles(server);
        tickTiantuiDashes(server);
    }

    // ── 輔助 ─────────────────────────────────────────────────────────────────

    public static ItemStack findButterfly(PlayerEntity player) {
        for (ItemStack s : player.getInventory().main) {
            if (s.getItem() == ModItems.BUTTERFLY_QUARTZ) return s;
        }
        return null;
    }

    // ── 天退星刀 ──────────────────────────────────────────────────────────────

    private static final Map<UUID, Boolean> tiantuiSavage = new HashMap<>();
    private record DashData(UUID ownerId, Vec3d vel, boolean savage, Set<UUID> hit, int[] ticks) {}
    private static final List<DashData> activeDashes = Collections.synchronizedList(new ArrayList<>());

    public static boolean hasTigerMark(PlayerEntity p) { return findItem(p, ModItems.TIGER_MARK) != null; }
    public static boolean hasSavageTigerMark(PlayerEntity p) { return findItem(p, ModItems.SAVAGE_TIGER_MARK) != null; }
    public static boolean isTiantuiSavage(PlayerEntity p) { return tiantuiSavage.getOrDefault(p.getUuid(), false); }

    public static void startTiantuiCharge(PlayerEntity player, World world, boolean savage) {
        tiantuiSavage.put(player.getUuid(), savage);
        world.playSound(null, player.getBlockPos(),
                savage ? ModSounds.TIANTUI_CHARGE_SAV_1 : ModSounds.TIANTUI_CHARGE_TIGER,
                SoundCategory.PLAYERS, 0.9f, 1.0f);
    }

    public static void cancelTiantuiCharge(PlayerEntity player) {
        tiantuiSavage.remove(player.getUuid());
    }

    public static void tiantuiChargeTick(PlayerEntity player, ServerWorld sw, boolean savage, int drawTicks) {
        if (savage) {
            if (drawTicks == 20) sw.playSound(null, player.getBlockPos(), ModSounds.TIANTUI_CHARGE_SAV_2, SoundCategory.PLAYERS, 0.9f, 1.0f);
            else if (drawTicks == 35) sw.playSound(null, player.getBlockPos(), ModSounds.TIANTUI_CHARGE_SAV_3, SoundCategory.PLAYERS, 0.9f, 1.0f);
        }
        sw.spawnParticles(savage ? ParticleTypes.FLAME : ParticleTypes.CRIT,
                player.getX(), player.getY() + 1.0, player.getZ(), savage ? 6 : 3, 0.4, 0.4, 0.4, 0.01);
    }

    public static void fireTiantuiDash(PlayerEntity player, ServerWorld sw, boolean savage) {
        ItemStack ammo = findItem(player, savage ? ModItems.SAVAGE_TIGER_MARK : ModItems.TIGER_MARK);
        if (ammo == null && !player.getAbilities().creativeMode) { cancelTiantuiCharge(player); return; }
        if (ammo != null) ammo.decrement(1);
        cancelTiantuiCharge(player);

        Vec3d dir = player.getRotationVector();
        dir = new Vec3d(dir.x, 0, dir.z);
        if (dir.lengthSquared() < 1.0e-6) dir = new Vec3d(0, 0, 1);
        dir = dir.normalize().multiply(savage ? 1.85 : 1.2);

        activeDashes.add(new DashData(player.getUuid(), dir, savage,
                Collections.synchronizedSet(new HashSet<>()), new int[]{ savage ? 10 : 8 }));

        sw.playSound(null, player.getBlockPos(), ModSounds.TIANTUI_DASH, SoundCategory.PLAYERS, 1.0f, savage ? 0.85f : 1.0f);
    }

    private static void tickTiantuiDashes(MinecraftServer server) {
        activeDashes.removeIf(d -> {
            ServerWorld sw = null;
            PlayerEntity player = null;
            for (ServerWorld w : server.getWorlds()) {
                PlayerEntity p = w.getPlayerByUuid(d.ownerId());
                if (p != null) { sw = w; player = p; break; }
            }
            if (player == null) return true;

            player.setVelocity(d.vel());
            player.velocityModified = true;

            double dmg = d.savage() ? 18.0 : 8.0;
            sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
            sw.spawnParticles(d.savage() ? ParticleTypes.FLAME : ParticleTypes.CRIT,
                    player.getX(), player.getY() + 1.0, player.getZ(), d.savage() ? 8 : 4, 0.3, 0.3, 0.3, 0.02);

            for (Entity e : sw.getOtherEntities(player, player.getBoundingBox().expand(1.4))) {
                if (!(e instanceof LivingEntity target)) continue;
                if (!d.hit().add(e.getUuid())) continue;
                target.damage(sw, sw.getDamageSources().playerAttack(player), (float) dmg);
                target.setVelocity(d.vel().multiply(0.4).add(0, 0.25, 0));
                target.velocityModified = true;
                target.setOnFireForTicks(d.savage() ? 100 : 60);
                if (d.savage()) target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 1));
            }

            d.ticks()[0]--;
            return d.ticks()[0] <= 0;
        });
    }

    // ── 插翅虎 / 終末鳥 開啟 ──────────────────────────────────────────────────

    public static void openChatuhuPack(PlayerEntity player, ServerWorld sw, ItemStack pack) {
        if (countFreeSlots(player) < 4) return;
        pack.decrement(1);
        player.giveItemStack(new ItemStack(ModItems.TIANTUI_STAR));
        player.giveItemStack(new ItemStack(ModItems.SAVAGE_TIGER_MARK, 10));
        player.giveItemStack(new ItemStack(ModItems.TIGER_MARK, 20));
        sw.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
    }

    public static void openApocalypseBirdPack(PlayerEntity player, ServerWorld sw, ItemStack pack) {
        if (countFreeSlots(player) < 1) return;
        pack.decrement(1);
        player.giveItemStack(new ItemStack(ModItems.TWILIGHT));
        sw.spawnParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.5, 0.6, 0.5, 0.02);
    }

    // ── 薄暝 ──────────────────────────────────────────────────────────────────

    private static final Map<UUID, Long> twilightCd = new HashMap<>();

    public static boolean twilightSpecialReady(PlayerEntity p) {
        return System.currentTimeMillis() >= twilightCd.getOrDefault(p.getUuid(), 0L);
    }

    public static void twilightChargeStart(PlayerEntity player, ServerWorld sw) {
        twilightCd.put(player.getUuid(), System.currentTimeMillis() + 6000L + 30 * 50L);
    }

    public static void twilightChargeTick(PlayerEntity player, ServerWorld sw) {
        sw.spawnParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.5, 0.5, 0.5, 0.01);
        sw.spawnParticles(new DustParticleEffect(0x6C5B9E, 1.2f), player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.4, 0.4, 0.4, 0);
    }

    private static double lowHpMult(PlayerEntity p) {
        double max = p.getMaxHealth();
        double frac = Math.max(0.0, Math.min(1.0, p.getHealth() / max));
        return 1.0 + 1.5 * (1.0 - frac);
    }

    private static void handleTwilight(PlayerEntity player, ServerWorld sw, LivingEntity target) {
        double mult = lowHpMult(player);
        double base = player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE);
        double total = base * mult;
        target.damage(sw, sw.getDamageSources().playerAttack(player), (float) (total * 0.70));
        dealTrueDamage(target, total * 0.30);
        sw.spawnParticles(ParticleTypes.WHITE_ASH, target.getX(), target.getY() + 1.0, target.getZ(), 8, 0.3, 0.4, 0.3, 0.01);
    }

    public static void twilightSlash(PlayerEntity player, ServerWorld sw) {
        double mult = lowHpMult(player);
        double dmg = 14.0 * mult;
        double range = 6.0;
        Vec3d look = player.getRotationVector();
        look = new Vec3d(look.x, 0, look.z);
        if (look.lengthSquared() < 1.0e-6) look = new Vec3d(0, 0, 1);
        look = look.normalize();

        sw.playSound(null, player.getBlockPos(),
                net.minecraft.registry.Registries.SOUND_EVENT.get(net.minecraft.util.Identifier.of("entity.player.attack.sweep")),
                SoundCategory.PLAYERS, 1.2f, 0.6f);

        Vec3d eye = player.getEyePos();
        for (double d = 1.0; d <= range; d += 0.7) {
            for (double deg = -50; deg <= 50; deg += 12) {
                Vec3d v = rotateY(look, Math.toRadians(deg)).multiply(d);
                sw.spawnParticles(ParticleTypes.WHITE_ASH, eye.x + v.x, eye.y + v.y, eye.z + v.z, 1, 0, 0, 0, 0);
                sw.spawnParticles(new DustParticleEffect(0x6C5B9E, 1.4f), eye.x + v.x, eye.y + v.y, eye.z + v.z, 1, 0, 0, 0, 0);
            }
        }

        for (Entity e : sw.getOtherEntities(player, player.getBoundingBox().expand(range))) {
            if (!(e instanceof LivingEntity target)) continue;
            Vec3d to = target.getPos().subtract(player.getPos());
            to = new Vec3d(to.x, 0, to.z);
            if (to.lengthSquared() < 1.0e-6) continue;
            double angle = Math.acos(Math.max(-1, Math.min(1, look.dotProduct(to.normalize()))));
            if (angle > Math.toRadians(55)) continue;
            target.damage(sw, sw.getDamageSources().playerAttack(player), (float) (dmg * 0.70));
            dealTrueDamage(target, dmg * 0.30);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 1));
        }
    }

    /** 真實傷害：先扣吸收再扣生命，無視盔甲與抗性。 */
    private static void dealTrueDamage(LivingEntity target, double dmg) {
        if (dmg <= 0 || !target.isAlive()) return;
        float absorb = target.getAbsorptionAmount();
        if (absorb > 0) {
            float used = (float) Math.min(absorb, dmg);
            target.setAbsorptionAmount(absorb - used);
            dmg -= used;
        }
        if (dmg <= 0) return;
        target.setHealth((float) Math.max(0.0, target.getHealth() - dmg));
    }

    // ── 共用工具 ──────────────────────────────────────────────────────────────

    private static ItemStack findItem(PlayerEntity player, net.minecraft.item.Item item) {
        for (ItemStack s : player.getInventory().main) {
            if (s.getItem() == item) return s;
        }
        return null;
    }

    private static int countFreeSlots(PlayerEntity player) {
        int free = 0;
        for (ItemStack s : player.getInventory().main) {
            if (s.isEmpty()) free++;
        }
        return free;
    }

    private static Vec3d rotateY(Vec3d v, double rad) {
        double cos = Math.cos(rad), sin = Math.sin(rad);
        return new Vec3d(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
    }
}
