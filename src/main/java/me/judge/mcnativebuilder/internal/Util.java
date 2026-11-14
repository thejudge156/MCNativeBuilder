package me.judge.mcnativebuilder.internal;

import me.judge.mcnativebuilder.Main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class Util {
    public static boolean extractResource(String name, String outPath) {
        return extractResource(name, new File(outPath));
    }

    public static boolean extractResource(String name, File outPath) {
        try (InputStream stream = Main.class.getClassLoader().getResourceAsStream("extraLibs/" + name)) {
            if (stream != null) {
                FileOutputStream fos = new FileOutputStream(outPath);
                byte[] buffer = stream.readAllBytes();
                fos.write(buffer);
                fos.flush();
                fos.close();
                return true;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return false;
    }
}
