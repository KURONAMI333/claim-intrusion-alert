package com.kuronami.claimintrusionalert.intrusion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.kuronami.claimintrusionalert.provider.ClaimProviders;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * ブロック破壊 / 設置 / 右クリックを受け、claim の所有者側へ chat 通知する listener（Fabric）。
 *
 * <p>Fabric はキャンセル済みイベントを後から観測できない（最初に FAIL / false を返した callback
 * で短絡し、以降のリスナーは呼ばれない）。NeoForge 基準セルと同じ「事前問い合わせ」方式で
 * provider へ直接「この行為はここで阻止されるか」を聞き、自分では何も妨害しない
 * （BREAK は常に {@code true}、右クリックは常に {@link InteractionResult#PASS} を返す）。
 *
 * <p>独自 phase（{@link #ALERT_PHASE}）を、OPAC の {@code PROTECTION_PHASE} と
 * {@link Event#DEFAULT_PHASE} の両方より前に {@code addPhaseOrdering} する。DEFAULT に
 * 素直に登録すると、OPAC が拒否した時に短絡されて呼ばれなくなる。
 *
 * <p>設置専用のイベントは無い。{@link UseBlockCallback} の中で持っているアイテムが
 * {@link BlockItem} なら PLACE、そうでなければ INTERACT として扱う。
 *
 * <p>cooldown: (侵入者 UUID, dimension, チャンク座標) で {@link #COOLDOWN_MS} ms 抑止。
 *
 * <p>ログインとサーバー tick も購読する。宛先が全員オフラインだった記録は
 * {@link IntrusionDigest} に積まれ、{@link DigestDelivery} が次回ログインでまとめて出す。
 */
public final class IntrusionListener {

    /** 同一 (侵入者 × claim チャンク) の通知抑止時間。 */
    public static final long COOLDOWN_MS = 5 * 60 * 1000L;

    /** key = "uuid@dim:chunkX,chunkZ"、value = 最終通知時刻 (ms)。 */
    private static final Map<String, Long> LAST_ALERT = new ConcurrentHashMap<>();

    // 1.20.1 世代の ResourceLocation は public コンストラクタ（1.21.1 は fromNamespaceAndPath 推奨）。
    private static final ResourceLocation ALERT_PHASE =
            new ResourceLocation("claimintrusionalert", "alert");
    private static final ResourceLocation OPAC_PROTECTION_PHASE =
            new ResourceLocation("openpartiesandclaims", "protection");

    private IntrusionListener() {}

    /** mod 初期化時に 1 回だけ呼ぶ。イベント購読を登録する。 */
    public static void register() {
        PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(ALERT_PHASE, OPAC_PROTECTION_PHASE);
        PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(ALERT_PHASE, Event.DEFAULT_PHASE);
        PlayerBlockBreakEvents.BEFORE.register(ALERT_PHASE, IntrusionListener::onBlockBreak);

        UseBlockCallback.EVENT.addPhaseOrdering(ALERT_PHASE, OPAC_PROTECTION_PHASE);
        UseBlockCallback.EVENT.addPhaseOrdering(ALERT_PHASE, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(ALERT_PHASE, IntrusionListener::onUseBlock);

        // 留守中の分をログイン時に出す。表示は DigestDelivery 側で少し遅らせる。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> DigestDelivery.onLogin(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(DigestDelivery::onServerTick);
    }

    private static boolean onBlockBreak(
            Level world, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity
    ) {
        handle(player, pos, IntrusionRecord.Action.BREAK);
        return true;
    }

    private static InteractionResult onUseBlock(
            Player player, Level world, InteractionHand hand, BlockHitResult hitResult
    ) {
        ItemStack stack = player.getItemInHand(hand);
        IntrusionRecord.Action action = stack.getItem() instanceof BlockItem
                ? IntrusionRecord.Action.PLACE
                : IntrusionRecord.Action.INTERACT;
        handle(player, hitResult.getBlockPos(), action);
        return InteractionResult.PASS;
    }

    private static void handle(Player actor, BlockPos pos, IntrusionRecord.Action action) {
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
