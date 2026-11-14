package me.judge.mcnativebuilder.internal.processors;

import me.judge.mcnativebuilder.*;
import me.judge.mcnativebuilder.internal.Util;
import org.angelauramc.judgelib.instance.Instance;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public class iOSProcessor implements IProcessor {
    @Override
    public List<File> processClasspath(Instance install) {
        File lwjgl = new File(Main.installDir, "lwjgl.jar");
        Util.extractResource("lwjgl.jar", lwjgl);
        return List.of(lwjgl);
    }

    @Override
    public List<String> preBuild(Instance install, List<File> classpath, LayerState appLayer) {
        for (String file : install.classpath().split(File.pathSeparator)) {
            String[] paths = file.split(Pattern.quote(File.separator));
            if(paths[paths.length - 1].contains("lwjgl-")) {
                try {
                    Files.delete(Path.of(file));
                } catch (IOException e) {
                    // ignore, file was probably deleted
                }
            }
        }

        File capCacheFolder = new File(Main.buildDir, "caps");
        if(!capCacheFolder.exists() || appLayer != LayerState.APP_LAYER) {
            Main.runCompileBlocking(install, classpath, "capCache",
                    List.of("-H:CAPCacheDir=" + capCacheFolder,
                            "-H:-UseContainerSupport",
                            "-H:-UseCAPCache",
                            "-H:+NewCAPCache",
                            "-H:-CheckToolchain",
                            "-Dsvm.platform=org.graalvm.nativeimage.Platform$IOS_AARCH64",
                            Main.mainClass
                    ), BuildType.QUERY_BUILD
            );
        }

        File sdkPath = new File("sdk.properties");
        ProcessBuilder pb = new ProcessBuilder("xcrun", "--sdk", "iphoneos", "--show-sdk-path");
        pb.redirectOutput(sdkPath);

        try {
            Process p = pb.start();
            while(p.isAlive()) {
                Thread.sleep(100);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        String sdkPathString;
        try {
            BufferedReader fr = new BufferedReader(new FileReader(sdkPath));
            sdkPathString = fr.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return List.of(
                "-H:-UseContainerSupport",
                "--target=ios-aarch64",
                "-H:-CheckToolchain",
                "-H:+UseCAPCache",
                "-H:CAPCacheDir=" + capCacheFolder,
                "--native-compiler-options=-target",
                "--native-compiler-options=arm64-apple-ios15.0",
                "--native-compiler-options=-isysroot",
                "--native-compiler-options=" + sdkPathString,
                "--native-compiler-options=-install_name",
                "--native-compiler-options=@rpath/" + Main.outputName + ".dylib",
                "-H:PageSize=16384"
        );
    }

    @Override
    public void postBuild(Instance install, LayerState appLayer) {

    }
}
