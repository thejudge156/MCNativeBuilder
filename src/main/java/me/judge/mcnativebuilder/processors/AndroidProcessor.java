package me.judge.mcnativebuilder.processors;

import me.judge.mcnativebuilder.Main;
import org.angelauramc.judgelib.installer.JudgeLibInstall;
import org.angelauramc.judgelib.util.ConnectionUtil;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public class AndroidProcessor implements IProcessor {
    @Override
    public List<File> processClasspath(JudgeLibInstall install) {
        File file = new File(Main.buildDir, "lwjgl-glfw-classes.jar");
        try (InputStream stream = Main.class.getClassLoader().getResourceAsStream("extraLibs/lwjgl-glfw-classes.jar")) {
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
        return List.of(file);
    }

    @Override
    public List<String> preBuild(JudgeLibInstall install, List<File> classpath, boolean appLayer) {
        if(System.getenv("ANDROID_NDK") == null || !appLayer) {
            // No NDK, skip
            return List.of();
        }

        for (String file : install.classpath.split(File.pathSeparator)) {
            String[] paths = file.split(Pattern.quote(File.separator));
            if(paths[paths.length - 1].contains("lwjgl-glfw-3.") || paths[paths.length - 1].contains("lwjgl-opengl-3.")) {
                try {
                    Files.delete(Path.of(file));
                } catch (IOException e) {
                    // ignore, file was probably deleted
                }
            }
        }

        File capCacheFolder = new File(Main.buildDir, "caps");
        if(!capCacheFolder.exists()) {
            Main.runCompileBlocking(classpath, "capCache",
                    List.of("-H:CAPCacheDir=" + capCacheFolder,
                            "-H:-UseContainerSupport",
                            "-H:-UseCAPCache",
                            "-H:+NewCAPCache",
                            "-H:-CheckToolchain",
                            "-Dsvm.platform=org.graalvm.nativeimage.Platform$ANDROID_AARCH64",
                            "-H:-ForeignAPISupport",
                            "--native-compiler-options=-static",
                            "--native-compiler-options=-Wl,--gc-sections",
                            "--native-compiler-path=" + System.getenv("ANDROID_NDK") + "/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang",
                            Main.mainClass
                    )
            );
        }

        return List.of(
                "-H:-UseContainerSupport",
                "--target=android-aarch64",
                "-H:-ForeignAPISupport",
                "-H:-CheckToolchain",
                "-H:PageSize=16384",
                "-H:+UseCAPCache",
                "-H:CAPCacheDir=" + capCacheFolder,
                "--native-compiler-path=" + System.getenv("ANDROID_NDK") + "/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
        );
    }

    @Override
    public void postBuild(JudgeLibInstall install, boolean appLayer) {

    }
}
