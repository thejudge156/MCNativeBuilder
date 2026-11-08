package me.judge.mcnativebuilder.internal.processors;

import me.judge.mcnativebuilder.IProcessor;
import me.judge.mcnativebuilder.LayerState;
import me.judge.mcnativebuilder.Main;
import me.judge.mcnativebuilder.internal.Util;
import org.angelauramc.judgelib.installer.LoaderType;
import org.angelauramc.judgelib.instance.Instance;
import org.angelauramc.judgelib.util.json.MinecraftVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FabricProcessor implements IProcessor {
    private final List<File> toRemove = new ArrayList<>();

    @Override
    public List<File> processClasspath(Instance settings) {
        for (String file : settings.classpath().split(File.pathSeparator)) {
            if (file.contains("sponge-mixin")) {
                Util.extractResource("sponge-mixin-0.17.3.jar", file);
            }
        }

        return List.of();
    }

    @Override
    public List<String> preBuild(Instance settings, List<File> classpath, LayerState appLayer) {
        if(toRemove.isEmpty()) {
            MinecraftVersion vanillaVersion;
            try {
                vanillaVersion = LoaderType.VANILLA.getMetadata().getMinecraftVersion(Main.version, null);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }

            for (MinecraftVersion.Library vanillaLibrary : vanillaVersion.libraries) {
                toRemove.add(classpath.stream().filter(f -> f.getAbsolutePath().contains(vanillaLibrary.downloads.artifact.path)).findFirst().orElse(null));
            }
            classpath.removeAll(toRemove);

            File systemLogger = new File(Main.installDir, "SystemLogger-9.9.jar");
            Util.extractResource("SystemLogger-9.9.jar", systemLogger);
            toRemove.add(systemLogger);
        }

        if(appLayer == LayerState.LIB_LAYER) {
            return List.of(
                    "-H:+ClassForNameRespectsClassLoader",
                    "-H:ApplicationLayerInitializedClasses=net.fabricmc.loader.impl.launch.FabricLauncherBase",
                    "-H:ApplicationLayerInitializedClasses=net.fabricmc.loader.impl.FabricLoaderImpl",
                    "-H:ApplicationLayerInitializedClasses=net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider",
                    "-H:ApplicationLayerInitializedClasses=me.judge.fabric.ClassFromFabricClassLoader",
                    "--initialize-at-build-time=net.fabricmc.loader.impl.util.version.VersionPredicateParser$SingleVersionPredicate," +
                            "net.fabricmc.loader.api.metadata.version.VersionComparisonOperator$5," +
                            "net.fabricmc.loader.impl.util.version.SemanticVersionImpl," +
                            "net.fabricmc.loader.impl.game.minecraft.patch.EntrypointPatch," +
                            "net.fabricmc.loader.api.metadata.version.VersionComparisonOperator$7," +
                            "net.fabricmc.loader.api.metadata.version.VersionComparisonOperator$6," +
                            "net.fabricmc.loader.api.metadata.version.VersionComparisonOperator$5," +
                            "net.fabricmc.loader.api.metadata.version.VersionComparisonOperator$4," +
                            "net.fabricmc.loader.api.metadata.version.VersionComparisonOperator$3," +
                            "net.fabricmc.loader.api.metadata.version.VersionComparisonOperator$2," +
                            "net.fabricmc.loader.api.metadata.version.VersionComparisonOperator$1," +
                            "net.fabricmc.loader.impl.game.minecraft.patch.EntrypointPatchFML125," +
                            "net.fabricmc.loader.impl.game.minecraft.Hooks," +
                            "net.fabricmc.loader.impl.launch.knot.Knot," +
                            "org.spongepowered," +
                            "net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider," +
                            "net.fabricmc.loader.impl.game.GameProvider$BuiltinTransform," +
                            "net.fabricmc.loader.impl.launch.MappingConfiguration," +
                            "net.fabricmc.loader.impl.launch.FabricLauncherBase," +
                            "org.objectweb," +
                            "net.fabricmc.loader.impl.launch.knot.MixinLogger," +
                            "net.fabricmc.loader.impl.util.log," +
                            "net.fabricmc.loader.impl.launch.knot.MixinServiceKnot," +
                            "net.fabricmc.loader.impl.util.version.SemanticVersionImpl," +
                            "net.fabricmc.loader.impl.FabricLoaderImpl"
            );
        }

        ArrayList<String> list = new ArrayList<>();
        list.add("-H:+ClassForNameRespectsClassLoader");
        list.add("--initialize-at-build-time=org.spongepowered,org.objectweb");
        list.add("--initialize-at-run-time=net.fabricmc.fabric");
        list.add("--features=me.judge.fabric.FabricFeature");
        list.add("-J-Dfabric.gameJarPath=" + settings.mainJar());
        list.add("-J--add-exports=org.graalvm.nativeimage/org.graalvm.nativeimage.impl=ALL-UNNAMED");
        list.add("-J--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.hosted.image=ALL-UNNAMED");
        // Scanning in native image makes fabric unhappy
        list.add("-J-Dfabric.debug.disableClassPathIsolation=true");
        list.add("-J-Dfabric.loader.useCompatibilityClassLoader=true");
        list.add("-H:-ReduceImplicitExceptionStackTraceInformation");
        list.add("-J-Dfabric.server=false");
        list.add("-Duser.language=en");
        list.add("-Duser.country=US");
        list.add("-J-Dfabric.gameLibraries=" + toRemove.stream().filter(Objects::nonNull).map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)) + File.pathSeparator);

        return list;
    }

    @Override
    public void postBuild(Instance settings, LayerState appLayer) {

    }
}
