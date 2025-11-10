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
import org.angelauramc.judgelib.launcher.BaseJavaLauncher;
import org.angelauramc.judgelib.util.json.MinecraftVersion;
import org.angelauramc.judgelib.util.json.auth.JudgeLibAccount;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    public static File buildDir = new File(installDir, "native-build");

    private static String version;
    private static String graalvmInstall;
    private static String gc;
    private static boolean fabric;
    public static String[] fabricRunTimeClasses;
    public static String[] fabricBuildTimeClasses;

    private static List<IProcessor> processors = new ArrayList<>();

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("MCNativeBuilder").build()
                .defaultHelp(true)
                .description("Build Minecraft Native Images.");
        parser.addArgument("--version")
                .setDefault("1.21.10")
                .help("Version of Minecraft to download and compile");
        parser.addArgument("--graalvm")
                .required(true)
                .help("Where your graalvm sdk is.");
        parser.addArgument("--processors")
                .nargs("*")
                .help("Fully-qualified name of additional processors to use.");
        parser.addArgument("--gc")
                .setDefault("serial")
                .help("Garbage collector to use.");
        parser.addArgument("--fabric")
                .setDefault(false)
                .type(Boolean.class)
                .help("Installs Fabric support. Read the wiki for usage");
        parser.addArgument("--fabric-run-time")
                .setDefault("")
                .help("Installs Fabric support. Read the wiki for usage");
        parser.addArgument("--fabric-build-time")
                .setDefault("")
                .help("Installs Fabric support. Read the wiki for usage");

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
        fabric = ns.getBoolean("fabric");
        fabricRunTimeClasses = ns.getString("fabric_run_time").split(",");
        fabricBuildTimeClasses = ns.getString("fabric_build_time").split(",");

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
                MinecraftVersion version = LoaderType.FABRIC.getMetadata().getMinecraftVersion(Main.version);
                install = JudgeLibAPI.getInstance().install("MCNative", "", version, Path.of(System.getProperty("user.dir"), "install"),
                    Path.of(System.getProperty("user.dir"), "assets"), Path.of(System.getProperty("user.dir"), "libraries"));
            } else {
                MinecraftVersion version = LoaderType.VANILLA.getMetadata().getMinecraftVersion(Main.version);
                install = JudgeLibAPI.getInstance().install("MCNative", "", version, Path.of(System.getProperty("user.dir"), "install"),
                        Path.of(System.getProperty("user.dir"), "assets"), Path.of(System.getProperty("user.dir"), "libraries"));
            }
            LOGGER.info("Built MC Classpath...");

            List<File> libs = new ArrayList<>();
            for (IProcessor processor : processors) {
                List<File> processedLibs = processor.processClasspath(install);
                if (processedLibs != null) {
                    libs.addAll(processedLibs);
                }
            }

            LOGGER.info("Injecting MCNativeTools-9.8 into Classpath");
            File mclibFile = new File(installDir, "MCNativeTools-9.8.jar");
            if (!mclibFile.exists()) {
                try (InputStream stream = Main.class.getResourceAsStream("extraLibs/MCNativeTools-9.8.jar")) {
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

            LOGGER.info("Logging in...");
            CompletableFuture<JudgeLibAccount> accountFuture = JudgeLibAPI.getInstance().startLogin("TheJudge156");
            JudgeLibAccount account = accountFuture.join();
            LOGGER.info("Logged in...");
            LOGGER.info("Launching MC with tracing agent, please follow the instructions in the README to prevent runtime crashes");
            JudgeLibAPI.getInstance().chooseLauncher("JRE");
            BaseJavaLauncher.INSTANCE.setup(new File(graalvmInstall, "bin/java").getAbsolutePath());
            JudgeLibAPI.getInstance().launchInstall(install, account, new String[0], new String[] {"-agentlib:native-image-agent=config-merge-dir=" + buildDir + "/minecraft-configs.jsonz"});

            List<String> extraArgs = new ArrayList<>();
            for (IProcessor processor : processors) {
                List<String> tempExtraArgs = processor.preBuild(install);
                if (tempExtraArgs != null) {
                    extraArgs.addAll(tempExtraArgs);
                }
            }

            LOGGER.info("Building Native Image...");
            try {
                Process process = startCompile(install, libs, extraArgs.toArray(new String[0]));
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

    private static Process startCompile(JudgeLibInstall install, List<File> classPath, String... extraArgs) throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(graalvmInstall + "/bin/native-image" + OS_EXT_SHELL, "-H:ConfigurationFileDirectories=" + installDir + "/configs/" + version, "-cp",
                install.classpath + File.pathSeparator + classPath.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)), "--gc=" + gc,
                "--future-defaults=all", "-Os", "--no-fallback", "",
                install.mainClass, "-o", version);
        for(String arg : extraArgs) {
            List<String> commands = builder.command();
            commands.add(builder.command().size(), arg);
            builder.command(commands);
        }
        System.out.println(builder.command());
        builder.directory(buildDir);
        if(!buildDir.exists()) {
            buildDir.mkdirs();
        }
        return builder.start();
    }
}
