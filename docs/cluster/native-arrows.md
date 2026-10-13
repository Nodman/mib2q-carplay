---
title: Native VC arrows - KDK hidden, VC draws FctID 23
tags: [cluster, kdk, bap, switch]
status: verified-decompile
sources:
  - code: java_patch/com/luka/carplay/rgd/NativeArrows.java
  - code: java_patch/com/luka/carplay/rgd/BAPBridge.java
  - code: java_patch/de/audi/tghu/navi/app/cluster/ClusterService.java
  - firmware: lsd.jxe ClusterKDKHandlerImpl (refreshState / notifyKombi / setKDKVisibility)
  - binary: MIBSI mhi2q-aa-navigation-proxy.jar (Mhi2qAaNavProxy, TurnCardPolicy), MHI2Q_ER_AUG22_P5092
---

# Native VC arrows - KDK hidden, VC draws FctID 23

Default mode (issue #3): the Virtual Cockpit shows its **own Audi maneuver arrows** instead of
the `maneuver_render` 3D arrow. The 3D arrow is opt-in.

## 📋 Context

> [bap-fctids](../rgd/bap-fctids.md) - FctID 23 descriptor -> **native-arrows** - KDK hidden ->
> the VC draws the icon. With the switch off: [compositing](compositing.md) / [kdk-geometry](kdk-geometry.md).

This is **not** the blocked Class 46 AIO-arrow path ([vc-aio-arrow](../re/firmware/vc-aio-arrow.md)).
It uses the same BAP stream that already drives the HUD. The only difference is that the HU stops
offering the KDK (junction box) slot.

## 🔍 Why hiding the KDK is enough

> `status: verified-decompile` (stock `ClusterKDKHandlerImpl` in lsd.jxe): `setKDKVisibility(v)` ->
> `refreshState(v)` -> `notifyKombi()` -> `CombiBAPListener.setSupplementaryMapVisibility(v, true)`
> (+ `setSupplementaryMapView(available ? 1 : 0)`). KDK *available* = KOMO view enabled+visible &&
> rgActive && !standby (&& clamp 15 on FPK). *Visible* is the last value passed in.

With supplementary-map visibility false, the VC shows no KDK box and draws its native icon from
`ManeuverDescriptor`, including the roundabout exit number. MIBSI does exactly this: at session
start it forces rgActive + RGI valid, then calls `ClusterService.setKDKVisibility(false)`
(`forceClusterCardUnavailable`), and it keeps publishing FctIDs 17/39/23/18/49/55.

## ⚙️ What the switch changes

Native arrows are on unless the marker `/mnt/app/carplay_native_arrows.off` (or `/tmp/...`) exists.
It is read by `RgdModule` at every session start and latched by `BAPBridge.onStart()` per route.
Toggle: `native_arrows_on` (removes the marker) / `native_arrows_off` (creates it), as M.I.B. mods
and GEM buttons.

| | 3D arrow (`.off` marker) | native arrows (default) |
|---|---|---|
| `maneuver_render` / displayable 98 | started, primed | never started |
| cluster context | ctx 80 (or 82 with AltScreen) | stays ctx 74 (or 81) |
| `isPresentationReady()` | BAP + renderer FRAME_READY | BAP start only |
| KDK | VC opens it (Fct44) | `setKDKVisibility(false)` at start; VC open requests answered "hidden" (`ClusterService.setSupplementaryMap`) until route end |
| FctID 23 far from the turn | real next maneuver | `FOLLOW_STREET` beyond prepare (1.5 km city / 3 km highway), ManeuverState 1 |
| FctID 18/19/21/22/24/46/55 | unchanged | unchanged |

The suppression (`NativeArrows.isKdkSuppressed`) is released in `shutdown()` and in the failed-start
rollback, so stock navigation's KDK works normally after the route or the phone session ends.

## ⚠️ Open points

- On-car confirmation is still pending for this patch. Evidence so far: the MIBSI user reports on
  issue #3, using the same firmware family.
- If a VC skin re-requests the KDK in a loop, it gets "hidden" each time (the stock answer when no
  KDK is possible). Check the log for repeated `KDK kept hidden for native VC arrows`.
