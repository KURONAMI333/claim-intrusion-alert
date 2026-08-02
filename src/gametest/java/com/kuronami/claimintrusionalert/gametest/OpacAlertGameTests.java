package com.kuronami.claimintrusionalert.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.kuronami.claimintrusionalert.ClaimIntrusionAlert;
import com.kuronami.claimintrusionalert.intrusion.IntrusionDigest;
import com.kuronami.claimintrusionalert.intrusion.IntrusionRecord;
import com.kuronami.claimintrusionalert.provider.ClaimProviders;
import com.kuronami.claimintrusionalert.provider.HostMods;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 「claim 内で非メンバーがブロックを壊すと所有者へ通知が届く」を、実物の
 * Open Parties and Claims を読み込んだ headless サーバー上で検証する。
 *
 * <p>この source set は出荷 jar に入らない（{@code jar} は {@code sourceSets.main} だけを詰める）。
 *
 * <p>検証は本番と同じ経路を通す。{@code NeoForge.EVENT_BUS} へ {@code BlockEvent.BreakEvent} を
 * post し、{@code IntrusionListener} → {@code IntrusionAnalyzer} → {@code OpacProvider}
 * （OPAC の {@code IChunkProtectionAPI} 実物）→ {@code DigestDelivery} を素通しで走らせる。
 * assert するのは末端の chat 通知だけで、途中に検証用の分岐を挟まない。
 *
 * <p>テストごとに別のチャンク・別の所有者を使う。{@code IntrusionListener.LAST_ALERT} は
 * static で (侵入者 UUID, 次元, チャンク) を 5 分抑止するので、チャンクを共有すると
 * 2 本目以降が cooldown で黙り、OPAC 側の失敗と見分けが付かなくなる。
 */
@GameTestHolder(ClaimIntrusionAlert.MOD_ID)
public class OpacAlertGameTests {

    private static final String TAG = "CIA-SELFTEST";
    private static final String BREAK_KEY = "claimintrusionalert.alert.break";

    private static final String API_CLASS = "xaero.pac.common.server.api.OpenPACServerAPI";
    private static final String CLAIMS_CLASS = "xaero.pac.common.server.claims.api.IServerClaimsManagerAPI";
    private static final String CLAIM_CLASS = "xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI";
    private static final String PROTECTION_CLASS =
            "xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI";

    // ------------------------------------------------------------------ tests

    /**
     * 土台の確認。OPAC が読まれていない GameTestServer では以降のテストが
     * 「claim が無いので通知も無い」で素通り PASS しうるので、ここで落とす。
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void opacIsLoadedAndAdapterIsActive(GameTestHelper helper) {
        if (!HostMods.isLoaded(OPAC_ID)) {
            helper.fail("Open Parties and Claims is not loaded in this GameTestServer "
                    + "— put its jar in run/gametest/mods/");
        }
        if (ClaimProviders.isIdle()) {
            helper.fail("ClaimProviders has no active provider (OpacProvider failed to resolve its API)");
        }
        try {
            Object api = api(helper.getLevel().getServer());
            log("opac api = " + api);
        } catch (Throwable t) {
            helper.fail("OpenPACServerAPI.get() failed: " + t);
        }
        helper.succeed();
    }

    /**
     * 本題。A の claim 内で、A のパーティに属さない B がブロックを壊そうとすると、
     * オンラインの A に {@code claimintrusionalert.alert.break} が届く。
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void breakInClaimAlertsOnlineOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = chunkCenter(helper, 8, 8);

        RecordingPlayer owner = joinRecordingPlayer(helper, "OwnerOnline");
        ServerPlayer intruder = helper.makeMockServerPlayerInLevel();
        log("online: owner=" + owner.getUUID() + " intruder=" + intruder.getUUID() + " pos=" + pos);

        claimFor(helper, server, level, pos, owner.getUUID());
        boolean wouldBlock = probeWouldBlock(helper, server, level, intruder, pos);
        log("online: opac wouldBlock(break) = " + wouldBlock);
        if (!wouldBlock) {
            helper.fail("OPAC says the break would NOT be blocked inside a claim owned by someone else. "
                    + "This is an OPAC-side judgement (possible fake-player exclusion), not a mod bug — "
                    + "the alert path was never reached.");
        }

        owner.messages.clear();
        postBreak(level, pos, intruder);

        Component hit = firstWithKey(owner.messages, BREAK_KEY);
        log("online: owner received " + owner.messages.size() + " message(s); match=" + (hit != null));
        for (Component c : owner.messages) log("online:   msg = " + c.getString());
        if (hit == null) {
            helper.fail("claim owner received no '" + BREAK_KEY + "' message (" + owner.messages.size()
                    + " message(s) total)");
        }
        helper.succeed();
    }

    /**
     * 所有者がオフラインなら digest に積まれる（次回ログインで {@code DigestDelivery} が出す）。
     * ここでは積まれたことまでを見る。
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void breakInClaimQueuesDigestForOfflineOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = chunkCenter(helper, 16, 16);

        UUID absentOwner = UUID.randomUUID();
        ServerPlayer intruder = helper.makeMockServerPlayerInLevel();
        log("offline: owner=" + absentOwner + " intruder=" + intruder.getUUID() + " pos=" + pos);

        claimFor(helper, server, level, pos, absentOwner);
        IntrusionDigest.get(server).drain(absentOwner);

        postBreak(level, pos, intruder);

        List<IntrusionRecord> queued = IntrusionDigest.get(server).drain(absentOwner);
        log("offline: digest entries = " + queued.size());
        if (queued.isEmpty()) {
            helper.fail("nothing was queued in the digest for the offline claim owner");
        }
        IntrusionRecord r = queued.get(0);
        log("offline: record = " + r.action() + " by " + r.intruderName() + " at " + r.pos());
        if (r.action() != IntrusionRecord.Action.BREAK) {
            helper.fail("digest record has action " + r.action() + ", expected BREAK");
        }
        if (!r.intruderUuid().equals(intruder.getUUID())) {
            helper.fail("digest record names the wrong intruder: " + r.intruderUuid());
        }
        helper.succeed();
    }

    /**
     * 対照。{@link #breakInClaimAlertsOnlineOwner} と手順を揃え、claim を作る一手だけ抜く。
     * これが無いと「claim を見ずに常に通知する」実装でも上の 2 本は PASS してしまう。
     *
     * <p>所有者役を実際にログインさせ、その UUID 宛の即時 chat と digest の両方が空であることを見る。
     * 無関係な傍観者を1人置いて「その人に来ていない」だけを見ると、claim の有無と関係なく
     * 通るので対照にならない。
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void breakOutsideClaimStaysSilent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = chunkCenter(helper, 24, 24);

        RecordingPlayer owner = joinRecordingPlayer(helper, "OwnerNoClaim");
        ServerPlayer intruder = helper.makeMockServerPlayerInLevel();
        log("control: owner=" + owner.getUUID() + " intruder=" + intruder.getUUID() + " pos=" + pos);

        Object existing = claimAt(helper, server, level, pos);
        log("control: claim at the control chunk = " + existing);
        if (existing != null) {
            helper.fail("the control chunk is already claimed — pick another chunk or wipe run/gametest/world");
        }

        owner.messages.clear();
        IntrusionDigest.get(server).drain(owner.getUUID());
        postBreak(level, pos, intruder);

        Component hit = firstWithKey(owner.messages, BREAK_KEY);
        List<IntrusionRecord> queued = IntrusionDigest.get(server).drain(owner.getUUID());
        log("control: messages=" + owner.messages.size() + " match=" + (hit != null)
                + " digest=" + queued.size());
        if (hit != null) {
            helper.fail("an alert was sent for a break in an UNCLAIMED chunk: " + hit.getString());
        }
        if (!queued.isEmpty()) {
            helper.fail("a break in an UNCLAIMED chunk was queued in the digest (" + queued.size() + " entries)");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ steps

    private static final String OPAC_ID = "openpartiesandclaims";

    /** テスト構造の位置を基準にチャンク単位でずらした座標。テスト同士が同じチャンクを使わないため。 */
    private static BlockPos chunkCenter(GameTestHelper helper, int chunkOffsetX, int chunkOffsetZ) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int cx = (origin.getX() >> 4) + chunkOffsetX;
        int cz = (origin.getZ() >> 4) + chunkOffsetZ;
        return new BlockPos((cx << 4) + 8, 64, (cz << 4) + 8);
    }

    /** OPAC に claim を作らせ、実際に所有者が付いたことを確認する。 */
    private static void claimFor(
            GameTestHelper helper, MinecraftServer server, ServerLevel level, BlockPos pos, UUID owner
    ) {
        ResourceLocation dim = level.dimension().location();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        try {
            Object claimsManager = Class.forName(API_CLASS).getMethod("getServerClaimsManager").invoke(api(server));
            // 引数順は claim(dimension, playerId, subConfigIndex, x, z, forceload)。
            // 座標より先に subConfigIndex が来る（実 jar の PlayerChunkClaim#toString で確認済み。
            // 座標を先に渡すと subConfigIndex にチャンク X が入り、別のチャンクが claim される）。
            Method claim = Class.forName(CLAIMS_CLASS)
                    .getMethod("claim", ResourceLocation.class, UUID.class, int.class, int.class,
                            int.class, boolean.class);
            // -1 = メインの sub config。念のため 0 も試す。
            for (int subConfig : new int[] {-1, 0}) {
                Object made = claim.invoke(claimsManager, dim, owner, subConfig, cx, cz, false);
                log("claim(sub=" + subConfig + ") -> " + made);
                if (made != null) break;
            }
        } catch (Throwable t) {
            helper.fail("failed to create an OPAC claim: " + t);
        }

        Object created = claimAt(helper, server, level, pos);
        if (created == null) {
            helper.fail("OPAC reports no claim at chunk (" + cx + ", " + cz + ") after claiming");
            return;
        }
        try {
            Object claimOwner = Class.forName(CLAIM_CLASS).getMethod("getPlayerId").invoke(created);
            log("claim at (" + cx + ", " + cz + ") owner = " + claimOwner);
            if (!owner.equals(claimOwner)) {
                helper.fail("the claim at the test chunk belongs to " + claimOwner + ", not to the test owner "
                        + owner + " — stale world data?");
            }
        } catch (Throwable t) {
            helper.fail("could not read the claim owner: " + t);
        }
    }

    private static Object claimAt(
            GameTestHelper helper, MinecraftServer server, ServerLevel level, BlockPos pos
    ) {
        try {
            Object claimsManager = Class.forName(API_CLASS).getMethod("getServerClaimsManager").invoke(api(server));
            return Class.forName(CLAIMS_CLASS)
                    .getMethod("get", ResourceLocation.class, int.class, int.class)
                    .invoke(claimsManager, level.dimension().location(), pos.getX() >> 4, pos.getZ() >> 4);
        } catch (Throwable t) {
            helper.fail("could not query OPAC for a claim: " + t);
            return null;
        }
    }

    /**
     * OPAC 自身に「この破壊は阻止されるか」を直接聞く。{@code OpacProvider} が使うのと同じ呼び出し。
     * 通知が出なかったときに、OPAC の判定で落ちたのか、こちらの配送で落ちたのかを切り分けるため。
     */
    private static boolean probeWouldBlock(
            GameTestHelper helper, MinecraftServer server, ServerLevel level, ServerPlayer actor, BlockPos pos
    ) {
        try {
            Object protection = Class.forName(API_CLASS).getMethod("getChunkProtection").invoke(api(server));
            Method onBlockInteraction = Class.forName(PROTECTION_CLASS).getMethod("onBlockInteraction",
                    Entity.class, InteractionHand.class, ItemStack.class, ServerLevel.class,
                    BlockPos.class, Direction.class, boolean.class, boolean.class, boolean.class);
            return (boolean) onBlockInteraction.invoke(protection, actor, InteractionHand.MAIN_HAND,
                    actor.getMainHandItem(), level, pos, Direction.UP, true, false, true);
        } catch (Throwable t) {
            helper.fail("IChunkProtectionAPI.onBlockInteraction failed: " + t);
            return false;
        }
    }

    /** 本番と同じイベントを本番と同じバスへ流す。 */
    private static void postBreak(ServerLevel level, BlockPos pos, ServerPlayer intruder) {
        NeoForge.EVENT_BUS.post(
                new BlockEvent.BreakEvent(level, pos, Blocks.STONE.defaultBlockState(), intruder));
    }

    private static Object api(MinecraftServer server) throws Exception {
        return Class.forName(API_CLASS).getMethod("get", MinecraftServer.class).invoke(null, server);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * chat を受け取れる本物の {@link ServerPlayer} を 1 人ログインさせる。
     *
     * <p>{@code GameTestHelper#makeMockServerPlayerInLevel} と同じ手順（{@code placeNewPlayer} まで）を
     * 踏むので PlayerList に載り、OPAC 側のプレイヤーデータも通常どおり初期化される。違いは
     * {@code sendSystemMessage} を横取りして記録する点だけ。侵入者側は素の
     * {@code makeMockServerPlayerInLevel} を使う（保護判定に掛かるのは侵入者なので、
     * そちらの型は vanilla の実装のままにしておく）。
     */
    private static RecordingPlayer joinRecordingPlayer(GameTestHelper helper, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        RecordingPlayer player = new RecordingPlayer(
                server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static Component firstWithKey(List<Component> messages, String key) {
        for (Component message : messages) {
            if (hasKey(message, key)) return message;
        }
        return null;
    }

    private static boolean hasKey(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents contents && key.equals(contents.getKey())) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (hasKey(sibling, key)) return true;
        }
        return false;
    }

    private static void log(String line) {
        ClaimIntrusionAlert.LOGGER.info("{} {}", TAG, line);
    }

    private static final class RecordingPlayer extends ServerPlayer {

        private final List<Component> messages = new ArrayList<>();

        private RecordingPlayer(
                MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info
        ) {
            super(server, level, profile, info);
        }

        @Override
        public void sendSystemMessage(Component component) {
            messages.add(component);
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return true;
        }
    }
}
