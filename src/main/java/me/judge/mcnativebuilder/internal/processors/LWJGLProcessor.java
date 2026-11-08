package me.judge.mcnativebuilder.internal.processors;

import me.judge.mcnativebuilder.IProcessor;
import me.judge.mcnativebuilder.LayerState;
import org.angelauramc.judgelib.instance.Instance;
import org.angelauramc.judgelib.util.ConnectionUtil;
import org.angelauramc.judgelib.util.SemVer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public class LWJGLProcessor implements IProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(LWJGLProcessor.class.getName());
    private static final String LWJGL_DOWNLOAD = "https://build.lwjgl.org/release/3.3.3/bin/";
    private boolean isFFMCapable;

    @Override
    public List<File> processClasspath(Instance install) {
        try {
            for (String file : install.classpath().split(File.pathSeparator)) {
                if (file.contains("lwjgl")) {
                    String pattern = Pattern.quote(File.separator);
                    String[] paths = file.split(pattern);
                    String newLWJGL = createLWJGL(paths[paths.length - 1]);

                    if(newLWJGL.equals(paths[paths.length - 1]))
                        continue;

                    LOGGER.debug("Replacing {} with 3.3.3 Version.\n", file);
                    ConnectionUtil.downloadFile(URI.create(LWJGL_DOWNLOAD + newLWJGL), Path.of(file));
                }
            }
        } catch (IOException e) {
            LOGGER.error("LWJGL Processing failed!", e);
        }

        return List.of();
    }

    @Override
    public List<String> preBuild(Instance settings, List<File> classPath, LayerState appLayer) {
        // Need to generate the method handles at build-time if LWJGL is ffm capable
        if(isFFMCapable && appLayer != LayerState.APP_LAYER)
            return List.of("--features=me.judge.lwjgl.LWJGL34Feature");

        return List.of();
    }

    @Override
    public void postBuild(Instance settings, LayerState appLayer) {
        // noop
    }

    private String createLWJGL(String fileName) {
        String[] nameParts = fileName.split("-");
        String baseName = nameParts[0];

        boolean matchesLWJGLPattern = fileName.matches("lwjgl-3\\.([0-9])\\.([0-9]).*");
        boolean isNatives = fileName.contains("natives");
        SemVer desired = new SemVer("3.3.3");
        SemVer ffmCapable = new SemVer("3.4.0");

        if (matchesLWJGLPattern) {
            SemVer ver = new SemVer(nameParts[1]);
            if(ver.greaterThan(desired)) {
                if(ver.greaterThan(ffmCapable)) {
                    isFFMCapable = true;
                }
                return fileName;
            }

            if (isNatives) {
                return formatLWJGLName(baseName, true, nameParts[2], nameParts[3]);
            } else {
                return baseName + "/" + baseName + ".jar";
            }
        } else {
            SemVer ver = new SemVer(nameParts[2]);
            if(ver.greaterThan(desired)) {
                return fileName;
            }

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
