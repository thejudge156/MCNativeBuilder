package me.judge.mcnativebuilder;

import net.hycrafthd.minecraft_downloader.*;
import net.hycrafthd.minecraft_downloader.library.DownloadableFile;
import net.hycrafthd.minecraft_downloader.settings.LauncherVariables;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;
import net.hycrafthd.minecraft_downloader.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Main {
    public static final String LWJGL_DOWNLOAD = "https://build.lwjgl.org/release/3.3.3/bin/";
    public static final String SLF4J_JDK14 = "https://repo1.maven.org/maven2/org/slf4j/slf4j-jdk14/2.0.9/slf4j-jdk14-2.0.9.jar";

    public static void main(String[] args) throws IOException {
        System.out.println("Make sure GRAALVM_HOME is set.");

        ProvidedSettings settings = new ProvidedSettings(args[0], new File("./install"), new File("./install"));
        MinecraftParser.launch(settings);
        MinecraftDownloader.launch(settings, false, null, false,  false);

        StringBuilder libsBuilder = new StringBuilder();
        for(DownloadableFile file : settings.getGeneratedSettings().getDownloadableFiles()) {
            String lib = file.getDownloadedFile().getCanonicalPath();
            if(file.isNative()) {
                continue;
            }
            if(file.getDownloadedFile().getName().contains("lwjgl")) {
                String newLWJGL = createLWJGL(file);
                System.out.printf("Replacing %s with 3.3.3 Version.\n", file.getDownloadedFile().getName());
                FileUtil.downloadFile(LWJGL_DOWNLOAD + newLWJGL, file.getDownloadedFile(), null);
            }

            if(file.getPath().contains("log4j")) {
                if(!file.getPath().contains("log4j-core")) {
                    continue;
                }
                FileUtil.downloadFile(SLF4J_JDK14, file.getDownloadedFile(), null);
            }

            libsBuilder.append(lib).append(";");
        }
        libsBuilder.append(System.getProperty("user.dir")).append("/libs/JFRSub-1.0-SNAPSHOT.jar;");
        libsBuilder.append(settings.getClientJarFile().getAbsolutePath());

        MinecraftClasspathBuilder.launch(settings, false);
        MinecraftAuthenticator.launch(settings, new File("./auth.json"), "web", false);

        MinecraftJavaRuntimeSetup.launch(settings, false, new File(System.getenv("GRAALVM_HOME") + "/bin/javaw.exe"));
        System.out.println("Waiting for Minecraft to close...");
        System.out.println("Run every configuration at least once.");
        MinecraftLauncher.launch(settings, "-agentlib:native-image-agent=config-merge-dir=../configs/" + args[0]);

        File buildDir = new File("./native-build");
        buildDir.mkdirs();


        ProcessBuilder builder = new ProcessBuilder();
        builder.command(System.getenv("GRAALVM_HOME") + "/bin/native-image.cmd", "-Djava.awt.headless=false", "-H:+UnlockExperimentalVMOptions", "-H:+AddAllCharsets", "-H:IncludeResources=.*",
                "-H:ConfigurationFileDirectories=../configs/default,../configs/" + args[0], "--initialize-at-run-time=sun.net.dns.ResolverConfigurationImpl", "--enable-http", "--enable-https", "--no-fallback", "-cp", libsBuilder.toString(), "net.minecraft.client.main.Main", args[0]);
        builder.directory(buildDir);

        boolean compilePGOs = false;
        if(args.length > 1) {
            compilePGOs = Boolean.parseBoolean(args[1]);
            if(compilePGOs) {
                List<String> commands = builder.command();
                commands.add(builder.command().size() - 4, "--pgo-instrument");
                builder.command(commands);
            }
        }

        System.out.println(builder.command());
        Process process = builder.start();

        while(process.isAlive()) {
            System.out.println(process.inputReader().readLine());
        }

        if(compilePGOs) {
            // Load MC to generate IPROF file
            builder = new ProcessBuilder();
            builder.command("./native-build/" + args[0] + ".exe", "--accessToken", settings.getVariable(LauncherVariables.AUTH_ACCESS_TOKEN),
                    "--assetIndex", "8", "--username", settings.getVariable(LauncherVariables.AUTH_PLAYER_NAME), "--uuid", settings.getVariable(LauncherVariables.AUTH_UUID), "--version", "MCNative");
            builder.directory(new File("./install"));
            process = builder.start();

            System.out.println("Generate a world, then join a server afterwards. It might take a while.");
            while(process.isAlive()) {
                System.out.println(process.errorReader().readLine());
            }

            builder = new ProcessBuilder();
            builder.command(System.getenv("GRAALVM_HOME") + "/bin/native-image.cmd", "-Djava.awt.headless=false", "-H:+UnlockExperimentalVMOptions", "-H:+AddAllCharsets", "-H:IncludeResources=.*",
                    "-H:ConfigurationFileDirectories=../configs/default,../configs/" + args[0], "--initialize-at-run-time=sun.net.dns.ResolverConfigurationImpl", "--enable-http", "--enable-https", "--no-fallback", "--pgo=../install/default.iprof", "-cp", libsBuilder.toString(), "net.minecraft.client.main.Main", args[0]);
            System.out.println(builder.command());
            builder.directory(buildDir);
            process = builder.start();

            while(process.isAlive()) {
                System.out.println(process.inputReader().readLine());
            }
        }
    }

    private static String createLWJGL(DownloadableFile file) {
        String fileName = file.getDownloadedFile().getName();
        String[] nameParts = fileName.split("-");
        String baseName = nameParts[0];

        boolean isNatives = file.getPath().contains("natives");
        boolean matchesLWJGLPattern = fileName.matches("lwjgl-3\\.([0-9])\\.([0-9]).*");

        if (matchesLWJGLPattern) {
            if (isNatives) {
                return formatLWJGLName(baseName, nameParts[2], nameParts[3]);
            } else {
                return baseName + "/" + baseName + ".jar";
            }
        } else {
            if (isNatives) {
                return formatLWJGLName(baseName, nameParts[1], nameParts[3], nameParts[4]);
            } else {
                return baseName + "-" + nameParts[1] + "/" + baseName + "-" + nameParts[1] + ".jar";
            }
        }
    }

    private static String formatLWJGLName(String baseName, String... parts) {
        StringBuilder sb = new StringBuilder(baseName);
        sb.append("-").append(parts[0]).append("/");
        sb.append(baseName).append("-").append(parts[0]).append("-");
        for (int i = 1; i < parts.length; i++) {
            sb.append(parts[i]);
            if (i < parts.length - 1) {
                sb.append("-");
            }
        }
        if (!parts[parts.length - 1].endsWith(".jar")) {
            sb.append(".jar");
        }
        return sb.toString();
    }
}
