package me.judge.mcnativebuilder.processors;

import me.judge.mcnativebuilder.Main;
import org.angelauramc.judgelib.installer.JudgeLibInstall;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public class iOSProcessor implements IProcessor {
    @Override
    public List<File> processClasspath(JudgeLibInstall install) {
        File lwjgl = new File(Main.buildDir, "lwjgl.jar");
        try (InputStream stream = Main.class.getClassLoader().getResourceAsStream("extraLibs/lwjgl.jar")) {
            if (stream != null) {
                FileOutputStream fos = new FileOutputStream(lwjgl);
                byte[] buffer = stream.readAllBytes();
                fos.write(buffer);
                fos.flush();
                fos.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return List.of(lwjgl);
    }

    @Override
    public List<String> preBuild(JudgeLibInstall install, List<File> classpath, boolean appLayer) {
        for (String file : install.classpath.split(File.pathSeparator)) {
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
        if(!capCacheFolder.exists()) {
            Main.runCompileBlocking(classpath, "capCache",
                    List.of("-H:CAPCacheDir=" + capCacheFolder,
                            "-H:-UseContainerSupport",
                            "-H:-UseCAPCache",
                            "-H:+NewCAPCache",
                            "-H:-CheckToolchain",
                            "-Dsvm.platform=org.graalvm.nativeimage.Platform$IOS_AARCH64",
                            Main.mainClass
                    )
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
    public void postBuild(JudgeLibInstall install, boolean appLayer) {

    }
}
