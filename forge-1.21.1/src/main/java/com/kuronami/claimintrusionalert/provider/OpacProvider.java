package com.kuronami.claimintrusionalert.provider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.kuronami.claimintrusionalert.ClaimIntrusionAlert;
import com.kuronami.claimintrusionalert.intrusion.IntrusionRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Open Parties and Claims アダプタ。
 *
 * <p>OPAC は公開 maven を持たないので、コンパイル時依存を一切作らずリフレクションで叩く。
 * {@link Method} は初回に一括解決してキャッシュし、1 本でも解決に失敗したら provider ごと
 * 無効化する（毎回 catch で握り潰して重くしない）。
 *
 * <p>阻止されるかの判定は OPAC 自身に聞く（{@code messages = false} なので副作用なし）。
 * 保護ルールを自前で再実装しない。
 */
public final class OpacProvider implements ClaimProvider {

    public static final String MOD_ID = "openpartiesandclaims";

    /** OPAC が右クリック面を要求するが、こちらは事前問い合わせなので実面を持たない時の代用。 */
    private static final Direction FALLBACK_FACE = Direction.UP;

    private boolean present;
    private boolean presenceChecked;
    private boolean broken;
    private boolean warned;
    private Handles handles;

    @Override
    public String id() {
        return MOD_ID;
    }

    @Override
    public boolean isAvailable() {
        if (broken) return false;
        if (!presenceChecked) {
            present = HostMods.isLoaded(MOD_ID);
            presenceChecked = true;
        }
        if (!present) return false;
        if (handles == null) {
            handles = Handles.resolve();
            if (handles == null) {
                broken = true;
                return false;
            }
        }
        return true;
    }

    @Override
    @Nullable
    public ClaimHit resolve(ServerPlayer actor, ServerLevel level, BlockPos pos, IntrusionRecord.Action action) {
        Handles h = handles;
        if (h == null) return null;
        try {
            MinecraftServer server = actor.getServer();
            if (server == null) return null;

            Object api = h.apiGet.invoke(null, server);
            Object claimsManager = h.getServerClaimsManager.invoke(api);
            ResourceLocation dim = level.dimension().location();
            Object claim = h.claimAt.invoke(claimsManager, dim, pos.getX() >> 4, pos.getZ() >> 4);
            if (claim == null) return null;

            Object protection = h.getChunkProtection.invoke(api);
            if (!wouldBlock(h, protection, actor, level, pos, action)) return null;

            UUID owner = (UUID) h.getPlayerId.invoke(claim);
            if (owner == null) return null;

            List<UUID> online = new ArrayList<>();
            List<UUID> absent = new ArrayList<>();
            collectRecipients(h, api, server, owner, actor.getUUID(), online, absent);
            return new ClaimHit(MOD_ID, owner, List.copyOf(online), List.copyOf(absent), true);
        } catch (Throwable t) {
            report(t);
            return null;
        }
    }

    private static boolean wouldBlock(
            Handles h, Object protection, ServerPlayer actor, ServerLevel level, BlockPos pos,
            IntrusionRecord.Action action
    ) throws Exception {
        return switch (action) {
            case PLACE -> (boolean) h.onEntityPlaceBlock.invoke(protection, actor, level, pos);
            // 9 引数版: (entity, hand, heldItem, world, pos, direction, breaking, messages, targetExceptions)
            case BREAK -> (boolean) h.onBlockInteraction.invoke(protection,
                    actor, InteractionHand.MAIN_HAND, actor.getMainHandItem(),
                    level, pos, FALLBACK_FACE, true, false, true);
            case INTERACT -> (boolean) h.onBlockInteraction.invoke(protection,
                    actor, InteractionHand.MAIN_HAND, actor.getMainHandItem(),
                    level, pos, FALLBACK_FACE, false, false, true);
        };
    }

    /**
     * 通知先 = claim 所有者のパーティの全 member。パーティ未所属なら所有者本人。
     * オンラインなら {@code online}、そうでなければ {@code absent}（digest 行き）へ振り分ける。
     *
     * <p>{@code getPartyByOwner} ではなく {@code getPartyByMember} を使う。claim 所有者が
     * 自分のパーティのオーナーとは限らず、他人のパーティの一員のこともあるため。
     *
     * <p>列挙元は {@code getMemberInfoStream}（オフライン含む全 member）1 本にする。
     * {@code getOnlineMemberStream} と併用すると 2 つのソースの差分がどちらにも入らない
     * member を生むので、単一ソースを playerList 引きで二分して網羅と排他を保証する。
     */
    private static void collectRecipients(
            Handles h, Object api, MinecraftServer server, UUID owner, UUID actorUuid,
            List<UUID> online, List<UUID> absent
    ) throws Exception {
        Object partyManager = h.getPartyManager.invoke(api);
        Object party = h.getPartyByMember.invoke(partyManager, owner);
        if (party == null) {
            classify(server, owner, actorUuid, online, absent);
            return;
        }
        Object stream = h.getMemberInfoStream.invoke(party);
        if (!(stream instanceof Stream<?> members)) return;
        for (Object member : members.toList()) {
            Object id = h.getMemberUuid.invoke(member);
            if (id instanceof UUID uuid) classify(server, uuid, actorUuid, online, absent);
        }
    }

    private static void classify(
            MinecraftServer server, UUID id, UUID actorUuid, List<UUID> online, List<UUID> absent
    ) {
        if (id.equals(actorUuid)) return;
        if (server.getPlayerList().getPlayer(id) != null) online.add(id);
        else absent.add(id);
    }

    /**
     * 呼び出し時の例外は「今回の判定を諦める」だけで、provider を止めない。
     * 恒久停止するのは {@link Handles#resolve()} が失敗した構造的なケースだけ。
     * ログは初回だけ warn、以降は debug（既定では出ない）。
     */
    private void report(Throwable t) {
        if (!warned) {
            warned = true;
            ClaimIntrusionAlert.LOGGER.warn("OPAC API call failed: {}", t.toString());
        }
        ClaimIntrusionAlert.LOGGER.debug("OPAC failure detail", t);
    }

    /** 初回に一括解決する reflection ハンドル。1 本でも欠けたら null を返す。 */
    private record Handles(
            Method apiGet,
            Method getServerClaimsManager,
            Method getChunkProtection,
            Method getPartyManager,
            Method claimAt,
            Method getPlayerId,
            Method onBlockInteraction,
            Method onEntityPlaceBlock,
            Method getPartyByMember,
            Method getMemberInfoStream,
            Method getMemberUuid
    ) {
        @Nullable
        static Handles resolve() {
            try {
                Class<?> apiClass = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
                Class<?> claimsClass = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
                Class<?> claimClass = Class.forName("xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI");
                Class<?> protectionClass =
                        Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI");
                Class<?> partyManagerClass =
                        Class.forName("xaero.pac.common.server.parties.party.api.IPartyManagerAPI");
                Class<?> partyClass = Class.forName("xaero.pac.common.server.parties.party.api.IServerPartyAPI");
                // IPartyMemberAPI は server. 配下ではなく common.parties 配下にいる（実 jar で確認）。
                Class<?> memberClass = Class.forName("xaero.pac.common.parties.party.member.api.IPartyMemberAPI");

                return new Handles(
                        apiClass.getMethod("get", MinecraftServer.class),
                        apiClass.getMethod("getServerClaimsManager"),
                        apiClass.getMethod("getChunkProtection"),
                        apiClass.getMethod("getPartyManager"),
                        claimsClass.getMethod("get", ResourceLocation.class, int.class, int.class),
                        claimClass.getMethod("getPlayerId"),
                        protectionClass.getMethod("onBlockInteraction",
                                Entity.class, InteractionHand.class, ItemStack.class, ServerLevel.class,
                                BlockPos.class, Direction.class, boolean.class, boolean.class, boolean.class),
                        protectionClass.getMethod("onEntityPlaceBlock",
                                Entity.class, ServerLevel.class, BlockPos.class),
                        partyManagerClass.getMethod("getPartyByMember", UUID.class),
                        partyClass.getMethod("getMemberInfoStream"),
                        memberClass.getMethod("getUUID"));
            } catch (Throwable t) {
                ClaimIntrusionAlert.LOGGER.warn(
                        "Open Parties and Claims is installed but its API does not match what this mod expects "
                                + "— the OPAC path stays off: {}", t.toString());
                ClaimIntrusionAlert.LOGGER.debug("OPAC reflection resolution detail", t);
                return null;
            }
        }
    }
}
