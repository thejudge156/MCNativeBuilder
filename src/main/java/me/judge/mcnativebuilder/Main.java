package me.judge.mcnativebuilder;

import me.judge.mcnativebuilder.processors.IProcessor;
import me.judge.mcnativebuilder.processors.LWJGLProcessor;
import net.hycrafthd.minecraft_downloader.*;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Main {
    static {
        if(System.getProperty("os.name").contains("Windows")) {
            OS_EXT = ".exe";
            OS_EXT_SHELL = ".cmd";
            OS_SEPARATOR = ";";
        } else {
            OS_EXT = "";
            OS_EXT_SHELL = "";
            OS_SEPARATOR = ":";
        }
    }
    public static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static String OS_EXT;
    public static String OS_EXT_SHELL;
    public static String OS_SEPARATOR;

    public static String homePath = System.getProperty("user.dir");
    public static File installDir = new File(homePath, "install");
    public static File buildDir = new File(installDir, "native-build");
    public static File runDir = new File(installDir, "run");
    public static File accFile = new File(runDir, "acc.json");
    private static Main instance;

    private final String version;
    private final String graalvmInstall;
    private final String gc;

    private List<IProcessor> processors = new ArrayList<>();

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("MCNativeBuilder").build()
                .defaultHelp(true)
                .description("Build Minecraft Native Images.");
        parser.addArgument("--version")
                .setDefault("1.20.6")
                .help("Version of Minecraft to download and compile");
        parser.addArgument("--graalvm")
                .help("Where your graalvm sdk is.");
        parser.addArgument("--processors")
                .nargs("*")
                .help("Fully-qualified name of additional processors to use.");
        parser.addArgument("--gc")
                .setDefault("serial")
                .help("Garbage collector to use.");

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        List<String> processors = ns.getList("processors");
        instance = new Main(ns.getString("gc"), ns.getString("version"), ns.getString("graalvm"), processors);
        LOGGER.info("Initialized MCNativeBuilder...");
        instance.start();
    }

    public Main(String gc, String version, String graalvmInstall, List<String> processors) {
        this.gc = gc;
        this.version = version;
        this.graalvmInstall = graalvmInstall;

        if(processors != null) {
            for (String processorPath : processors) {
                try {
                    this.processors.add((IProcessor) Class.forName(processorPath).getDeclaredConstructor().newInstance());
                    LOGGER.fine("Added processor " + processorPath);
                } catch (ClassNotFoundException | InvocationTargetException | InstantiationException |
                         IllegalAccessException | NoSuchMethodException e) {
                    LOGGER.severe("Could not find the processor with name " + processorPath);
                }
            }
        }
    }

    public void start() {
        processors.add(new LWJGLProcessor());
        LOGGER.fine("Finished adding processors...");

        ProvidedSettings mcSettings = new ProvidedSettings(version, installDir, runDir);
        MinecraftParser.launch(mcSettings);
        MinecraftDownloader.launch(mcSettings, false, null, true, false);
        LOGGER.info("Downloaded MC...");
        MinecraftClasspathBuilder.launch(mcSettings, false);
        LOGGER.info("Built MC Classpath...");

        List<File> libs = new ArrayList<>();
        for(IProcessor processor : processors) {
            List<File> processedLibs = processor.processClasspath(mcSettings);
            if(processedLibs != null) {
                libs.addAll(processedLibs);
            }
        }

        LOGGER.info("Injecting MC-Lib into Classpath");
        File mclibFile = new File(installDir, "mc-lib.jar");
        if(!mclibFile.exists()) {
            try(InputStream stream = Main.class.getResourceAsStream("extraLibs/mc-lib.jar")) {
                if(stream != null) {
                    FileOutputStream fos = new FileOutputStream(mclibFile);
                    while (stream.available() > 0) {
                        byte[] data = stream.readNBytes(1024);
                        fos.write(data);
                        fos.flush();
                    }
                    fos.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        libs.add(mclibFile);
        libs.add(mcSettings.getClientJarFile());

        LOGGER.info("Logging in...");
        MinecraftAuthenticator.launch(mcSettings, accFile, "web", false);
        LOGGER.info("Logged in...");
        LOGGER.info("Launching MC with tracing agent, please follow the instructions in the README to prevent runtime crashes");
        MinecraftJavaRuntimeSetup.launch(mcSettings, false, new File(graalvmInstall, "bin/java"));
        MinecraftLauncher.launch(mcSettings, "-agentlib:native-image-agent=config-merge-dir=" + installDir + "/configs/" + version);

        List<String> extraArgs = new ArrayList<>();
        for(IProcessor processor : processors) {
            List<String> tempExtraArgs = processor.preBuild(mcSettings);
            if(tempExtraArgs != null) {
                extraArgs.addAll(tempExtraArgs);
            }
        }

        LOGGER.info("Building Native Image...");
        try {
            Process process = startCompile(libs, extraArgs.toArray(new String[0]));
            BufferedReader errors = process.errorReader();
            BufferedReader info = process.inputReader();
            while(process.isAlive()) {
                if(info.ready()) {
                    LOGGER.info(info.readLine());
                }
                if(errors.ready()) {
                    LOGGER.severe(errors.readLine());
                }
            }
        } catch (IOException e) {
            LOGGER.severe("Error while compiling! " + e.getMessage());
        }
        LOGGER.info("Built to path " + buildDir.getAbsolutePath());
    }

    private Process startCompile(List<File> classPath, String... extraArgs) throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(graalvmInstall + "/bin/native-image" + OS_EXT_SHELL, "-H:ConfigurationFileDirectories=" + installDir + "/configs/" + version, "-cp",
                classPath.stream().map(File::getAbsolutePath).collect(Collectors.joining(OS_SEPARATOR)), "--gc=" + gc, "net.minecraft.client.main.Main", version);
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
