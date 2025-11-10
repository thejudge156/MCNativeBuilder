package me.judge.mcnativebuilder.processors;

import me.judge.mcnativebuilder.Main;
import org.angelauramc.judgelib.installer.JudgeLibInstall;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FabricProcessor implements IProcessor{
    @Override
    public List<File> processClasspath(JudgeLibInstall settings) {
        return List.of();
    }

    @Override
    public List<String> preBuild(JudgeLibInstall settings) {
        ArrayList<String> list = new ArrayList<>();
        list.add("--initialize-at-run-time=net.fabricmc.fabric");
        list.add("--features=me.judge.fabric.FabricFeature");
        for(String clazz : Main.fabricRunTimeClasses)
            list.add("--initialize-at-run-time=" + clazz);
        for(String clazz : Main.fabricBuildTimeClasses)
            list.add("--initialize-at-build-time=" + clazz);
        return list;
    }

    @Override
    public void postBuild(JudgeLibInstall settings) {

    }
}
