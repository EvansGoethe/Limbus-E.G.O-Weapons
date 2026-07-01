package me.yisang.limbus.status;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SanityListener implements Listener {
    private final SanityManager sanity;

    public SanityListener(SanityManager sanity) {
        this.sanity = sanity;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sanity.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sanity.onQuit(event.getPlayer());
    }

    /**
     * 進食回復飽食度時，同步回復理智值（每 1 飢餓點 → +1 SAN）。
     * 只在飽食度上升時觸發，避免自然消耗 hunger 反而扣 SAN。
     */
    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        int diff = event.getFoodLevel() - p.getFoodLevel();
        if (diff <= 0) return;
        sanity.setSan(p, sanity.getSan(p) + diff);
    }
}
