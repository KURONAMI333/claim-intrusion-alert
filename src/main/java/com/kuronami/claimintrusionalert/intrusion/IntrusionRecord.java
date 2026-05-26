package com.kuronami.claimintrusionalert.intrusion;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 妨害アクション 1 件の不変記録。
 *
 * @param intruderUuid 侵入者の UUID
 * @param intruderName 侵入者の表示名
 * @param action       break / place / interact
 * @param dimension    妨害が起きた次元
 * @param pos          妨害対象のブロック座標
 * @param timestampMs  サーバー時刻 (ms)
 * @param recipients   通知先 UUID 群 (= claim owner + team members の online オンライン)
 */
public record IntrusionRecord(
        UUID intruderUuid,
        String intruderName,
        Action action,
        ResourceKey<Level> dimension,
        BlockPos pos,
        long timestampMs,
        List<UUID> recipients
) {

    public enum Action {
        BREAK, PLACE, INTERACT;

        /** lang key suffix: claimintrusionalert.action.{break|place|interact}。 */
        public String key() {
            return name().toLowerCase();
        }
    }
}
