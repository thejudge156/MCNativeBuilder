package me.judge.mcnativebuilder.processors;

import me.judge.mcnativebuilder.Main;
import org.angelauramc.judgelib.installer.JudgeLibInstall;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class FabricProcessor implements IProcessor{
    @Override
    public List<File> processClasspath(JudgeLibInstall settings) {
        for (String file : settings.classpath.split(File.pathSeparator)) {
            if (file.contains("sponge-mixin")) {
                try (InputStream stream = Main.class.getClassLoader().getResourceAsStream("extraLibs/sponge-mixin-0.17.3.jar")) {
                    if (stream != null) {
                        FileOutputStream fos = new FileOutputStream(file);
                        byte[] buffer = stream.readAllBytes();
                        fos.write(buffer);
                        fos.flush();
                        fos.close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return List.of();
    }

    @Override
    public List<String> preBuild(JudgeLibInstall settings, List<File> classpath) {
        ArrayList<String> list = new ArrayList<>();
        list.add("--initialize-at-run-time=net.fabricmc.fabric");
        list.add("--features=me.judge.fabric.FabricFeature");
        list.add("-J-Dfabric.gameJarPath=" + settings.mainJar);
        // Scanning in native image makes fabric unhappy
        list.add("-J-Dfabric.debug.disableClassPathIsolation=true");
        list.add("-H:+ClassForNameRespectsClassLoader");
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
