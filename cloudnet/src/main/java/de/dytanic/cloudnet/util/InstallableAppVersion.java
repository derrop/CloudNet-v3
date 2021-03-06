package de.dytanic.cloudnet.util;

import de.dytanic.cloudnet.common.Validate;
import de.dytanic.cloudnet.common.collection.Iterables;
import de.dytanic.cloudnet.driver.service.ServiceEnvironment;
import de.dytanic.cloudnet.driver.service.ServiceEnvironmentType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.function.Predicate;

@Getter
@RequiredArgsConstructor
public final class InstallableAppVersion {

    public static final Collection<InstallableAppVersion> VERSIONS = Iterables.newArrayList();

    public static final InstallableAppVersion
        //BungeeCord
        BUNGEECORD_DEFAULT = new InstallableAppVersion(ServiceEnvironmentType.BUNGEECORD, ServiceEnvironment.BUNGEECORD_DEFAULT, "default", //BungeeCord
        "https://ci.md-5.net/job/BungeeCord/lastSuccessfulBuild/artifact/bootstrap/target/BungeeCord.jar"),
        BUNGEECORD_1_7_10 = new InstallableAppVersion(ServiceEnvironmentType.BUNGEECORD, ServiceEnvironment.BUNGEECORD_DEFAULT, "1.7.10",
            "https://ci.md-5.net/job/BungeeCord/1119/artifact/bootstrap/target/BungeeCord.jar"),
        BUNGEECORD_1_6_4 = new InstallableAppVersion(ServiceEnvironmentType.BUNGEECORD, ServiceEnvironment.BUNGEECORD_DEFAULT, "1.6.4",
            "https://ci.md-5.net/job/BungeeCord/701/artifact/bootstrap/target/BungeeCord.jar"),
        BUNGEECORD_1_5_2 = new InstallableAppVersion(ServiceEnvironmentType.BUNGEECORD, ServiceEnvironment.BUNGEECORD_DEFAULT, "1.5.2",
            "https://ci.md-5.net/job/BungeeCord/548/artifact/proxy/target/BungeeCord.jar"),
        BUNGEECORD_WATERFALL = new InstallableAppVersion(ServiceEnvironmentType.BUNGEECORD, ServiceEnvironment.BUNGEECORD_WATERFALL, "waterfall",
            "https://ci.destroystokyo.com/job/Waterfall/lastSuccessfulBuild/artifact/Waterfall-Proxy/bootstrap/target/Waterfall.jar"),
        BUNGEECORD_TRAVERTINE = new InstallableAppVersion(ServiceEnvironmentType.BUNGEECORD, ServiceEnvironment.BUNGEECORD_TRAVERTINE, "travertine",
            "https://papermc.io/ci/view/WaterfallMC/job/Travertine/lastSuccessfulBuild/artifact/Travertine-Proxy/bootstrap/target/Travertine.jar"),
        BUNGEECORD_HEXACORD = new InstallableAppVersion(ServiceEnvironmentType.BUNGEECORD, ServiceEnvironment.BUNGEECORD_HEXACORD, "hexacord",
            "https://yivesmirror.com/files/hexacord/HexaCord-v219.jar"),
        GLOWSTONE_DEFAULT = new InstallableAppVersion(ServiceEnvironmentType.GLOWSTONE, ServiceEnvironment.GLOWSTONE_DEFAULT, "default", //GlowStone
            "https://github.com/GlowstoneMC/Glowstone/releases/download/2018.9.0/glowstone.jar"),
        GLOWSTONE_1_8_9 = new InstallableAppVersion(ServiceEnvironmentType.GLOWSTONE, ServiceEnvironment.GLOWSTONE_DEFAULT, "1.8.9",
            "https://github.com/GlowstoneMC/Glowstone/releases/download/v1.8.9/glowstone.-1.8.9-SNAPSHOT.jar"),
        CRAFTBUKKIT_DEFAULT = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_CRAFTBUKKIT, "default", //CraftBukkit
            "https://cdn.getbukkit.org/craftbukkit/craftbukkit-1.14.1-R0.1-SNAPSHOT.jar"),
        CRAFTBUKKIT_1_14_1 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_CRAFTBUKKIT, "craftbukkit-1.14",
            "https://cdn.getbukkit.org/craftbukkit/craftbukkit-1.14.1-R0.1-SNAPSHOT.jar"),
        CRAFTBUKKIT_1_14 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_CRAFTBUKKIT, "craftbukkit-1.14",
            "https://cdn.getbukkit.org/craftbukkit/craftbukkit-1.14-R0.1-SNAPSHOT.jar"),
        CRAFTBUKKIT_1_13_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_CRAFTBUKKIT, "craftbukkit-1.13.2",
            "https://cdn.getbukkit.org/craftbukkit/craftbukkit-1.13.2.jar"),
        CRAFTBUKKIT_1_12_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_CRAFTBUKKIT, "craftbukkit-1.12.2",
            "https://cdn.getbukkit.org/craftbukkit/craftbukkit-1.12.2.jar"),
        CRAFTBUKKIT_1_11_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_CRAFTBUKKIT, "craftbukkit-1.11.2",
            "https://cdn.getbukkit.org/craftbukkit/craftbukkit-1.11.2.jar"),
        CRAFTBUKKIT_1_10_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_CRAFTBUKKIT, "craftbukkit-1.10.2",
            "https://cdn.getbukkit.org/craftbukkit/craftbukkit-1.10.2-R0.1-SNAPSHOT-latest.jar"),
        CRAFTBUKKIT_1_9_4 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_CRAFTBUKKIT, "craftbukkit-1.9.4",
            "https://cdn.getbukkit.org/craftbukkit/craftbukkit-1.9.4-R0.1-SNAPSHOT-latest.jar"),
        CRAFTBUKKIT_1_8_8 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_CRAFTBUKKIT, "craftbukkit-1.8.8",
            "https://cdn.getbukkit.org/craftbukkit/craftbukkit-1.8.8-R0.1-SNAPSHOT-latest.jar"),
        CRAFTBUKKIT_1_7_10 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_CRAFTBUKKIT, "craftbukkit-1.7.10",
            "https://cdn.getbukkit.org/craftbukkit/craftbukkit-1.7.10-R0.1-20140808.005431-8.jar"),
        CRAFTBUKKIT_1_6_4 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_CRAFTBUKKIT, "craftbukkit-1.6.4",
            "https://cdn.getbukkit.org/craftbukkit/craftbukkit-1.6.4-R2.0.jar"),
        CRAFTBUKKIT_1_5_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_CRAFTBUKKIT, "craftbukkit-1.5.2",
            "https://cdn.getbukkit.org/craftbukkit/craftbukkit-1.5.2-R1.0.jar"),
        SPONGE_VANILLA_1_12_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPONGE_VANILLA, "spongevanilla-1.12.2",
            "https://repo.spongepowered.org/maven/org/spongepowered/spongevanilla/1.12.2-7.1.2/spongevanilla-1.12.2-7.1.2.jar"),
        SPONGE_VANILLA_1_11_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPONGE_VANILLA, "spongevanilla-1.11.2",
            "https://repo.spongepowered.org/maven/org/spongepowered/spongevanilla/1.11.2-6.1.0-BETA-27/spongevanilla-1.11.2-6.1.0-BETA-27.jar"),
        SPONGE_VANILLA_1_10_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPONGE_VANILLA, "spongevanilla-1.10.2",
            "https://repo.spongepowered.org/maven/org/spongepowered/spongevanilla/1.10.2-5.2.0-BETA-403/spongevanilla-1.10.2-5.2.0-BETA-403.jar"),
        SPONGE_VANILLA_1_8_9 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPONGE_VANILLA, "spongevanilla-1.8.9",
            "https://repo.spongepowered.org/maven/org/spongepowered/spongevanilla/1.8.9-4.2.0-BETA-352/spongevanilla-1.8.9-4.2.0-BETA-352.jar"),
        SPONGE_FORGE_1_12_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPONGE_FORGE, "spongeforge-1.12.2",
            "https://repo.spongepowered.org/maven/org/spongepowered/spongeforge/1.12.2-2768-7.1.2/spongeforge-1.12.2-2768-7.1.2.jar"),
        SPONGE_FORGE_1_11_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPONGE_FORGE, "spongeforge-1.11.2",
            "https://repo.spongepowered.org/maven/org/spongepowered/spongeforge/1.11.2-2476-6.1.0-BETA-2792/spongeforge-1.11.2-2476-6.1.0-BETA-2792.jar"),
        SPONGE_FORGE_1_10_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPONGE_FORGE, "spongeforge-1.10.2",
            "https://repo.spongepowered.org/maven/org/spongepowered/spongeforge/1.10.2-2477-5.2.0-BETA-2793/spongeforge-1.10.2-2477-5.2.0-BETA-2793.jar"),
        PAPER_SPIGOT_LATEST = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_PAPER_SPIGOT, "paperspigot-latest",
            "https://yivesmirror.com/files/paper/Paper-latest.jar"),
        PAPER_SPIGOT_1_13_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_PAPER_SPIGOT, "paperspigot-1.13.2",
            "https://yivesmirror.com/files/paper/Paper-1.13.2-b597.jar"),
        PAPER_SPIGOT_1_12_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_PAPER_SPIGOT, "paperspigot-1.12.2",
            "https://yivesmirror.com/files/paper/Paper-1.12.2-b1611.jar"),
        PAPER_SPIGOT_1_8_8 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_PAPER_SPIGOT, "paperspigot-1.8.8",
            "https://yivesmirror.com/files/paper/PaperSpigot-1.8.8-R0.1-SNAPSHOT-latest.jar"),
        SPIGOT_1_14_1 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "spigot-1.14.1",
            "https://cdn.getbukkit.org/spigot/spigot-1.14.1.jar"),
        SPIGOT_1_14 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "spigot-1.14",
            "https://cdn.getbukkit.org/spigot/spigot-1.14.jar"),
        SPIGOT_1_13_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "spigot-1.13.2",
            "https://cdn.getbukkit.org/spigot/spigot-1.13.2.jar"),
        SPIGOT_1_12_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "spigot-1.12.2",
            "https://cdn.getbukkit.org/spigot/spigot-1.12.2.jar"),
        SPIGOT_1_11_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "spigot-1.11.2",
            "https://cdn.getbukkit.org/spigot/spigot-1.11.2.jar"),
        SPIGOT_1_10_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "spigot-1.10.2",
            "https://cdn.getbukkit.org/spigot/spigot-1.10.2-R0.1-SNAPSHOT-latest.jar"),
        SPIGOT_1_9_4 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "spigot-1.9.4",
            "https://cdn.getbukkit.org/spigot/spigot-1.9.4-R0.1-SNAPSHOT-latest.jar"),
        SPIGOT_1_8_8 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "spigot-1.8.8",
            "https://cdn.getbukkit.org/spigot/spigot-1.8.8-R0.1-SNAPSHOT-latest.jar"),
        SPIGOT_1_7_10 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "spigot-1.7.10",
            "https://cdn.getbukkit.org/spigot/spigot-1.7.10-SNAPSHOT-b1657.jar"),
        SPIGOT_1_6_4 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "spigot-1.6.4",
            "https://cdn.getbukkit.org/spigot/spigot-1.6.4-R2.1-SNAPSHOT.jar"),
        SPIGOT_1_5_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "spigot-1.5.2",
            "https://cdn.getbukkit.org/spigot/spigot-1.5.2-R1.1-SNAPSHOT.jar"),
        VELOCITY_DEFAULT = new InstallableAppVersion(ServiceEnvironmentType.VELOCITY, ServiceEnvironment.VELOCITY_DEFAULT, "default",
            "https://ci.velocitypowered.com/job/velocity/lastSuccessfulBuild/artifact/proxy/build/libs/velocity-proxy-1.0-SNAPSHOT-all.jar"),
        PROX_PROX_DEFAULT = new InstallableAppVersion(ServiceEnvironmentType.PROX_PROX, ServiceEnvironment.PROX_PROX_DEFAULT, "default",
            "http://ci.gomint.io/job/ProxProx/job/master/lastSuccessfulBuild/artifact/proxprox-server/target/ProxProx.jar"),
        GO_MINT_DEFAULT = new InstallableAppVersion(ServiceEnvironmentType.GO_MINT, ServiceEnvironment.GO_MINT_DEFAULT, "default",
            "http://ci.gomint.io/job/GoMint/job/master/lastSuccessfulBuild/artifact/gomint-server/target/GoMint.jar"),
    /*
            PAPER_SPIGOT_DEFAULT = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "paperspigot-default",
                    "https://yivesmirror.com/files/paper/Paper-latest.jar"),
            PAPER_SPIGOT_1_13_1 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "paperspigot-1.13.1",
                    "https://yivesmirror.com/files/paper/Paper-1.13.1-b386.jar"),
            PAPER_SPIGOT_1_12_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "paperspigot-1.12.2",
                    "https://yivesmirror.com/files/paper/PaperSpigot-1.12.2-b1562.jar"),
            PAPER_SPIGOT_1_12_1 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "paperspigot-1.12.1",
                    "https://yivesmirror.com/files/paper/PaperSpigot-1.12.1-b1204.jar"),
            PAPER_SPIGOT_1_11_2 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "paperspigot-1.11.2",
                    "https://yivesmirror.com/files/paper/PaperSpigot-1.11.2-b1104.jar"),
            PAPER_SPIGOT_1_8_8 = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "paperspigot-1.8.8",
                    "https://yivesmirror.com/files/paper/PaperSpigot-1.8.8-R0.1-SNAPSHOT-latest.jar"),
            CAULDRON_DEFAULT = new InstallableAppVersion(ServiceEnvironmentType.MINECRAFT_SERVER, ServiceEnvironment.MINECRAFT_SERVER_SPIGOT, "cauldron-default",
                    "https://yivesmirror.com/files/cauldron/cauldron-1.7.10-2.1403.1.49.zip"),
    */
    NUKKIT_DEFAULT = new InstallableAppVersion(ServiceEnvironmentType.NUKKIT, ServiceEnvironment.NUKKIT_DEFAULT, "default",
        "https://ci.nukkitx.com/job/NukkitX/job/Nukkit/job/master/lastSuccessfulBuild/artifact/target/nukkit-1.0-SNAPSHOT.jar");

    static
    {
        for (Field field : InstallableAppVersion.class.getFields())
            if (field.getType().equals(InstallableAppVersion.class) &&
                Modifier.isFinal(field.getModifiers()) &&
                Modifier.isStatic(field.getModifiers()) &&
                Modifier.isPublic(field.getModifiers()))
                try
                {
                    field.setAccessible(true);
                    VERSIONS.add((InstallableAppVersion) field.get(null));
                } catch (IllegalAccessException e)
                {
                    e.printStackTrace();
                }
    }

    public static InstallableAppVersion getVersion(ServiceEnvironmentType serviceEnvironment, String version)
    {
        Validate.checkNotNull(version);

        return Iterables.first(VERSIONS, new Predicate<InstallableAppVersion>() {
            @Override
            public boolean test(InstallableAppVersion installableAppVersion)
            {
                return installableAppVersion.getServiceEnvironment() == serviceEnvironment && installableAppVersion.getVersion().equalsIgnoreCase(version);
            }
        });
    }
    /*= -------------------------------------------------- =*/

    private final ServiceEnvironmentType serviceEnvironment;

    private final ServiceEnvironment environmentType;

    private final String version, url;

    /*= -------------------------------------------------- =*/
}