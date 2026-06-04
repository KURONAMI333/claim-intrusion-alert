# Claim Intrusion Alert

Vanilla FTB Chunks tells the intruder they can't break a block. This tells you, the claim owner, that someone tried.

When a non-team player tries to break, place, or interact inside your FTB Chunks claim and the action is blocked, every online team member gets a one-line chat alert:

```
⚠ Alex tried to break a block at (210, 64, -88) in minecraft:overworld.
```

Otherwise you'd only find out by digging through the log file later — or when a chunk goes missing. [FTB-Mods-Issues #257](https://github.com/FTBTeam/FTB-Mods-Issues/issues/257) asked for exactly this; the FTB maintainer marked it "no longer planned for FTB," so this is a small drop-in that adds the missing notification.

It listens to vanilla `BlockEvent.BreakEvent` and `PlayerInteractEvent.RightClickBlock` at HIGHEST priority and, when one is canceled, asks FTB Chunks' public API whether the position is a claim owned by someone other than the actor. There's a 5-minute cooldown per intruder-and-claim so it doesn't spam, no mixin (it uses `FTBChunksAPI`), and it silently no-ops if FTB Chunks' API shifts in an update. The only state is an in-memory cooldown map, cleared on server stop.

**Dependencies**

- FTB Chunks ≥ 2101.0 — required. NeoForge 1.21.1 only, because FTB Chunks has no Forge 1.21.1 build.

Server-side only — clients don't need it.

Free to use in any modpack. Source and issues: https://github.com/KURONAMI333/claim-intrusion-alert
