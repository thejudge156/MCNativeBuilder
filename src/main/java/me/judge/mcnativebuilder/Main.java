package me.judge.mcnativebuilder;

import com.microsoft.aad.msal4j.DeviceCode;
import com.microsoft.aad.msal4j.PublicClientApplication;
import me.judge.mcnativebuilder.internal.TokenCache;
import me.judge.mcnativebuilder.internal.Util;
import me.judge.mcnativebuilder.internal.processors.*;
import net.flamgop.auth.AuthSession;
import net.flamgop.auth.Authenticator;
import net.flamgop.auth.LoginFlow;
import net.flamgop.auth.minecraft.MinecraftAPI;
import net.flamgop.auth.minecraft.MinecraftProfile;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import okhttp3.OkHttpClient;
import org.angelauramc.judgelib.JudgeLibAPI;
import org.angelauramc.judgelib.installer.DownloadTracker;
import org.angelauramc.judgelib.installer.JudgeLibVersion;
import org.angelauramc.judgelib.installer.LoaderType;
import org.angelauramc.judgelib.instance.Instance;
import org.angelauramc.judgelib.instance.InstanceFormat;
import org.angelauramc.judgelib.instance.InstanceManager;
import org.angelauramc.judgelib.launcher.Platform;
import org.angelauramc.judgelib.util.JsonUtil;
import org.angelauramc.judgelib.util.json.JudgeLibAccount;
import org.angelauramc.judgelib.util.json.MinecraftVersion;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class.getName());

    public static String OS_EXT;
    public static String OS_EXT_SHELL;

    public static String homePath = System.getProperty("user.dir");
    public static File installDir = new File(homePath, "installs");
    public static File buildDir;

    public static String version;
    private static String graalvmInstall;
    public static boolean shared;
    private static String gc;
    public static String mainClass;
    private static String buildMode;
    private static boolean fabric;
    private static boolean debug;
    public static String outputName;
    public static boolean android;
    public static boolean ios;
    private static boolean experimentalLWJGLPatch;
    private static boolean dryRun;
    public static boolean incremental;
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
        parser.addArgument("--ios")
                .setDefault(false)
                .type(Boolean.TYPE)
                .help("Installs iOS support. Read the wiki for usage");
        parser.addArgument("--debug")
                .setDefault(false)
                .type(Boolean.TYPE)
                .help("Outputs debug information.");
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
        parser.addArgument("--android")
                .setDefault(false)
                .type(Boolean.TYPE)
                .help("Builds a SO library for Android, see Wiki");
        parser.addArgument("--main-class")
                .setDefault("net.minecraft.client.main.Main")
                .help("Specify native image main class");
        parser.addArgument("--dry-run")
                .type(Boolean.TYPE)
                .setDefault(false)
                .help("Don't run Minecraft before native-imaging. Helpful if you already have reflection configs.");
        parser.addArgument("--incremental")
                .type(Boolean.TYPE)
                .setDefault(false)
                .help("Native image the base libs and then using the libraries the application binary.");
        parser.addArgument("--out")
                .type(String.class)
                .help("Native image output name");

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
        android = ns.getBoolean("android");
        ios = ns.getBoolean("ios");
        extraBuildArgs = ns.getString("extra_build_args").split("\\?");
        mainClass = ns.getString("main_class");
        shared = ns.getBoolean("shared");
        dryRun = ns.getBoolean("dry_run");
        buildMode = ns.getString("O");
        incremental = ns.getBoolean("incremental");
        debug = ns.getBoolean("debug");

        if(ns.getString("out") == null)
            outputName = version;
        else
            outputName = ns.getString("out");

        LOGGER.info("Initialized MCNativeBuilder...");
        if(Main.fabric) {
            Main.processors.add(new FabricProcessor());
        }
        if(Main.android) {
            Main.processors.add(new AndroidProcessor());
        }
        if(Main.ios) {
            Main.processors.add(new iOSProcessor());
        }

        if(processors != null) {
            for (String processorPath : processors) {
                try {
                    Main.processors.add((IProcessor) Class.forName(processorPath).getDeclaredConstructor().newInstance());
                    LOGGER.debug("Added processor {}", processorPath);
                } catch (ClassNotFoundException | InvocationTargetException | InstantiationException |
                         IllegalAccessException | NoSuchMethodException e) {
                    LOGGER.warn("Could not find the processor with name {}", processorPath);
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
        LOGGER.debug("Finished adding processors...");

        try {
            Instance install;
            DownloadTracker tracker = new DownloadTracker();
            if (fabric) {
                mcVersion = LoaderType.FABRIC.getMetadata().getMinecraftVersion(Main.version, null);
                JudgeLibVersion judgeLibVersion = JudgeLibAPI.installVersion(mcVersion, Path.of(System.getProperty("user.dir"), "assets"), Path.of(System.getProperty("user.dir"), "libraries"), tracker);
                install = JudgeLibAPI.createInstance(outputName, InstanceFormat.HOTSPOT_JVM, judgeLibVersion, installDir + "/" + outputName, System.getProperty("user.dir") + "/assets", graalvmInstall + "/bin/" + "java", null);
                //install.addMod(Path.of(System.getProperty("user.dir"), "install"), "fabric-api", List.of(LoaderType.FABRIC), "fabric", Source.MODRINTH);
            } else {
                mcVersion = LoaderType.VANILLA.getMetadata().getMinecraftVersion(Main.version, null);
                JudgeLibVersion judgeLibVersion = JudgeLibAPI.installVersion(mcVersion, Path.of(System.getProperty("user.dir"), "assets"), Path.of(System.getProperty("user.dir"), "libraries"), tracker);
                install = JudgeLibAPI.createInstance(outputName, InstanceFormat.HOTSPOT_JVM, judgeLibVersion, installDir + "/" + outputName, System.getProperty("user.dir") + "/assets", graalvmInstall + "/bin/" + "java", null);
            }
            InstanceManager.saveInstance(install);
            buildDir = new File(install.gameDir());

            LOGGER.info("Built MC Classpath...");

            List<File> libs = new ArrayList<>(Arrays.stream(install.classpath().split(File.pathSeparator)).map(File::new).toList());
            for (IProcessor processor : processors) {
                List<File> processedLibs = processor.processClasspath(install);
                if (processedLibs != null) {
                    libs.addAll(0, processedLibs);
                }
            }

            List<String> extraArgs = new ArrayList<>();
            LOGGER.info("Injecting MCNativeTools-9.9 into Classpath");
            File mclibFile = new File(installDir, "MCNativeTools-9.9.jar");
            Util.extractResource("MCNativeTools-9.9.jar", mclibFile);
            libs.addFirst(mclibFile);

            File accountDir = new File(installDir, "account");

            if(!dryRun) {
                LOGGER.info("Logging in...");
                OkHttpClient client = new OkHttpClient.Builder().build();

                Authenticator auth = new Authenticator(
                        PublicClientApplication.builder("d17a73a2-707c-40f5-8c90-d3eda0956f10")
                                .authority("https://login.microsoftonline.com/consumers/")
                                .setTokenCacheAccessAspect(new TokenCache(installDir.getAbsolutePath()))
                                .build(),
                        client
                );
                JudgeLibAccount account;
                JudgeLibAccount[] accounts = JudgeLibAPI.getAccounts(accountDir.getAbsolutePath());
                if(accounts.length > 0) {
                    account = accounts[0];
                    if(account.expired()) {
                        LoginFlow flow = new LoginFlow.SilentAuth(account.microsoftEmail());
                        account = getJudgeLibAccount(client, auth, flow);
                    }
                    account.saveAccount(accountDir.getAbsolutePath());
                } else {
                    LoginFlow flow = new LoginFlow.DeviceCodeAuth(Main::printResult);
                    account = getJudgeLibAccount(client, auth, flow);
                    account.saveAccount(accountDir.getAbsolutePath());
                }

                LOGGER.info("Logged in...");
                LOGGER.info("Launching MC with tracing agent, please follow the instructions in the README to prevent runtime crashes");
                JudgeLibAPI.launchInstance(install, account, Platform.JRE, new String[0], new String[]{"-agentlib:native-image-agent=config-merge-dir=" + buildDir}, System.getenv("LD_LIBRARY_PATH"), "libGL.so");
            }

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

            if(fabric || incremental) {
                libs.remove(new File(install.mainJar()));
            }

            if(fabric) {
                mainClass = "me.judge.fabric.FabricMain";
            }

            extraArgs.addAll(List.of(extraBuildArgs));

            List<String> baseArgs = new ArrayList<>(extraArgs);
            for (IProcessor processor : processors) {
                List<String> tempExtraArgs = processor.preBuild(install, libs, incremental ? LayerState.LIB_LAYER : LayerState.NOT_LAYERED);
                if (tempExtraArgs != null) {
                    baseArgs.addAll(tempExtraArgs);
                }
            }

            String layerCreateArg = "-H:LayerCreate=libmcnative-base.nil,module=java.scripting,module=java.base,module=jdk.httpserver,module=java.management,module=jdk.jfr,module=java.desktop,module=java.sql,module=java.net.http";
            String sharedArg = "--shared";
            if(incremental)
                baseArgs.add(layerCreateArg);
            else if(shared) {
                baseArgs.add(sharedArg);
                baseArgs.add("-H:+InstallExitHandlers");
            }

            if(!incremental)
                baseArgs.add(mainClass);

            LOGGER.info(incremental ? "Building base-layer Native Image..." : "Building Native Image...");
            //runCompileBlocking(install, libs, incremental ? "libmcnative-base" : outputName, baseArgs, BuildType.NORMAL_BUILD);
            LOGGER.info("Built to path {}", buildDir.getAbsolutePath());
            if(incremental) {
                List<String> extensionExtraArgs = new ArrayList<>(extraArgs);
                for (IProcessor processor : processors) {
                    List<String> tempExtraArgs = processor.preBuild(install, libs, LayerState.APP_LAYER);
                    if (tempExtraArgs != null) {
                        extensionExtraArgs.addAll(tempExtraArgs);
                    }
                }

                extensionExtraArgs.add("-H:LayerUse=libmcnative-base.nil");
                if(shared)
                    extensionExtraArgs.add(sharedArg);
                extensionExtraArgs.add(mainClass);

                LOGGER.info("Building app-layer Native Image...");
                runCompileBlocking(install, libs, outputName, extensionExtraArgs, BuildType.NORMAL_BUILD);
            }
        } catch (ReflectiveOperationException | IOException | ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private static JudgeLibAccount getJudgeLibAccount(OkHttpClient client, Authenticator auth, LoginFlow flow) throws InterruptedException, ExecutionException {
        CompletableFuture<AuthSession> accountFuture = auth.authenticate(flow, null);
        AuthSession session = accountFuture.join();
        MinecraftAPI api = new MinecraftAPI(client);
        MinecraftProfile profile = api.queryPlayerProfile(session).get();
        return new JudgeLibAccount(profile.name(), profile.uuid().toString(), session.minecraftToken(), Long.toString(System.currentTimeMillis() + session.expiresIn()), session.microsoftUsername());
    }

    public static void runCompileBlocking(Instance install, List<File> libs, String out, List<String> extraArgs, BuildType buildType) {
        try {
            Process process = startCompile(libs, buildDir, out, buildMode, gc, extraArgs.toArray(new String[0]));
            process.waitFor();
        } catch (InterruptedException | IOException e) {
            LOGGER.error("Error while compiling! " + e.getMessage());
        }

        if(buildType == BuildType.NORMAL_BUILD)
            for (IProcessor processor : processors) processor.postBuild(install, incremental ? LayerState.LIB_LAYER : LayerState.NOT_LAYERED);
    }

    public static Process startCompile(List<File> classPath, File buildDir, String out, String buildMode, String gc, String... extraArgs) throws IOException {
        ProcessBuilder builder = new ProcessBuilder();

        File argFile = new File(buildDir, "command.txt");
        if(!argFile.getParentFile().exists()) {
            argFile.getParentFile().mkdirs();
        }
        if(argFile.exists()) {
            argFile.delete();
            argFile.createNewFile();
        }

        File tmpDir = new File(buildDir, "tmp");
        if(!tmpDir.exists()) {
            tmpDir.mkdirs();
        }

        FileWriter writer = new FileWriter(argFile);

        for(String arg : List.of("-cp",
                classPath.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)), "--gc=" + gc,
                "--enable-url-protocols=https,http", "--add-modules=jdk.jfr", "-H:ExcludeResources=.*.class",
                "-H:+AddAllCharsets", "-H:+IncludeAllLocales", "-H:IncludeResources=resourcepacks/.*", "-H:IncludeResources=.*.so",
                "-H:IncludeResources=.*.dylib", "-H:IncludeResources=.*.dll", "-H:IncludeResources=.*.jnilib",
                "-H:IncludeResources=data/.*", "-H:IncludeResources=assets/.*", "-H:+AddAllCharsets", "-H:+IncludeAllLocales",
                "--initialize-at-run-time=io.netty,org.slf4j,sun.net.dns.ResolverConfigurationImpl", "-H:TempDirectory=tmp")) {
            writer.write(" ");
            writer.write(arg);
        }

        writer.write(" ");
        writer.write("-H:ConfigurationFileDirectories=" + buildDir);

        if(debug) {
            writer.write(" ");
            writer.write("-g");
        }

        writer.write(" ");
        writer.write("-march=compatibility");

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

        builder.command(graalvmInstall + "/bin/native-image" + OS_EXT_SHELL, "@" + argFile.getName(), "-o", out);
        builder.directory(buildDir);
        builder.inheritIO();
        if(!buildDir.exists()) {
            buildDir.mkdirs();
        }
        return builder.start();
    }
}
