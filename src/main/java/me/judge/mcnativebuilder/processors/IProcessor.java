package me.judge.mcnativebuilder.processors;

import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;

import java.io.File;
import java.util.List;

public interface IProcessor {
    List<File> processClasspath(ProvidedSettings settings);
    List<String> preBuild(ProvidedSettings settings);
    void postBuild(ProvidedSettings settings);
}
