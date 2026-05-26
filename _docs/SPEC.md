# Claim Intrusion Alert — SPEC

## 目的 (1行)

FTB Chunks の claim に **生身プレイヤー (= not fake)** が妨害アクションを取った瞬間、claim **オーナー / チームメンバー** に chat 通知する。本家 FTB Chunks は侵入者にだけ通知し、オーナーには何も伝えない gap を埋める。

## エビデンス (gap 実証、2026-05-27 再確認)

reference: `dev/ftb/mods/ftbchunks` repo branch `1.21.1/main`

### 妨害イベント呼出 (ClaimedChunkManagerImpl.java 周辺 ~L219-225)

```java
if (prevented) {
    PlayerNotifier.notifyWithCooldown(player, Component.translatable("ftbchunks.action_prevented"), ...);
    if (isFake) {
        chunk.getTeamData().logPreventedAccess(player, System.currentTimeMillis());
    }
}
```

- 通知の宛先 = `player` (= **侵入者本人**)。owner には届かない
- `logPreventedAccess` は `isFake` ガード = **生身の人間は記録すらされない**

### データ層 (ChunkTeamDataImpl.java)

- `preventedAccess: Map<UUID, PreventedAccess>` フィールド (L99)
- `logPreventedAccess(ServerPlayer, long)` メソッド (L744-746): 書き込み
- save / load / prune はあるが、**owner 向け getter / surface 経路ゼロ**
- `PreventedAccess` レコードは {name, when} のみ持ち、消費点なし

### upstream の意思

FTB Issues #257「Claimed Chunk Visitor's Log」で侵入/visitor log 要望 → メンテナが **"no longer planned for FTB"** と明示却下。gap は upstream で恒久的に埋められない。

## 機能要件

### 必須
- claim 内で **PVP 以外の妨害** (block break / place / interact が `prevented`) が発火した瞬間、claim owner + team members の **オンラインプレイヤー全員に chat 通知**
- 通知内容:
  - 侵入者名
  - 妨害アクション (break / place / interact)
  - 妨害対象 (block の種類、または座標+次元)
  - 時刻 (`HH:mm`)
- **cooldown**: 同一 (侵入者 × 侵入対象 claim) ペアで N 分間まとめる (デフォ 5 分、config 化検討)
- **fake player フィルタ**: hopper / minecart / ボット系は通知抑止 (FTB Chunks 既存 `isFake` 判定を尊重、ただし対象は逆 = 生身のみ通知)

### オプション (将来)
- **オフライン digest**: owner offline 時はログ蓄積 → 次回ログイン時 / `/cia recent` で recap (Away Digest 流用)
- **対象 claim per-team config**: 通知 ON/OFF, granularity (action 別、座標表示)
- **クライアントサイド toast / sound 強調** (server-side で十分か検証中)

## アーキテクチャ

### コア依存
- FTB Chunks (required, dep): `FTBChunksAPI.api().getManager()`、`ChunkDimPos`、`ClaimedChunk#getTeamData()`、Team#owner UUID 解決
- mixin **不要**: `FTBChunksAPI` public API + vanilla イベントだけで実装可

### イベントフック戦略 (3 候補)

**A. vanilla イベント HIGHEST priority + `event.isCanceled()` 観測**
- `BlockEvent.BreakEvent` / `BlockEvent.EntityPlaceEvent` / `PlayerInteractEvent.RightClickBlock` を HIGHEST で listen
- すでに canceled なら「誰かが妨害した」= FTB Chunks 由来か他 mod 由来か曖昧
- リスク: FTB Chunks 以外の protection mod が canceled にしてた場合に誤通知

**B. FTB Chunks 内部 event を hook**
- `FTBChunksAPI` に妨害発火専用 callback が公開されていれば最高 (要確認)
- ない場合は B 不可

**C. PlayerNotifier.notifyWithCooldown を mixin で wrap**
- mixin 入るがロジック確実
- Fabric にも適用可 (PlayerNotifier はパッケージ共通)

**選定**: 着工時に FTB Chunks API を再度走査して B 可能か確認 → 不可なら **A**、A の誤通知が大きすぎれば **C** に格上げ。

### コードレイアウト (scaffold 済み)

```
src/main/java/com/kuronami/claimintrusionalert/
├── ClaimIntrusionAlert.java          # entry (NeoForge/Forge 共通)
├── ClaimIntrusionAlertFabric.java    # entry (Fabric)
├── command/
│   └── ClaimIntrusionAlertCommand.java   # /cia recent ... (TODO)
└── intrusion/
    ├── IntrusionListener.java        # vanilla event hook → 妨害観測 (TODO)
    ├── IntrusionAnalyzer.java        # claim owner 解決 + cooldown 判定 (TODO)
    ├── IntrusionRecord.java          # 内部レコード (TODO)
    └── IntrusionReport.java          # chat メッセージ整形 (TODO)
```

⚠ 現状はファイル名のみ rename 済み。**中身は death-forensics の LivingDeathEvent ロジック残**。次回着工で `BlockEvent` 系に置換。

### Cooldown 戦略 (kuronami_cf 既存パターン流用)
- `(intruderUUID, claimDimPos)` キーで in-memory `Map<Key, Long>` 保持
- 同一キーで 5 分以内なら通知抑止
- サーバー再起動で reset 許容 (SavedData 不要、軽量優先)

### Offline digest (オプション、将来)
- Away Digest と同じ `SavedData` パターン
- owner offline 時の侵入を per-owner キューに蓄積、ログイン時に digest
- 初手は不要、需要次第で追加

## 公開ターゲット

PROJECT_REGISTRY 標準マトリクス:
- NeoForge 1.21.1 (root)
- Forge 1.21.1
- Forge 1.20.1
- Fabric 1.21.1
- Fabric 1.20.1

### FTB Chunks 依存バージョン

各ターゲットで `mods.toml` / `fabric.mod.json` に required dependency 追加が必要。バージョンは:
- 1.21.1: FTB Chunks 2102.x (最新)
- 1.20.1: FTB Chunks 2001.x (最新)

着工時に Modrinth / CurseForge で実バージョン確認。

## ライセンス

MIT (kuronami_cf シリーズ標準)、modpack 自由配布。

## 実装 TODO (次セッション着手)

1. **依存追加**: build.gradle (NeoForge/Forge×2) と fabric (Fabric×2) に FTB Chunks の maven 追加 + dependency declaration
2. **mods.toml / fabric.mod.json**: FTB Chunks を required dependency に追記
3. **IntrusionListener**: vanilla event hook (A 案で start)、isCanceled 観測、FTB Chunks `getChunk(ChunkDimPos)` で claim 検出
4. **IntrusionAnalyzer**: owner UUID 解決、cooldown 判定、Map<Key, Long> 保持
5. **IntrusionReport**: chat message 整形 (Component.translatable + 翻訳キー)
6. **lang ファイル**: en_us.json / ja_jp.json + 翻訳 7 言語 (kuronami_cf 標準)
7. **entry point**: `IntrusionListener` を `MinecraftForge.EVENT_BUS.register` (Forge) / `ServerLifecycleEvents` (Fabric) で登録
8. **command** (オプション): `/cia recent [N]` で自 claim の最近の侵入リスト
9. **lib タグ整理 + STORE_BODY_EN.md 整備**
10. **5 ローダービルド緑確認**

## リスク・既知

- **A 案 (vanilla event isCanceled 観測) の誤通知**: 他 protection mod (GriefDefender, OpenPartiesAndClaims など) が canceled にすると claim 内じゃない場所でも通知が走る → FTB Chunks API `getChunk(...).getTeamData() != null` の前段ガード必須
- **fake player フィルタ**: vanilla event の `event.getPlayer()` が `FakePlayer` 派生かを `instanceof` で判定可
- **複数 claim owner**: チーム化されてる FTB Chunks では owner = team、メンバー全員に通知が妥当。`getTeamData().getMembers()` で online のみピックアップ
- **競合**: Griefercheck 系は **admin retrospective pull** 流派なのでパターンが直接競合しない (BACKLOG 記載)

## 確度

- 実装容易性: **高** (vanilla event + FTB Chunks public API のみ、mixin 不要見込み)
- 需要: **高** (FTB issue + メンテナ恒久却下 + ATM 等大型 modpack で常時同梱)
- 維持コスト: **低** (BlockEvent / FTB API は安定、Xaero Reflection ほど fragile ではない)
