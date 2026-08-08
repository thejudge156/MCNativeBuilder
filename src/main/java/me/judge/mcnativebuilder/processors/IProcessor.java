package me.judge.mcnativebuilder.processors;

import org.angelauramc.judgelib.installer.JudgeLibInstall;

import java.io.File;
import java.util.List;

public interface IProcessor {
    List<File> processClasspath(JudgeLibInstall install);
    List<String> preBuild(JudgeLibInstall install, List<File> classpath, boolean appLayer);
    void postBuild(JudgeLibInstall install, boolean appLayer);
}
