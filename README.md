# Claim Intrusion Alert

> When someone tries to grief your FTB Chunks claim and gets blocked, **you get a chat alert** — name, action, coords. Vanilla FTB Chunks only tells the intruder; this tells **you**.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Modrinth](https://img.shields.io/badge/Modrinth-claim--intrusion--alert-00AF5C)](https://modrinth.com/mod/claim-intrusion-alert)
[![CurseForge](https://img.shields.io/badge/CurseForge-claim--intrusion--alert-F16436)](https://www.curseforge.com/minecraft/mc-mods/claim-intrusion-alert)

---

## What it does

When a non-team player tries to **break / place / interact** inside a chunk your FTB Chunks team has claimed, and FTB Chunks cancels the action, this mod posts a chat alert to every online team member:

```
⚠ Alex tried to break a block at (210, 64, -88) in minecraft:overworld.
```

- Per-(intruder, claim) **5-minute cooldown** to prevent spam.
- **Server-side only**: clients don't need it.
- **No mixin, no config** (v0.1).

## Why?

[FTB-Mods-Issues #257](https://github.com/FTBTeam/FTB-Mods-Issues/issues/257) asked for exactly this. The maintainer marked it **"no longer planned for FTB"** — so this mod fills the gap. Vanilla FTB Chunks does notify the intruder ("you can't do that here"), but does nothing for the claim owner. Now you'll know.

## Supported loaders / versions

| Minecraft | NeoForge | Forge | Fabric |
|---|:---:|:---:|:---:|
| 1.21.1 | ✅ | — | — |

⚠ Currently NeoForge 1.21.1 only. FTB Chunks does not ship a Forge 1.21.1 build, and the Fabric port is on the roadmap (`_docs/SPEC.md`).

## Dependencies

- **FTB Chunks** (required, `>= 2101.0`) — claim/team API

## Install

1. Install **NeoForge 1.21.1**
2. Install **FTB Chunks** (NeoForge 1.21.1)
3. Drop `claimintrusionalert-0.1.0.jar` into `mods/` (server only, clients don't need it)

## License

[MIT](LICENSE) — modpack inclusion welcome, no credit required.

## Credits

- Author: KURONAMI
- Uses FTB Chunks' public `FTBChunksAPI` (no mixin)
