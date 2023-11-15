package me.judge.mcnativebuilder;

import net.hycrafthd.minecraft_downloader.*;
import net.hycrafthd.minecraft_downloader.library.DownloadableFile;
import net.hycrafthd.minecraft_downloader.settings.LauncherVariables;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;
import net.hycrafthd.minecraft_downloader.util.FileUtil;
import net.sourceforge.argparse4j.ArgumentParserBuilder;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
    public static final String LWJGL_DOWNLOAD = "https://build.lwjgl.org/release/3.3.3/bin/";
    private static String version;
    private static Boolean profileGuidedOptimizations;
    private static String graalvmInstall;
    private static String authToken;
    private static String uuid;
    private static String customJar;
    private static List<String> extraLibs = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        ArgumentParser parser = ArgumentParsers.newFor("MCNativeBuilder").build()
                .defaultHelp(true)
                .description("Build Minecraft Native Images.");
        parser.addArgument("--version")
                .choices("1.18.2", "1.19.0", "1.19.2", "1.19.4", "1.20.2")
                .setDefault("1.20.2")
                .help("Version of Minecraft to compile");
        parser.addArgument("--accessToken")
                .setDefault("0")
                .help("Your Minecraft accounts auth token.");
        parser.addArgument("--uuid")
                .help("Your Minecraft accounts UUID.");
        parser.addArgument("--pgos")
                .choices(true, false)
                .setDefault(true)
                .type(Boolean.class)
                .help("Whether or not to enable Profile-Guided-Optimizations.");
        parser.addArgument("--graalvm")
                .help("Where your graalvm sdk is.");
        parser.addArgument("--customJar")
                .help("Path to a custom main jar file");
        parser.addArgument("--extraLibs")
                .type(List.class)
                .help("Add extra files to the classpath");

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        version = ns.get("version");
        profileGuidedOptimizations = ns.getBoolean("pgos");
        graalvmInstall = ns.get("graalvm");
        authToken = ns.get("accessToken");
        uuid = ns.get("uuid");
        customJar = ns.get("customJar");
        extraLibs = ns.getList("extraLibs");

        ProvidedSettings settings = new ProvidedSettings(version, new File("./install"), new File("./install"));
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

            libsBuilder.append(lib).append(";");
        }
        libsBuilder.append(System.getProperty("user.dir")).append("/libs/JFRSub-1.0-SNAPSHOT.jar;");
        MinecraftClasspathBuilder.launch(settings, false);

        if(extraLibs != null) {
            for (String lib : extraLibs) {
                settings.addVariable(LauncherVariables.CLASSPATH, settings.getVariable(LauncherVariables.CLASSPATH) + lib + ";");
                libsBuilder.append(lib + ";");
            }
        }

        if(customJar != null) {
            settings.addVariable(LauncherVariables.PRIMARY_JAR, customJar);
        } else {
            libsBuilder.append(settings.getClientJarFile().getAbsolutePath());
        }

        if(authToken == null || uuid == null){
            MinecraftAuthenticator.launch(settings, new File("./auth.json"), "web", false);
        } else {
            settings.addVariable(LauncherVariables.AUTH_ACCESS_TOKEN, authToken);
            settings.addVariable(LauncherVariables.AUTH_UUID, uuid);
        }

        MinecraftJavaRuntimeSetup.launch(settings, false, new File(graalvmInstall + "/bin/javaw.exe"));
        System.out.println("Waiting for Minecraft to close...");
        System.out.println("Generate a world, go to the end, leave, join a server.");
        MinecraftLauncher.launch(settings, "-agentlib:native-image-agent=config-merge-dir=../configs/" + version);

        File buildDir = new File("./native-build");
        buildDir.mkdirs();

        Process process;
        if(profileGuidedOptimizations) {
            process = startCompile(libsBuilder.toString(), "./native-build", "--pgo-instrument");
        } else {
            process = startCompile(libsBuilder.toString(), "./native-build");
        }

        while(process.isAlive()) {
            System.out.println(process.inputReader().readLine());
        }

        if(profileGuidedOptimizations) {
            // Load MC to generate IPROF file
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("./native-build/" + version + ".exe", "--accessToken", settings.getVariable(LauncherVariables.AUTH_ACCESS_TOKEN),
                    "--assetIndex", settings.getVariable(LauncherVariables.ASSET_INDEX_NAME), "--username", settings.getVariable(LauncherVariables.AUTH_PLAYER_NAME), "--uuid", settings.getVariable(LauncherVariables.AUTH_UUID), "--version", "MCNative");
            builder.directory(new File("./install"));
            process = builder.start();

            System.out.println("Generate a world, then join a server afterwards. It might take a while.");
            while(process.isAlive()) {
                System.out.println(process.errorReader().readLine());
            }

            process = startCompile(libsBuilder.toString(), "./native-build", "--pgo=../install/default.iprof");
            while(process.isAlive()) {
                System.out.println(process.inputReader().readLine());
            }
        }
    }

    private static Process startCompile(String libs, String buildDir, String... extraArgs) throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(graalvmInstall + "/bin/native-image.cmd", "-Djava.awt.headless=false", "-H:+UnlockExperimentalVMOptions", "-H:+AddAllCharsets", "-H:IncludeResources=.*",
                "-H:ConfigurationFileDirectories=../configs/default,../configs/" + version, "--initialize-at-run-time=sun.net.dns.ResolverConfigurationImpl", "--enable-http", "--enable-https", "--no-fallback", "-cp", libs, "net.minecraft.client.main.Main", version);
        for(String arg : extraArgs) {
            List<String> commands = builder.command();
            commands.add(builder.command().size() - 4, arg);
            builder.command(commands);
        }
        System.out.println(builder.command());
        builder.directory(new File(buildDir));
        return builder.start();
    }

    private static String createLWJGL(DownloadableFile file) {
        String fileName = file.getDownloadedFile().getName();
        String[] nameParts = fileName.split("-");
        String baseName = nameParts[0];

        boolean isNatives = file.getPath().contains("natives");
        boolean matchesLWJGLPattern = fileName.matches("lwjgl-3\\.([0-9])\\.([0-9]).*");

        if (matchesLWJGLPattern) {
            if (isNatives) {
                return formatLWJGLName(baseName, true, nameParts[2], nameParts[3]);
            } else {
                return baseName + "/" + baseName + ".jar";
            }
        } else {
            if (isNatives) {
                return formatLWJGLName(baseName, false, nameParts[1], nameParts[3], nameParts[4]);
            } else {
                return baseName + "-" + nameParts[1] + "/" + baseName + "-" + nameParts[1] + ".jar";
            }
        }
    }

    private static String formatLWJGLName(String baseName, boolean isSingleNamespace, String... parts) {
        StringBuilder sb = new StringBuilder(baseName);
        if(!isSingleNamespace) {
            sb.append("-").append(parts[0]).append("/");
        } else {
            sb.append("/");
        }
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
