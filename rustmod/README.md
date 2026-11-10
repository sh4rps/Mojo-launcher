# Rust Mod (Fabric 1.21.4, client)

Max-performance mode for Minecraft Java. It does NOT replace the Java engine - it removes the settings and
spam that cost FPS:

* One-time **fast preset**: Fast graphics, minimal particles, no clouds, no entity shadows, no biome blend, smooth
  lighting off, VSync off, unlimited FPS, entity distance 75 %. Your old values are saved and **restored when you turn it off**.
* **Adaptive render distance**: FPS under target-5 -> render distance -1; FPS over target+45 -> +1 (min/max limits).
* **Entity cull**: entities farther than `entityCull` blocks are not rendered (players, Ender Dragon, Wither never).
* **Particle cap**: at most `particleCap` particles per tick.
* Key **F9** (rebind in Controls -> Rust Mod) toggles everything; small red FPS/RD overlay top-left.

Config: `config/rustmod.properties` (enabled, adaptive, targetFps, minRd, maxRd, simDist, entityCull, particleCap, overlay).

Needs Fabric Loader 0.16+, Fabric API, Java 21.
