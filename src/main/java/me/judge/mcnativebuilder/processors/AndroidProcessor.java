package me.judge.mcnativebuilder.processors;

import me.judge.mcnativebuilder.Main;
import org.angelauramc.judgelib.installer.JudgeLibInstall;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class AndroidProcessor implements IProcessor {
    @Override
    public List<File> processClasspath(JudgeLibInstall install) {
        return List.of();
    }

    @Override
    public List<String> preBuild(JudgeLibInstall install, List<File> classpath) {
        if(System.getenv("ANDROID_NDK") == null) {
            // No NDK, skip
            return List.of();
        }

        // Query build for Android CAPCache
        File capCacheFolder = new File(Main.buildDir, "capCache");
        if(!capCacheFolder.exists()) {
            capCacheFolder.mkdirs();
            try {
                Main.startCompile(classpath, Main.buildDir, "Ob", "serial",
                        "-H:CAPCacheDir=" + capCacheFolder,
                        "-H:-UseContainerSupport",
                        "-H:-UseCAPCache",
                        "-H:+NewCAPCache",
                        "-Dsvm.platform=org.graalvm.nativeimage.Platform$ANDROID_AARCH64",
                        "-H:-ForeignAPISupport",
                        "--native-compiler-options=-I" + System.getenv("ANDROID_NDK") + "/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/aarch64-linux-android",
                        "--native-compiler-options=-D__aarch64__",
                        "--native-compiler-options=-I" + System.getenv("ANDROID_NDK") + "/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include"
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return List.of(
                "-H:-UseContainerSupport",
                "-H:+UseCAPCache",
                "--target=android-aarch64",
                "-H:CAPCacheDir=" + capCacheFolder,
                "-H:-ForeignAPISupport",
                "--native-compiler-path=" + System.getenv("ANDROID_NDK") + "/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
        );
    }

    @Override
    public void postBuild(JudgeLibInstall install) {

    }
}
