<!-- name-start -->
# AttributeFix [![CurseForge Project](https://img.shields.io/curseforge/dt/280510?logo=curseforge&label=CurseForge&style=flat-square&labelColor=2D2D2D&color=555555)](https://www.curseforge.com/minecraft/mc-mods/attributefix) [![Modrinth Project](https://img.shields.io/modrinth/dt/lOOpEntO?logo=modrinth&label=Modrinth&style=flat-square&labelColor=2D2D2D&color=555555)](https://modrinth.com/mod/attributefix) [![Maven Project](https://img.shields.io/maven-metadata/v?style=flat-square&logoColor=D31A38&labelColor=2D2D2D&color=555555&label=Latest&logo=gradle&metadataUrl=https%3A%2F%2Fmaven.blamejared.com%2Fnet%2Fdarkhax%2Fattributefix%2Fattributefix-common-1.21.1%2Fmaven-metadata.xml)](https://maven.blamejared.com/net/darkhax/attributefix)
<!-- name-end -->
<!-- description-start -->
Extends the maximum attribute ranges to allow for higher values. The documentation for this mod can be found [here](https://docs.darkhax.net/mods/attributefix/).
<!-- description-end -->

## Per-Player Attribute Limits (Fork Feature)

> This is a fork of [AttributeFix by Darkhax](https://github.com/Darkhax-Minecraft/AttributeFix) (LGPL 2.1). In addition to the global range expansion, it adds **per-player** attribute limits.

While the base mod raises the global ceiling shared by every entity, this fork lets you give individual players their own minimum and/or maximum for any attribute. Limits are stored per player (by UUID), persist with the world save, and are synced to clients so the HUD (armor bar, health, etc.) reflects the capped value — including on dedicated servers.

### Command

Requires permission level 2 (operator). Targets are resolved as player profiles, so you can edit **online or offline** players by name, UUID, or selector (`@a`, `@p`, ...).

```
/attributelimit max     <targets> <attribute> <value>   Set the highest value the attribute may reach
/attributelimit min     <targets> <attribute> <value>   Set the lowest value the attribute may reach
/attributelimit add max <targets> <attribute> <delta>   Shift the maximum relative to its current value
/attributelimit add min <targets> <attribute> <delta>   Shift the minimum relative to its current value
/attributelimit clear   <targets> <attribute>           Remove both bounds (back to the global limit)
/attributelimit get     <target>  <attribute>           Show the current bounds for a player
```

`<attribute>` is a registry id such as `minecraft:generic.armor` or `minecraft:generic.max_health`.

The `add` form adjusts a bound **relative** to its current value, so `<delta>` may be negative. When the
player has no custom bound for that attribute yet, the attribute's **global** limit (the server-wide value
the base mod expands it to) is used as the base — e.g. `add max ... -5` on a fresh player yields
`global - 5`. This makes adjustments compose across multiple sources, and lets you revert one by applying
the opposite delta (apply `-5`, later undo with `+5`). Unlike `max`/`min`, `add` is **not** idempotent:
running the same delta twice shifts the bound twice.

### Examples

```
/attributelimit max Steve minecraft:generic.armor 30
/attributelimit min Steve minecraft:generic.armor 10
/attributelimit get Steve minecraft:generic.armor          -> min: 10.0, max: 30.0
/attributelimit add max Steve minecraft:generic.armor -5   lower Steve's armor max by 5 (from 30 to 25)
/attributelimit add max Bob minecraft:generic.armor -5     Bob has no bound yet -> global armor max minus 5
/attributelimit clear Steve minecraft:generic.armor
```

### Notes

- `min` and `max` are independent — setting one keeps the other.
- A per-player limit can only **narrow** the range within the global one: it cannot push a value above the mod's global maximum (which vanilla clamps to first). Since the global maximum is very high by default, this only matters if you lower an attribute's global limit.
- Limits are saved in the world's data folder (`attributefix_player_limits.dat`) and are cleared when the server stops, so they do not leak between worlds.

<!-- maven-start -->
## Maven Dependency

If you are using [Gradle](https://gradle.org) to manage your dependencies, add the following into your `build.gradle` file. Make sure to replace the version with the correct one. All versions can be viewed [here](https://maven.blamejared.com/net/darkhax/attributefix).

```gradle
repositories {
    maven { 
        url 'https://maven.blamejared.com'
    }
}

dependencies {
    // NeoForge
    implementation group: 'net.darkhax.attributefix', name: 'attributefix-neoforge-1.21.1', version: '21.1.0'

    // Forge
    implementation group: 'net.darkhax.attributefix', name: 'attributefix-forge-1.21.1', version: '21.1.0'

    // Fabric & Quilt
    modImplementation group: 'net.darkhax.attributefix', name: 'attributefix-fabric-1.21.1', version: '21.1.0'

    // Common / MultiLoader / Vanilla
    compileOnly group: 'net.darkhax.attributefix', name: 'attributefix-common-1.21.1', version: '21.1.0'
}
```
<!-- maven-end -->

<!-- sponsor-start -->
## Sponsors

[![](https://assets.blamejared.com/nodecraft/darkhax.jpg)](https://nodecraft.com/r/darkhax)    
AttributeFix is sponsored by Nodecraft. Use code **[DARKHAX](https://nodecraft.com/r/darkhax)** for 30% of your first month of service!
<!-- sponsor-end -->