package com.kuronami.claimintrusionalert.provider;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * claim MOD 1 つに問い合わせた結果。
 *
 * @param providerId       返した provider の {@link ClaimProvider#id()}
 * @param ownerUuid        claim 所有者。FTB Chunks は claim をチーム単位で持ち所有者 UUID を
 *                         公開 API に出さないため null になる（通知には使わずログ用）
 * @param recipients       今オンラインで、その場に chat を出せる宛先（行為者本人は除外済み）
 * @param absentRecipients 同じ team / party のオフライン member。次回ログイン時の digest に積む
 * @param wouldBlock       その claim MOD がこの行為を実際に阻止するか
 */
public record ClaimHit(
        String providerId,
        @Nullable UUID ownerUuid,
        List<UUID> recipients,
        List<UUID> absentRecipients,
        boolean wouldBlock
) {}
