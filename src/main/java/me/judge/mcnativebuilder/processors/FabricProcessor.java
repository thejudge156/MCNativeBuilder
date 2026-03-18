package me.judge.mcnativebuilder.processors;

import org.angelauramc.judgelib.installer.JudgeLibInstall;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class FabricProcessor implements IProcessor{
    @Override
    public List<File> processClasspath(JudgeLibInstall settings) {
        for (String file : settings.classpath.split(File.pathSeparator)) {
            if (file.contains("sponge-mixin")) {
                try {
                    Files.copy(Paths.get(System.getProperty("user.dir"), "libs", "sponge-mixin-0.17.0+mixin.0.8.7-local.jar"),
                            Paths.get(file), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return List.of();
    }

    @Override
    public List<String> preBuild(JudgeLibInstall settings) {
        ArrayList<String> list = new ArrayList<>();
        list.add("--initialize-at-run-time=net.fabricmc.fabric");
        list.add("--features=me.judge.fabric.FabricFeature");
        list.add("-J-Dfabric.gameJarPath=" + settings.mainJar);
        // Scanning in native image makes fabric unhappy
        list.add("-J-Dfabric.debug.disableClassPathIsolation=true");
        list.add("-H:+ClassForNameRespectsClassLoader");
        list.add("--trace-object-instantiation=org.apache.logging.log4j.Level,org.apache.logging.slf4j.Log4jMarkerFactory");
        list.add("--initialize-at-run-time=io.netty,org.slf4j,com.mojang.logging.LogUtils,org.spongepowered.asm.service.modlauncher.LoggerAdapterLog4j2");
        list.add("--initialize-at-build-time=net.fabricmc.fabric.impl.client.indigo.IndigoMixinConfigPlugin");
        list.add("-H:-ReduceImplicitExceptionStackTraceInformation");
        list.add("-J-Dfabric.server=false");
        return list;
    }

    @Override
    public void postBuild(JudgeLibInstall settings) {

    }
}
