package me.judge.mcnativebuilder;

import org.angelauramc.judgelib.instance.Instance;

import java.io.File;
import java.util.List;

public interface IProcessor {
    List<File> processClasspath(Instance install);
    List<String> preBuild(Instance install, List<File> classpath, LayerState appLayer);
    void postBuild(Instance install, LayerState appLayer);
}
