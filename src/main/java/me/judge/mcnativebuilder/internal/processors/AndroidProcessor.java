package me.judge.mcnativebuilder.internal.processors;

import me.judge.mcnativebuilder.*;
import me.judge.mcnativebuilder.internal.Util;
import org.angelauramc.judgelib.instance.Instance;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public class AndroidProcessor implements IProcessor {
    @Override
    public List<File> processClasspath(Instance install) {
        return List.of();
    }

    @Override
    public List<String> preBuild(Instance install, List<File> classpath, LayerState appLayer) {
        if(System.getenv("ANDROID_NDK") == null) {
            // No NDK, skip
            return List.of();
        }

        File capCacheFolder = new File(Main.buildDir, "caps");
        if(!capCacheFolder.exists() && appLayer != LayerState.APP_LAYER) {
            Main.runCompileBlocking(install, classpath, "capCache",
                    List.of("-H:CAPCacheDir=" + capCacheFolder,
                            "-H:-UseContainerSupport",
                            "-H:-UseCAPCache",
                            "-H:+NewCAPCache",
                            "-H:-CheckToolchain",
                            "-Dos.arch=aarch64",
                            "-Dsvm.platform=org.graalvm.nativeimage.Platform$ANDROID_AARCH64",
                            "--native-compiler-options=-static",
                            "--native-compiler-options=-Wl,--gc-sections",
                            "--native-compiler-path=" + System.getenv("ANDROID_NDK") + "/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang",
                            Main.mainClass
                    ), BuildType.QUERY_BUILD
            );
        }

        return List.of(
                "-H:-UseContainerSupport",
                "--target=android-aarch64",
                "-Dos.arch=aarch64",
                "-H:-CheckToolchain",
                "-H:PageSize=16384",
                "-H:+UseCAPCache",
                "-H:CAPCacheDir=" + capCacheFolder,
                "--native-compiler-path=" + System.getenv("ANDROID_NDK") + "/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
        );
    }

    @Override
    public void postBuild(Instance install, LayerState appLayer) {

    }
}
