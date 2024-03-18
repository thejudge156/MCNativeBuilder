package me.judge.mcnativebuilder;

import me.judge.mcnativebuilder.util.ClasspathUtil;
import net.hycrafthd.minecraft_downloader.*;
import net.hycrafthd.minecraft_downloader.library.DownloadableFile;
import net.hycrafthd.minecraft_downloader.settings.LauncherVariables;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;
import net.hycrafthd.minecraft_downloader.util.FileUtil;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class Main {
    static {
        if(System.getProperty("os.name").contains("Windows")) {
            OS_EXT = ".exe";
            OS_EXT_SHELL = ".cmd";
            OS_EXT_BAT = ".bat";
            OS_SEPARATOR = ";";
        } else {
            OS_EXT = "";
            OS_EXT_SHELL = "";
            OS_EXT_BAT = "";
            OS_SEPARATOR = ":";
        }
    }

    public static String OS_EXT;
    public static String OS_EXT_SHELL;
    public static String OS_EXT_BAT;
    public static String OS_SEPARATOR;
    public static final String LWJGL_DOWNLOAD = "https://build.lwjgl.org/release/3.3.3/bin/";
    private static String version;
    private static String path;
    private static Boolean profileGuidedOptimizations;
    private static String garbageCollector;
    private static String graalvmInstall;
    private static String authToken;
    private static String uuid;
    private static String customJar;
    private static String fabricEntryPoints;
    private static String fabricEntryPointsClient;
    private static List<String> extraLibs = new ArrayList<>();
    private static List<String> mixinMods = new ArrayList<>();
    private static List<String> mixinRefMaps = new ArrayList<>();
    private static List<String> mixinPackages = new ArrayList<>();
    private static boolean fabric;

    public static void main(String[] args) throws Exception {
        ArgumentParser parser = ArgumentParsers.newFor("MCNativeBuilder").build()
                .defaultHelp(true)
                .description("Build Minecraft Native Images.");
        parser.addArgument("--version")
                .setDefault("1.20.2")
                .help("Version of Minecraft to compile");
        parser.addArgument("--accessToken")
                .setDefault("0")
                .help("Your Minecraft accounts auth token.");
        parser.addArgument("--uuid")
                .help("Your Minecraft accounts UUID.");
        parser.addArgument("--pgos")
                .choices(true, false)
                .setDefault(false)
                .type(Boolean.class)
                .help("Whether or not to enable Profile-Guided-Optimizations.");
        parser.addArgument("--gc")
                .setDefault("serial")
                .help("Garbage collector to use for the outputted image.");
        parser.addArgument("--graalvm")
                .help("Where your graalvm sdk is.");
        parser.addArgument("--customJar")
                .help("Path to a custom main jar file");
        parser.addArgument("--replacedLibs")
                .help("MC Libraries to replace")
                .nargs("*");
        parser.addArgument("--extraLibs")
                .nargs("*")
                .help("Add extra files to the classpath");
        parser.addArgument("--fabric")
                .choices(true, false)
                .setDefault(false)
                .type(Boolean.class)
                .help("Adds a VERY basic fabric patch");
        parser.addArgument("--mixinMods")
                .nargs("*")
                .help("Add mixin code jars to the classpath");
        parser.addArgument("--mixinPackages")
                .nargs("*")
                .help("Add mixin packages to process");
        parser.addArgument("--mixinRefMaps")
                .nargs("*")
                .help("Refmaps for coresponding mods");
        parser.addArgument("--fabricEntrypoints")
                .help("Common entrypoints for coresponding mods, comma seperated");
        parser.addArgument("--fabricEntrypointsClient")
                .help("Client entrypoints for coresponding mods, comma seperated");

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        version = ns.get("version");
        profileGuidedOptimizations = ns.getBoolean("pgos");
        garbageCollector = ns.getString("gc");
        graalvmInstall = ns.get("graalvm");
        authToken = ns.get("accessToken");
        uuid = ns.get("uuid");
        customJar = ns.get("customJar");
        mixinMods = ns.getList("mixinMods");
        mixinPackages = ns.getList("mixinPackages");
        mixinRefMaps = ns.getList("mixinRefMaps");
        extraLibs = ns.getList("extraLibs");
        fabric = ns.getBoolean("fabric");
        fabricEntryPoints = ns.get("fabricEntrypoints");
        fabricEntryPointsClient = ns.get("fabricEntrypointsClient");

        path = System.getProperty("user.dir") + "/" + version;

        ProvidedSettings settings = new ProvidedSettings(version, new File(path + "/install"), new File(path + "/install"));
        MinecraftParser.launch(settings);

        MinecraftDownloader.launch(settings, false, null, false, false);

        List<File> libsBuilder = new ArrayList<>();
        for(DownloadableFile file : settings.getGeneratedSettings().getDownloadableFiles()) {
            if(file.isNative()) {
                continue;
            }
            if(file.getDownloadedFile().getName().contains("lwjgl")) {
                String newLWJGL = createLWJGL(file);
                System.out.printf("Replacing %s with 3.3.3 Version.\n", file.getDownloadedFile().getName());
                FileUtil.downloadFile(LWJGL_DOWNLOAD + newLWJGL, file.getDownloadedFile(), null);
            }

            libsBuilder.add(file.getDownloadedFile());
        }
        MinecraftClasspathBuilder.launch(settings, true);
        libsBuilder.addAll(settings.getGeneratedSettings().getClassPath());

        if(fabric) {
            libsBuilder.add(new File(System.getProperty("user.dir"), "libs/FabricShim.jar"));
        }

        if(extraLibs != null) {
            libsBuilder.addAll(extraLibs.stream().map(File::new).toList());
        }
        settings.addVariable(LauncherVariables.CLASSPATH, libsBuilder.stream().map(File::getAbsolutePath).collect(Collectors.joining(OS_SEPARATOR)));

        if(customJar != null) {
            String libs = settings.getVariable(LauncherVariables.CLASSPATH);
            StringBuilder newLibs = new StringBuilder();
            for(String lib : libs.split(OS_SEPARATOR)) {
                if(!lib.contains(version + "-client.jar")) {
                    newLibs.append(lib + OS_SEPARATOR);
                }
            }
            newLibs.append(customJar);
            settings.addVariable(LauncherVariables.CLASSPATH, newLibs.toString());
        }

        if(mixinMods != null || fabric) {
            File outputDir = new File(path, "mixinOutput");
            boolean mixinsGenerated = outputDir.exists();
            if(!mixinsGenerated) {
                ProcessBuilder builder = new ProcessBuilder();
                File classPath = ClasspathUtil.shortenClasspath(System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/Arbiter-1.0.0-slim.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/Executor-1.0.0.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/jopt-simple-6.0-alpha-3.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/sponge-mixin-0.12.5+mixin.0.8.5.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/mixinextras-common-0.3.2.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/log4j-slf4j18-impl-2.17.1.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/log4j-core-2.17.1.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/log4j-api-2.17.1.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/guava-31.0.1-jre.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/asm-commons-9.2.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/asm-util-9.2.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/asm-analysis-9.2.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/asm-tree-9.2.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/commons-lang3-3.3.2.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/commons-io-2.11.0.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/gson-2.8.9.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/forgeflower-2.0.605.1.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/slf4j-api-1.8.0-beta4.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/failureaccess-1.0.1.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/jsr305-3.0.2.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/checker-qual-3.12.0.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/error_prone_annotations-2.7.1.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/j2objc-annotations-1.3.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/asm-9.2.jar" + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/lib/annotations-20.1.0.jar", path, OS_SEPARATOR);
                builder.command(graalvmInstall + "/bin/java" + OS_EXT, "-Dmixin.env.refMapRemappingFile=" + mixinRefMaps.stream().collect(Collectors.joining(OS_SEPARATOR)), "-classpath", classPath.getAbsolutePath(), "net.minecraftforge.ducker.Main", "-o", path + "/mixinOutput", "--transformer",
                        "MIXIN_METHOD_REMAPPER_PRIVATIZER", "--transformer", "ACCESSOR_DESYNTHESIZER", "--transformer", "--transformer", "OVERWRITE_FIXER", "--transformer", "PUBLICIZER");

                for (String target : settings.getVariable(LauncherVariables.CLASSPATH).split(OS_SEPARATOR)) {
                    List<String> list = builder.command();
                    list.add("-t");
                    list.add(target);
                    builder.command(list);
                }

                if(fabric) {
                    List<String> list = builder.command();
                    list.add("-m");
                    list.add(System.getProperty("user.dir") + "/libs/FabricMixinShim.jar");
                    builder.command(list);
                }

                if(mixinMods != null) {
                    for (String lib : mixinMods) {
                        List<String> list = builder.command();
                        list.add("-m");
                        list.add(lib);
                        builder.command(list);
                    }
                }

                for (String packages : mixinPackages) {
                    List<String> list = builder.command();
                    list.add("-p");
                    list.add(packages);
                    builder.command(list);
                }

                System.out.println("Args: " + builder.command());
                Process process = builder.start();
                int i = 0;
                while (process.isAlive()) {
                    if(i > 999999) {
                        System.out.println("Mixing took too long, open an issue if this causes a problem.");
                        process.destroy();
                        break;
                    }
                    String output = process.inputReader().readLine();
                    if (output != null) {
                        System.out.println(output);
                    }
                    i++;
                }
            }

            ZipFile customJarZip = new ZipFile(customJar);
            customJarZip.extractAll(path + "/" + version + "-extracted");

            FileVisitor<Path> deleteVisitor = new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            };

            File com = new File(path + "/" + version + "-extracted/com");
            Files.walkFileTree(com.toPath(), deleteVisitor);
            File net = new File(path + "/" + version + "-extracted/net");
            Files.walkFileTree(net.toPath(), deleteVisitor);

            Files.walkFileTree(Path.of(path + "/mixinOutput/com"), new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path replacedPath = Path.of(path + "/mixinOutput/com");
                    Path newPath = Path.of(path + "/" + version + "-extracted/com" +
                            file.toString().replace(replacedPath.toString(), ""));
                    Files.createDirectories(newPath.getParent());
                    Files.copy(file, newPath, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
            Files.walkFileTree(Path.of(path + "/mixinOutput/net"), new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path replacedPath = Path.of(path + "/mixinOutput/net");
                    Path newPath = Path.of(path + "/" + version + "-extracted/net" +
                            file.toString().replace(replacedPath.toString(), ""));
                    Files.createDirectories(newPath.getParent());
                    Files.copy(file, newPath, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });

            ZipFile newCustomJar = new ZipFile(path + "/newClientJar.jar");
            if(newCustomJar.getFile().exists()) {
                newCustomJar.getFile().delete();
            }

            File sourceFolder = new File(path + "/" + version + "-extracted/");
            ZipParameters param = new ZipParameters();
            param.setIncludeRootFolder(false);
            newCustomJar.createZipFileFromFolder(sourceFolder, param, false, -1);

            if(customJar != null) {
                String libs = settings.getVariable(LauncherVariables.CLASSPATH);
                StringBuilder newLibs = new StringBuilder();
                for(String lib : libs.split(OS_SEPARATOR)) {
                    if(!lib.contains(customJar)) {
                        newLibs.append(lib + OS_SEPARATOR);
                    }
                }
                customJar = newCustomJar.getFile().getAbsolutePath();
                newLibs.append(customJar);
                settings.addVariable(LauncherVariables.CLASSPATH, newLibs.toString());
            }

            // Add mod to classpath
            for (String mod : mixinMods) {
                settings.addVariable(LauncherVariables.CLASSPATH, settings.getVariable(LauncherVariables.CLASSPATH) + OS_SEPARATOR + mod);
            }
            settings.addVariable(LauncherVariables.CLASSPATH, settings.getVariable(LauncherVariables.CLASSPATH) + OS_SEPARATOR + System.getProperty("user.dir") + "/libs/Arbiter-1.0.0/libs/Arbiter-1.0.0/lib/sponge-mixin-0.11.4+mixin.0.8.5.jar");
        }

        if(customJar != null && mixinMods != null)  {
            URI uri = new URI("jar:file", new File(customJar).toURI().getPath(), null);
            Map<String, String> env = new HashMap<>();

            try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
                for(File mod : mixinMods.stream().map(File::new).toList()) {
                    URI uri2 = new URI("jar:file", mod.toURI().getPath(), null);

                    List<String> entries = new ArrayList<>();
                    try (FileSystem modZipFs = FileSystems.newFileSystem(uri2, env)) {
                        Files.walkFileTree(modZipFs.getPath("/assets/").toAbsolutePath(), new FileVisitor<>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                entries.add(file.toString());
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                                return FileVisitResult.CONTINUE;
                            }
                        });

                        for(String entry : entries) {
                            if(!Files.exists(zipfs.getPath(entry).getParent())) {
                                Files.createDirectories(zipfs.getPath(entry));
                            }
                            Files.copy(modZipFs.getPath(entry), zipfs.getPath(entry), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        }

        if(authToken == null || uuid == null){
            MinecraftAuthenticator.launch(settings, new File(path, "auth.json"), "web", false);
        } else {
            settings.addVariable(LauncherVariables.AUTH_ACCESS_TOKEN, authToken);
            settings.addVariable(LauncherVariables.AUTH_UUID, uuid);
        }

        libsBuilder.add(new File(System.getProperty("user.dir"), "libs/JFRSub.jar"));
        MinecraftJavaRuntimeSetup.launch(settings, false, new File(graalvmInstall + "/bin/java" + OS_EXT));
        System.out.println("Waiting for Minecraft to close...");
        System.out.println("Generate a world, go to the end, leave, join a server.");

        String extraArgs = "";
        if(fabricEntryPointsClient != null) {
            extraArgs += " -Dfabric.entrypoints.client=" + fabricEntryPointsClient;
        }
        if(fabricEntryPoints != null) {
            extraArgs += " -Dfabric.entrypoints=" + fabricEntryPoints;
        }

        MinecraftLauncher.launch(settings, "-noverify -agentlib:native-image-agent=config-merge-dir=../configs/" + version + extraArgs);

        File buildDir = new File(path, "native-build");
        buildDir.mkdirs();

        Process process;
        if(profileGuidedOptimizations) {
            File classPath = ClasspathUtil.shortenClasspathGraal(libsBuilder.stream().map(File::getAbsolutePath).collect(Collectors.joining(OS_SEPARATOR)), path, OS_SEPARATOR);
            process = startCompile(classPath.getAbsolutePath(),
                    path + "/native-build", "--pgo-instrument", "--gc=" + garbageCollector);
        } else {
            File classPath = ClasspathUtil.shortenClasspathGraal(libsBuilder.stream().map(File::getAbsolutePath).collect(Collectors.joining(OS_SEPARATOR)), path, OS_SEPARATOR);
            process = startCompile(classPath.getAbsolutePath(),
                    path + "/native-build", "--gc=" + garbageCollector);
        }

        while(process.isAlive()) {
            String output = process.inputReader()   .readLine();
            if(output != null) {
                System.out.println(output);
            }
        }

        if(profileGuidedOptimizations) {
            // Load MC to generate IPROF file
            ProcessBuilder builder = new ProcessBuilder();
            builder.command(path + "/native-build/" + version + OS_EXT, "--accessToken", settings.getVariable(LauncherVariables.AUTH_ACCESS_TOKEN),
                    "--assetIndex", settings.getVariable(LauncherVariables.ASSET_INDEX_NAME), "--username", settings.getVariable(LauncherVariables.AUTH_PLAYER_NAME), "--uuid", settings.getVariable(LauncherVariables.AUTH_UUID), "--version", "MCNative");
            builder.directory(new File(path, "/install"));
            process = builder.start();

            System.out.println("Generate a world, then join a server afterwards. It might take a while.");
            while(process.isAlive()) {
                System.out.println(process.errorReader().readLine());
            }

            process = startCompile(libsBuilder.toString(), "./native-build", "--pgo=../install/default.iprof");
            while(process.isAlive()) {
                String output = process.errorReader().readLine();
                if (output != null) {
                    System.out.println(output);
                }
            }
        }
    }

    private static Process startCompile(String libs, String buildDir, String... extraArgs) throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(graalvmInstall + "/bin/native-image" + OS_EXT_SHELL, "-Djava.awt.headless=false", "-H:+AddAllCharsets", "-H:IncludeResources=.*",
                "-H:ConfigurationFileDirectories=" + System.getProperty("user.dir") + "/defaultConfig," + System.getProperty("user.dir") + "/" + version + "/configs/" + version, "--initialize-at-run-time=sun.net.dns.ResolverConfigurationImpl", "--enable-http", "--enable-https", "--no-fallback", "-cp", libs, "net.minecraft.client.main.Main", version);
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
