package me.judge.mcnativebuilder.processors;

import me.judge.mcnativebuilder.Main;
import org.angelauramc.judgelib.installer.JudgeLibInstall;
import org.angelauramc.judgelib.util.ConnectionUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class LWJGLProcessor implements IProcessor {
    private static final String LWJGL_DOWNLOAD = "https://build.lwjgl.org/release/3.3.3/bin/";

    @Override
    public List<File> processClasspath(JudgeLibInstall install) {
        List<File> files = new ArrayList<>();
        try {
            for (String file : install.classpath.split(File.pathSeparator)) {
                if (file.contains("lwjgl")) {
                    String pattern = Pattern.quote(File.separator);
                    String[] paths = file.split(pattern);
                    String newLWJGL = createLWJGL(paths[paths.length - 1]);
                    Main.LOGGER.fine("Replacing " + file + " with 3.3.3 Version.\n");
                    ConnectionUtil.downloadFile(URI.create(LWJGL_DOWNLOAD + newLWJGL), Path.of(file));
                }

                files.add(new File(file));
            }
        } catch (IOException e) {
            Main.LOGGER.severe("LWJGL Processing failed! " + e.getMessage());
        }

        return files;
    }

    @Override
    public List<String> preBuild(JudgeLibInstall settings) {
        // noop
        return List.of();
    }

    @Override
    public void postBuild(JudgeLibInstall settings) {
        // noop
    }

    private String createLWJGL(String fileName) {
        String[] nameParts = fileName.split("-");
        String baseName = nameParts[0];

        boolean matchesLWJGLPattern = fileName.matches("lwjgl-3\\.([0-9])\\.([0-9]).*");
        boolean isNatives = fileName.contains("natives");

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

    private String formatLWJGLName(String baseName, boolean isSingleNamespace, String... parts) {
        StringBuilder sb = new StringBuilder(baseName);
        if(!isSingleNamespace) {
            sb.append("-").append(parts[0]).append("/");
        } else {
            sb.append("/");
        }
        sb.append(baseName).append("-").append(parts[0]).append("-");
        for (int i = 1; i < parts.length; i++) {
            sb.append(parts[i].replace(".jar", ""));
            if(parts[i].contains(".jar") && System.getProperty("os.arch").equals("aarch64")) {
                sb.append("-arm64");
            }
            if (i < parts.length - 1) {
                sb.append("-");
            }
        }
        if (!sb.toString().endsWith(".jar")) {
            sb.append(".jar");
        }
        return sb.toString();
    }
}
