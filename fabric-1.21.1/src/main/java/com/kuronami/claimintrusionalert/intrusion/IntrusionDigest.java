package com.kuronami.claimintrusionalert.intrusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.Nullable;

/**
 * 宛先がオフラインだった侵入記録を、次回ログインまでワールドデータに預かる保管庫。
 *
 * <p>overworld の {@code DataStorage} に 1 つだけ置く（次元ごとに分けない）。
 * 保持は「1 プレイヤーあたり最新 {@value #MAX_PER_PLAYER} 件 / {@value #RETENTION_DAYS} 日」まで。
 * 期限切れは追加時・取り出し時・ロード時の 3 箇所で掃く。ロード時に掃かないと、
 * 二度と来ないプレイヤーの分がワールドデータに残り続ける。
 *
 * <p>このクラスと {@code IntrusionListener} だけが loader / MC バージョン差を持つ。
 * 1.21.1 世代の {@link SavedData} は {@code save(CompoundTag, HolderLookup.Provider)} と
 * {@code SavedData.Factory} を要求する（1.20.1 世代は引数なしの {@code save(CompoundTag)} と
 * {@code computeIfAbsent(Function, Supplier, String)}）。NBT の形と保持ルールは両世代で同じ。
 */
public final class IntrusionDigest extends SavedData {

    /** 1 プレイヤーあたりの保持件数。超えた分は古い順に捨てる。 */
    public static final int MAX_PER_PLAYER = 20;

    /** 保持期間（日）。これより古い記録は出さずに捨てる。 */
    public static final int RETENTION_DAYS = 7;

    private static final long RETENTION_MS = RETENTION_DAYS * 24L * 60L * 60L * 1000L;

    private static final String DATA_NAME = "claimintrusionalert_digest";

    private static final String TAG_PLAYERS = "players";
    private static final String TAG_ID = "id";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_INTRUDER = "intruder";
    private static final String TAG_NAME = "name";
    private static final String TAG_ACTION = "action";
    private static final String TAG_DIMENSION = "dim";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";
    private static final String TAG_TIME = "time";

    /** 受信者 UUID → 保留中の記録（古い順）。 */
    private final Map<UUID, List<IntrusionRecord>> pending = new HashMap<>();

    private IntrusionDigest() {}

    /**
     * ワールドに 1 つの保管庫。無ければ作る。
     *
     * <p>{@code DataFixTypes} は null（vanilla の {@code DimensionDataStorage} は null を許す）。
     * 自前の NBT に vanilla の datafixer を通す理由が無い。2 引数版のコンストラクタは NeoForge の
     * 追加なので、Fabric と共通にするためにここは 3 引数版で書く。
     */
    public static IntrusionDigest get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(IntrusionDigest::new, IntrusionDigest::load, null), DATA_NAME);
    }

    private static IntrusionDigest load(CompoundTag tag, HolderLookup.Provider registries) {
        IntrusionDigest digest = new IntrusionDigest();
        digest.read(tag);
        return digest;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return write(tag);
    }

    /** オフラインの宛先 1 人分に 1 件積む。 */
    public void add(UUID recipient, IntrusionRecord record) {
        List<IntrusionRecord> list = pending.computeIfAbsent(recipient, k -> new ArrayList<>());
        list.add(strip(record));
        prune(list, System.currentTimeMillis());
        setDirty();
    }

    /**
     * 保留分を取り出して消す。出したら消すので、同じ digest は二度出ない。
     *
     * @return 古い順。期限切れは除外済み
     */
    public List<IntrusionRecord> drain(UUID recipient) {
        List<IntrusionRecord> list = pending.remove(recipient);
        if (list == null) return List.of();
        setDirty();
        prune(list, System.currentTimeMillis());
        return List.copyOf(list);
    }

    /** 期限切れを落とし、上限を超えた古い分を切る。 */
    private static void prune(List<IntrusionRecord> list, long now) {
        list.removeIf(r -> now - r.timestampMs() > RETENTION_MS);
        while (list.size() > MAX_PER_PLAYER) {
            list.remove(0);
        }
    }

    /** 保管するのは記録そのものだけ。宛先リストは配り終えた後の情報なので捨てる。 */
    private static IntrusionRecord strip(IntrusionRecord r) {
        return new IntrusionRecord(
                r.intruderUuid(), r.intruderName(), r.action(), r.dimension(), r.pos(), r.timestampMs(),
                List.of(), List.of());
    }

    private void read(CompoundTag tag) {
        long now = System.currentTimeMillis();
        int stored = 0;
        int kept = 0;
        ListTag players = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            UUID recipient = parseUuid(playerTag.getString(TAG_ID));
            if (recipient == null) continue;

            List<IntrusionRecord> list = new ArrayList<>();
            ListTag entries = playerTag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
            for (int j = 0; j < entries.size(); j++) {
                IntrusionRecord record = readRecord(entries.getCompound(j));
                if (record != null) list.add(record);
            }
            stored += entries.size();
            prune(list, now);
            kept += list.size();
            if (!list.isEmpty()) pending.put(recipient, list);
        }
        // 二度と来ないプレイヤーの分をここで落とす。落としたなら書き戻しを予約する。
        if (kept != stored) setDirty();
    }

    private CompoundTag write(CompoundTag tag) {
        long now = System.currentTimeMillis();
        ListTag players = new ListTag();
        for (Iterator<Map.Entry<UUID, List<IntrusionRecord>>> it = pending.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, List<IntrusionRecord>> entry = it.next();
            prune(entry.getValue(), now);
            if (entry.getValue().isEmpty()) {
                it.remove();
                continue;
            }
            CompoundTag playerTag = new CompoundTag();
            playerTag.putString(TAG_ID, entry.getKey().toString());
            ListTag entries = new ListTag();
            for (IntrusionRecord record : entry.getValue()) entries.add(writeRecord(record));
            playerTag.put(TAG_ENTRIES, entries);
            players.add(playerTag);
        }
        tag.put(TAG_PLAYERS, players);
        return tag;
    }

    private static CompoundTag writeRecord(IntrusionRecord r) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_INTRUDER, r.intruderUuid().toString());
        tag.putString(TAG_NAME, r.intruderName());
        tag.putString(TAG_ACTION, r.action().name());
        tag.putString(TAG_DIMENSION, r.dimension().location().toString());
        tag.putInt(TAG_X, r.pos().getX());
        tag.putInt(TAG_Y, r.pos().getY());
        tag.putInt(TAG_Z, r.pos().getZ());
        tag.putLong(TAG_TIME, r.timestampMs());
        return tag;
    }

    @Nullable
    private static IntrusionRecord readRecord(CompoundTag tag) {
        UUID intruder = parseUuid(tag.getString(TAG_INTRUDER));
        IntrusionRecord.Action action = parseAction(tag.getString(TAG_ACTION));
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        if (intruder == null || action == null || dimension == null) return null;

        return new IntrusionRecord(
                intruder,
                tag.getString(TAG_NAME),
                action,
                ResourceKey.create(Registries.DIMENSION, dimension),
                new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z)),
                tag.getLong(TAG_TIME),
                List.of(),
                List.of());
    }

    @Nullable
    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static IntrusionRecord.Action parseAction(String raw) {
        try {
            return IntrusionRecord.Action.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
