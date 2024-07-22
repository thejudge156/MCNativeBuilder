package me.judge.mcnativebuilder.processors;

import me.judge.mcnativebuilder.Main;
import net.hycrafthd.minecraft_downloader.library.DownloadableFile;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;
import net.hycrafthd.minecraft_downloader.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LWJGLProcessor implements IProcessor {
    private static final String LWJGL_DOWNLOAD = "https://build.lwjgl.org/release/3.3.3/bin/";

    @Override
    public List<File> processClasspath(ProvidedSettings settings) {
        List<File> files = new ArrayList<>();
        try {
            for (DownloadableFile file : settings.getGeneratedSettings().getDownloadableFiles()) {
                if (file.isNative()) {
                    continue;
                }
                if (file.getDownloadedFile().getName().contains("lwjgl")) {
                    String newLWJGL = createLWJGL(file);
                    Main.LOGGER.fine("Replacing %s " + file.getDownloadedFile().getName() + " with 3.3.3 Version.\n");
                    FileUtil.downloadFile(LWJGL_DOWNLOAD + newLWJGL, file.getDownloadedFile(), null);
                }

                files.add(file.getDownloadedFile());
            }
        } catch (IOException e) {
            Main.LOGGER.severe("LWJGL Processing failed! " + e.getMessage());
        }

        return files;
    }

    @Override
    public List<String> preBuild(ProvidedSettings settings) {
        // noop
        return List.of();
    }

    @Override
    public void postBuild(ProvidedSettings settings) {
        // noop
    }

    private String createLWJGL(DownloadableFile file) {
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

    private String formatLWJGLName(String baseName, boolean isSingleNamespace, String... parts) {
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
