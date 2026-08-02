# Changelog

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
