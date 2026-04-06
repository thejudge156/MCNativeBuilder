package me.judge.mcnativebuilder;

import com.microsoft.aad.msal4j.DeviceCode;
import me.judge.mcnativebuilder.processors.FabricProcessor;
import me.judge.mcnativebuilder.processors.IProcessor;
import me.judge.mcnativebuilder.processors.LWJGLProcessor;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.angelauramc.judgelib.JudgeLibAPI;
import org.angelauramc.judgelib.impl.InitInfo;
import org.angelauramc.judgelib.installer.JudgeLibInstall;
import org.angelauramc.judgelib.installer.LoaderType;
import org.angelauramc.judgelib.installer.Source;
import org.angelauramc.judgelib.launcher.BaseJavaLauncher;
import org.angelauramc.judgelib.util.json.MinecraftVersion;
import org.angelauramc.judgelib.util.json.auth.JudgeLibAccount;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Main {
    static {
        if(System.getProperty("os.name").contains("Windows")) {
            OS_EXT = ".exe";
            OS_EXT_SHELL = ".cmd";
        } else {
            OS_EXT = "";
            OS_EXT_SHELL = "";
        }
    }
    public static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static String OS_EXT;
    public static String OS_EXT_SHELL;

    public static String homePath = System.getProperty("user.dir");
    public static File installDir = new File(homePath, "install");
    public static File buildDir;

    private static String version;
    private static String graalvmInstall;
    public static boolean shared;
    private static String gc;
    private static String mainClass;
    private static String buildMode;
    private static boolean fabric;
    private static boolean experimentalLWJGLPatch;
    private static boolean dryRun;
    public static String[] extraBuildArgs;
    private static MinecraftVersion mcVersion;

    private static final List<IProcessor> processors = new ArrayList<>();

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("MCNativeBuilder").build()
                .defaultHelp(true)
                .description("Build Minecraft Native Images.");
        parser.addArgument("--version")
                .setDefault("1.21.11")
                .help("Version of Minecraft to download and compile");
        parser.addArgument("--graalvm")
                .required(true)
                .help("Should be the home of your GraalVM SDK");
        parser.addArgument("--processors")
                .nargs("*")
                .help("Fully-qualified name of additional processors to use");
        parser.addArgument("--gc")
                .setDefault("serial")
                .help("Garbage collector to use");
        parser.addArgument("-O")
                .setDefault("s")
                .help("Set the build mode, read the Wiki for an explanation");
        parser.addArgument("--fabric")
                .setDefault(false)
                .type(Boolean.TYPE)
                .help("Installs Fabric support. Read the wiki for usage");
        parser.addArgument("--shared")
                .setDefault(false)
                .type(Boolean.TYPE)
                .help("Makes the output a shared library");
        parser.addArgument("--extra-build-args")
                .setDefault("")
                .help("Additional arguments for the native-image builder.");
        parser.addArgument("--experimental-lwjgl")
                .setDefault(false)
                .type(Boolean.TYPE)
                .help("Patches LWJGL's JNI class to use C Functions instead of JNI");
        parser.addArgument("--main-class")
                .setDefault("net.minecraft.client.main.Main")
                .help("Specify native image main class");
        parser.addArgument("--dry-run")
                .type(Boolean.TYPE)
                .setDefault(false)
                .help("Don't run Minecraft before native-imaging. Helpful if you already have reflection configs.");

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        List<String> processors = ns.getList("processors");

        gc = ns.getString("gc");
        version = ns.getString("version");
        graalvmInstall = ns.getString("graalvm");
        experimentalLWJGLPatch = ns.getBoolean("experimental_lwjgl");
        fabric = ns.getBoolean("fabric");
        extraBuildArgs = ns.getString("extra_build_args").split("\\?");
        mainClass = ns.getString("main_class");
        shared = ns.getBoolean("shared");
        dryRun = ns.getBoolean("dry_run");
        buildMode = ns.getString("O");

        LOGGER.info("Initialized MCNativeBuilder...");
        if(Main.fabric) {
            Main.processors.add(new FabricProcessor());
        }

        JudgeLibAPI api = JudgeLibAPI.getInstance();
        api.initialize(new InitInfo("d17a73a2-707c-40f5-8c90-d3eda0956f10", "https://login.microsoftonline.com/consumers/", ".", Main::printResult));

        if(processors != null) {
            for (String processorPath : processors) {
                try {
                    Main.processors.add((IProcessor) Class.forName(processorPath).getDeclaredConstructor().newInstance());
                    LOGGER.fine("Added processor " + processorPath);
                } catch (ClassNotFoundException | InvocationTargetException | InstantiationException |
                         IllegalAccessException | NoSuchMethodException e) {
                    LOGGER.severe("Could not find the processor with name " + processorPath);
                }
            }
        }
        start();
    }

    private static void printResult(DeviceCode res) {
        System.out.println(res.message());
    }

    public static void start() {
        processors.add(new LWJGLProcessor());
        LOGGER.fine("Finished adding processors...");

        try {
            JudgeLibInstall install;
            if (fabric) {
                mcVersion = LoaderType.FABRIC.getMetadata().getMinecraftVersion(Main.version);
                install = JudgeLibAPI.getInstance().install(mcVersion.id, "", mcVersion, Path.of(System.getProperty("user.dir"), "install"),
                    Path.of(System.getProperty("user.dir"), "assets"), Path.of(System.getProperty("user.dir"), "libraries"));
                install.addMod(Path.of(System.getProperty("user.dir"), "install"), "fabric-api", List.of(LoaderType.FABRIC), "fabric", Source.MODRINTH);
            } else {
                mcVersion = LoaderType.VANILLA.getMetadata().getMinecraftVersion(Main.version);
                install = JudgeLibAPI.getInstance().install(mcVersion.id, "", mcVersion, Path.of(System.getProperty("user.dir"), "install"),
                        Path.of(System.getProperty("user.dir"), "assets"), Path.of(System.getProperty("user.dir"), "libraries"));
            }
            buildDir = new File(installDir, install.installName);

            LOGGER.info("Built MC Classpath...");

            List<File> libs = new CopyOnWriteArrayList<>(Arrays.stream(install.classpath.split(File.pathSeparator)).map(File::new).toList());
            for (IProcessor processor : processors) {
                List<File> processedLibs = processor.processClasspath(install);
                if (processedLibs != null) {
                    libs.addAll(processedLibs);
                }
            }

            List<String> extraArgs = new ArrayList<>();
            LOGGER.info("Injecting MCNativeTools-9.8 into Classpath");
            File mclibFile = new File(installDir, "MCNativeTools-9.8.jar");
            if (!mclibFile.exists()) {
                try (InputStream stream = Main.class.getClassLoader().getResourceAsStream("extraLibs/MCNativeTools-9.8.jar")) {
                    if (stream != null) {
                        FileOutputStream fos = new FileOutputStream(mclibFile);
                        byte[] buffer = stream.readAllBytes();
                        fos.write(buffer);
                        fos.flush();
                        fos.close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            libs.add(mclibFile);

            if(!dryRun) {
                LOGGER.info("Logging in...");
                CompletableFuture<JudgeLibAccount> accountFuture = JudgeLibAPI.getInstance().startLogin("TheJudge156");
                JudgeLibAccount account = accountFuture.join();
                LOGGER.info("Logged in...");
                LOGGER.info("Launching MC with tracing agent, please follow the instructions in the README to prevent runtime crashes");
                JudgeLibAPI.getInstance().chooseLauncher("JRE");
                BaseJavaLauncher.INSTANCE.setup(new File(graalvmInstall, "bin/java").getAbsolutePath());
                JudgeLibAPI.getInstance().launchInstall(install, account, new String[0], new String[]{"-agentlib:native-image-agent=config-merge-dir=" + buildDir});
            }

            for (IProcessor processor : processors) {
                List<String> tempExtraArgs = processor.preBuild(install);
                if (tempExtraArgs != null) {
                    extraArgs.addAll(tempExtraArgs);
                }
            }
            if(shared)
                extraArgs.add("--shared");

            if(experimentalLWJGLPatch) {
                LOGGER.info("Injecting LWJGLPatch-9.8 into Classpath");
                libs.stream().filter((f) -> {
                    if(f == null) {
                        return false;
                    }
                    return f.getAbsolutePath().contains("org" + File.separator + "ow2" + File.separator + "asm");
                }).forEach(libs::remove);
                File lwjglPatch = new File(installDir, "LWJGLPatch-9.8.jar");
                if (!lwjglPatch.exists()) {
                    try (InputStream stream = Main.class.getClassLoader().getResourceAsStream("extraLibs/LWJGLPatch-9.8.jar")) {
                        if (stream != null) {
                            FileOutputStream fos = new FileOutputStream(lwjglPatch);
                            byte[] buffer = stream.readAllBytes();
                            fos.write(buffer);
                            fos.flush();
                            fos.close();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                extraArgs.add("-J-javaagent:" + lwjglPatch.getAbsolutePath());
            }

            if(fabric) {
                mainClass = "me.judge.fabric.FabricMain";
                libs.remove(new File(install.mainJar));
                libs.stream().filter((f) -> f.getName().contains("datafixerupper")).findFirst().ifPresent((file) -> {
                    libs.remove(file);
                    extraArgs.add("-J-Dfabric.gameLibraries=" + file.getAbsolutePath());
                });
            }

            extraArgs.addAll(List.of(extraBuildArgs));

            LOGGER.info("Building Native Image...");
            try {
                Process process = startCompile(libs, extraArgs.toArray(new String[0]));
                BufferedReader errors = process.errorReader();
                BufferedReader info = process.inputReader();
                while (process.isAlive()) {
                    if (info.ready()) {
                        LOGGER.info(info.readLine());
                    }
                    if (errors.ready()) {
                        LOGGER.severe(errors.readLine());
                    }
                }
                errors.close();
                info.close();
            } catch (IOException e) {
                LOGGER.severe("Error while compiling! " + e.getMessage());
            }
            LOGGER.info("Built to path " + buildDir.getAbsolutePath());
        } catch (ReflectiveOperationException | IOException | ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static Process startCompile(List<File> classPath, String... extraArgs) throws IOException {
        ProcessBuilder builder = new ProcessBuilder();

        File argFile = new File(buildDir, "command.txt");
        if(!argFile.getParentFile().exists()) {
            argFile.getParentFile().mkdirs();
        }
        if(argFile.exists()) {
            argFile.delete();
            argFile.createNewFile();
        }

        FileWriter writer = new FileWriter(argFile);

        for(String arg : List.of("-H:ConfigurationFileDirectories=" + buildDir, "-cp",
                classPath.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)), "--gc=" + gc,
                "--enable-url-protocols=https,http",
                "-H:+AddAllCharsets", "-H:+IncludeAllLocales", "-H:IncludeResources=resourcepacks/.*", "-g",
                "-H:IncludeResources=data/.*", "-H:IncludeResources=assets/.*", "-H:+AddAllCharsets", "-H:+IncludeAllLocales",
                "--initialize-at-run-time=sun.net.dns.ResolverConfigurationImpl")) {
            writer.write(" ");
            writer.write(arg);
        }

        // TODO: Arm
        writer.write(" ");
        writer.write("-march=x86-64-v2");

        if(buildMode.equals("pgoi")) {
            writer.write(" ");
            writer.write("--pgo-instrument");
        } else if(buildMode.equals("pgo")) {
            writer.write(" ");
            writer.write("--pgo=default.iprof");
        } else {
            writer.write(" ");
            writer.write("-O" + buildMode);
        }

        for(String arg : extraArgs) {
            writer.write(" ");
            writer.write(arg);
        }
        writer.flush();
        writer.close();

        builder.command(graalvmInstall + "/bin/native-image" + OS_EXT_SHELL, "@" + argFile.getName(),
                mainClass, "-o", version);
        builder.directory(buildDir);
        if(!buildDir.exists()) {
            buildDir.mkdirs();
        }
        return builder.start();
    }
}
