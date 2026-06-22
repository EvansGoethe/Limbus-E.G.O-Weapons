package me.yisang.limbus;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用 ProtocolLib 攔截莊嚴哀悼（弓）射擊瞬間，玩家附近的原版弓箭聲音
 * （entity.arrow.shoot 及任何含 "bow" 的聲音），讓武器只發出自訂音效。
 */
public class SoundSuppressor {

    private final Plugin plugin;
    private final ProtocolManager pm;
    private final Map<UUID, Mark> marks = new ConcurrentHashMap<>();

    private record Mark(String world, double x, double y, double z, long expire) {}

    public SoundSuppressor(Plugin plugin) {
        this.plugin = plugin;
        this.pm = ProtocolLibrary.getProtocolManager();
    }

    /** 記錄某玩家剛射擊，於其位置附近短時間內抑制原版弓箭聲音（預設 400ms）。 */
    public void mark(Player player) { mark(player, 400L); }

    /** 同上，但可指定抑制時長（毫秒）。用於覆蓋較長的上弦階段。 */
    public void mark(Player player, long durationMs) {
        Location l = player.getLocation();
        if (l.getWorld() == null) return;
        long expire = System.currentTimeMillis() + durationMs;
        Mark prev = marks.get(player.getUniqueId());
        // 不縮短：若先前 mark 的 expire 更晚就保留之
        if (prev != null && prev.expire() > expire) return;
        marks.put(player.getUniqueId(),
                new Mark(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), expire));
    }

    private boolean active(UUID id) {
        Mark m = marks.get(id);
        return m != null && m.expire() >= System.currentTimeMillis();
    }

    public void register() {
        pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGH,
                PacketType.Play.Server.NAMED_SOUND_EFFECT,
                PacketType.Play.Server.ENTITY_SOUND) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    PacketContainer p = event.getPacket();

                    Sound sound = p.getSoundEffects().read(0);
                    if (sound == null) return;
                    String key = sound.getKey().getKey(); // 例：entity.arrow.shoot
                    if (!(key.equals("entity.arrow.shoot") || key.contains("bow"))) return;

                    if (event.getPacketType() == PacketType.Play.Server.ENTITY_SOUND) {
                        // 跟隨實體的音效：來源實體若是已標記的玩家就攔截
                        Entity e = p.getEntityModifier(event).read(0);
                        if (e instanceof Player pl && active(pl.getUniqueId())) {
                            event.setCancelled(true);
                        }
                        return;
                    }

                    // 定位音效（NAMED_SOUND_EFFECT）：座標為 block*8
                    double x = p.getIntegers().read(0) / 8.0;
                    double y = p.getIntegers().read(1) / 8.0;
                    double z = p.getIntegers().read(2) / 8.0;

                    String world = event.getPlayer().getWorld().getName();
                    long now = System.currentTimeMillis();
                    for (Mark m : marks.values()) {
                        if (m.expire() < now) continue;
                        if (!m.world().equals(world)) continue;
                        double dx = x - m.x(), dy = y - m.y(), dz = z - m.z();
                        if (dx * dx + dy * dy + dz * dz <= 16.0) { // 4 格內
                            event.setCancelled(true);
                            return;
                        }
                    }
                } catch (Throwable ignored) {
                    // 版本差異導致欄位讀取失敗時，不影響其他功能（不抑制即可）
                }
            }
        });
    }
}
