package me.judge.mcnativebuilder.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;

public class ClasspathUtil {
    public static File shortenClasspath(String files, String rootDir, String sep) throws IOException {
        File newClasspathFile = new File(rootDir, "Classpath.jar");
        if(!newClasspathFile.exists()) {
            newClasspathFile.createNewFile();
        }

        String filePaths = "file:/" + files.replaceAll(sep, " file:/").replaceAll(Matcher.quoteReplacement("\\"), "/");

        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, filePaths);

        JarOutputStream output = new JarOutputStream(new FileOutputStream(newClasspathFile), manifest);

        ZipEntry manifestFile = new ZipEntry("META-INF/");
        output.putNextEntry(manifestFile);

        output.flush();
        output.closeEntry();

        output.close();

        return newClasspathFile;
    }
}
