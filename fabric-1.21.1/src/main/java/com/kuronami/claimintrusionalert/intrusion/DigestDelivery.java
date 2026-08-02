package com.kuronami.claimintrusionalert.intrusion;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

/**
 * 通知を実際に届ける層。オンラインの宛先へはその場で、オフラインの宛先へは
 * {@link IntrusionDigest} 経由で次回ログイン時に届ける。
 *
 * <p>vanilla の型しか触らないので、4 セルでこのファイルはバイト同一に保てる。
 * loader 固有のイベント購読（ログイン / サーバー tick）は各セルの
 * {@code IntrusionListener} 側が持ち、ここへは {@link #onLogin} と {@link #onServerTick} を渡す。
 *
 * <p>ログイン直後は参加メッセージや他 MOD の案内が流れて digest が押し流されるので、
 * 表示は {@value #DELAY_MS} ms 待ってから出す。待っている間に切断したら出さずに保留へ戻す
 * （{@link IntrusionDigest#drain} を呼ばないので記録は残る）。
 */
public final class DigestDelivery {

    /**
     * ログインから digest を出すまでの待ち。他 MOD の参加時メッセージを先に流させる。
     *
     * <p>server tick 数ではなく実時刻で持つ。tick カウンタはワールドを開き直すと 0 に戻るので、
     * tick で持つと再起動をまたいだ待ちが何時間も先を指すことがある。
     */
    private static final long DELAY_MS = 2000L;

    /** 1 回のログインで出す明細の上限。総数はヘッダが伝える。 */
    private static final int MAX_LINES = 5;

    /** key = プレイヤー UUID、value = 表示予定の時刻 (ms)。 */
    private static final Map<UUID, Long> PENDING_LOGINS = new ConcurrentHashMap<>();

    private DigestDelivery() {}

    /** 1 件の記録を配る。オンラインには即時、オフラインには digest へ積む。 */
    public static void dispatch(@Nullable MinecraftServer server, IntrusionRecord record) {
        if (server == null) return;

        if (!record.recipients().isEmpty()) {
            Component msg = IntrusionReport.format(record);
            for (UUID recipient : record.recipients()) {
                ServerPlayer member = server.getPlayerList().getPlayer(recipient);
                if (member != null) member.sendSystemMessage(msg);
            }
        }

        if (record.absentRecipients().isEmpty()) return;
        IntrusionDigest digest = IntrusionDigest.get(server);
        for (UUID recipient : record.absentRecipients()) digest.add(recipient, record);
    }

    /** ログイン時に呼ぶ。実際の表示は {@value #DELAY_MS} ms 後。 */
    public static void onLogin(ServerPlayer player) {
        PENDING_LOGINS.put(player.getUUID(), System.currentTimeMillis() + DELAY_MS);
    }

    /** サーバー tick の終わりに呼ぶ。待ちが無ければ即戻る。 */
    public static void onServerTick(MinecraftServer server) {
        if (PENDING_LOGINS.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = PENDING_LOGINS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                // 表示前に切断。記録は digest に残るので次のログインで出る。
                it.remove();
                continue;
            }
            if (now < entry.getValue()) continue;
            it.remove();
            flush(server, player);
        }
    }

    /** 保留分を出して消す。明細は新しい {@value #MAX_LINES} 件だけ、時系列順に並べる。 */
    private static void flush(MinecraftServer server, ServerPlayer player) {
        List<IntrusionRecord> records = IntrusionDigest.get(server).drain(player.getUUID());
        if (records.isEmpty()) return;

        player.sendSystemMessage(IntrusionReport.digestHeader(records.size()));
        for (int i = Math.max(0, records.size() - MAX_LINES); i < records.size(); i++) {
            player.sendSystemMessage(IntrusionReport.format(records.get(i)));
        }
    }
}
