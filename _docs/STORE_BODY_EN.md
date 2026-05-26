# Claim Intrusion Alert

> Vanilla FTB Chunks tells the **intruder** they can't break a block. This mod tells **you** (the claim owner) that someone tried.

When a non-team player tries to **break / place / interact** inside your FTB Chunks claim and the action is blocked, every online team member gets a one-line chat alert:

```
⚠ Alex tried to break a block at (210, 64, -88) in minecraft:overworld.
```

- ⏱ **5-minute cooldown** per (intruder × claim) — no spam
- 🖥 **Server-side only** — clients don't need to install it
- 🧩 **No mixin** — uses FTB Chunks' public `FTBChunksAPI`
- 🛡 **Silent fail** — if FTB Chunks updates and the API shifts, the mod stays out of the way (no crashes, no log spam)

## Why this exists

The vanilla FTB Chunks behaviour is one-sided: when an outsider tries to grief your claim, FTB Chunks shows them a message ("you can't do that here"). The claim owner gets nothing. You only find out something happened if you happen to check the log file later — or if a chunk goes missing.

[FTB-Mods-Issues #257](https://github.com/FTBTeam/FTB-Mods-Issues/issues/257) requested exactly this feature. The FTB maintainer marked it **"no longer planned for FTB"**. So this is a small drop-in mod that adds the missing notification.

## Use cases

- **Private survival servers**: catch friends-of-friends being rude before they get bored and leave
- **Public / whitelisted servers**: detect raid attempts the moment they happen, not after the fact
- **Modpack servers** (FTB, ATM, etc.): works inside any pack that ships FTB Chunks

## How it works (technical)

The mod listens to vanilla `BlockEvent.BreakEvent` and `PlayerInteractEvent.RightClickBlock` at `HIGHEST` priority. When one of those events is canceled, it asks `FTBChunksAPI.api().getManager().getChunk(...)` whether the position is inside a claim. If yes, and the actor is not a team member, the team's online members get a chat message.

No per-player NBT, no save data, no command, no Item / Block registered. The only state is a small in-memory cooldown map, cleared on server stop.

## Supported loaders / versions

| Minecraft | NeoForge | Forge | Fabric |
|---|:---:|:---:|:---:|
| 1.21.1 | ✅ | — | — |

Currently NeoForge 1.21.1 only, because FTB Chunks does not ship a Forge 1.21.1 build. A Fabric port is on the roadmap.

## Dependencies

- **FTB Chunks** ≥ 2101.0 (required)

## Install

1. Install NeoForge 1.21.1
2. Install FTB Chunks
3. Drop `claimintrusionalert-0.1.0.jar` into `mods/` (server side only; clients do **not** need it)

## Languages

English, Japanese.

## License

MIT — modpack inclusion welcome, no credit required.

Author: KURONAMI
