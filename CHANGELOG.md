# Changelog

## Unreleased

**この版を切るときに必ず一緒に出すもの**（2026-09-04 に用意済み・未配布）:

- **jar 内アイコン**。`icon.png`（256px）を全セルに配置し、fabric は `fabric.mod.json` の `"icon"`、
  forge は `mods.toml` の `logoFile`、neoforge は `src/main/templates/META-INF/neoforge.mods.toml` の
  `logoFile` で参照済み。**0.2.0 までの公開 jar にはアイコンが入っていない**ので、この版で初めて
  MOD 一覧にアイコンが出る。ビルドし直せばそのまま入る（追加の作業は無い）

## v0.2.0

**0.1.0 never sent an alert.** It waited for a cancelled event, but NeoForge does not deliver cancelled events to a listener that has not asked for them, and its listener ran at `HIGHEST` priority — ahead of anything that would have cancelled. Both paths were dead. If you are running 0.1.0, it has been silent the entire time.

- **Open Parties and Claims is now supported**, alongside FTB Chunks. Either one alone is enough, both together is fine, and both are optional dependencies.
- **New detection**: the mod asks the claim mod whether the action would be blocked, instead of watching for a cancelled event. This is what made Fabric support possible — Fabric cannot observe a cancel at all.
- **Fabric 1.21.1, Forge 1.20.1 and Fabric 1.20.1** join NeoForge 1.21.1.
- **Offline digest**: attempts against a claim with nobody online are stored and posted at next login. Up to 20 per player, kept 7 days.
- **Allies no longer trigger alerts.** FTB Chunks allies were being reported as intruders.
- **Block placement is now reported.** The action existed but nothing ever produced it.
- Alert text is one full sentence per action, in 9 languages. The old shared string passed its arguments in an order the Japanese translation did not match.

## v0.1.0

Initial release. NeoForge 1.21.1, FTB Chunks only.
