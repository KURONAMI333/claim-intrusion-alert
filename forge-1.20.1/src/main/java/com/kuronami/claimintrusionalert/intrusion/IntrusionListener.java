package com.kuronami.claimintrusionalert.intrusion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.kuronami.claimintrusionalert.provider.ClaimProviders;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * ブロック破壊 / 設置 / 右クリックを受け、claim の所有者側へ chat 通知する listener。
 *
 * <p>検知は「事前問い合わせ」。イベントを {@link EventPriority#HIGHEST} で受け、その場で
 * {@link ClaimProviders} へ「この行為はここで阻止されるか」を聞く。イベントがキャンセル
 * されたかは見ない（Forge のバスはキャンセル済みイベントを {@code receiveCanceled = true}
 * のリスナーにしか配送せず、そもそも HIGHEST は claim MOD より先に走るため、
 * キャンセル観測は成立しない）。
 *
 * <p>設置は {@code BlockEvent.EntityPlaceEvent} だけを購読する。
 * {@code BlockEvent.EntityMultiPlaceEvent} はその子クラスで、Forge の
 * {@code ListenerList} が親のリスナーを引き継ぐので同じ購読で届く。両方に置くと
 * multi-place で 2 回走る。
 *
 * <p>cooldown: (侵入者 UUID, dimension, チャンク座標) で {@link #COOLDOWN_MS} ms 抑止。
 *
 * <p>ログインとサーバー tick も購読する。宛先が全員オフラインだった記録は
 * {@link IntrusionDigest} に積まれ、{@link DigestDelivery} が次回ログインでまとめて出す。
 */
public class IntrusionListener {

    /** 同一 (侵入者 × claim チャンク) の通知抑止時間。 */
    public static final long COOLDOWN_MS = 5 * 60 * 1000L;

    /** key = "uuid@dim:chunkX,chunkZ"、value = 最終通知時刻 (ms)。 */
    private static final Map<String, Long> LAST_ALERT = new ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        handle(event.getPlayer(), event.getPos(), IntrusionRecord.Action.BREAK);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        handle(event.getEntity(), event.getPos(), IntrusionRecord.Action.PLACE);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        handle(event.getEntity(), event.getPos(), IntrusionRecord.Action.INTERACT);
    }

    /** 留守中の分をログイン時に出すため、待機列へ入れる（表示は {@link DigestDelivery} 側で少し遅らせる）。 */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) DigestDelivery.onLogin(player);
    }

    /** 1.20.1 の {@code TickEvent.ServerTickEvent} は START / END の両方で飛ぶ単一クラス。 */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        DigestDelivery.onServerTick(event.getServer());
    }

    private void handle(Entity actor, BlockPos pos, IntrusionRecord.Action action) {
        if (ClaimProviders.isIdle()) return;
        if (!(actor instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        IntrusionRecord record = IntrusionAnalyzer.analyze(player, level, pos, action);
        if (record == null) return;
        DigestDelivery.dispatch(player.getServer(), record);
    }

    /** cooldown の確認と記録（true なら通知して良し、false なら直近通知済み）。 */
    static boolean shouldAlert(String key) {
        long now = System.currentTimeMillis();
        Long last = LAST_ALERT.get(key);
        if (last != null && now - last < COOLDOWN_MS) return false;
        LAST_ALERT.put(key, now);
        return true;
    }
}
