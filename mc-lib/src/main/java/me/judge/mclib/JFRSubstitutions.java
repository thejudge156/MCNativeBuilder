//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package me.judge.mclib;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@TargetClass(ModuleLayer.class)
public final class JFRSubstitutions {
    @Alias
    private static ModuleLayer EMPTY_LAYER;
    @Alias
    private Map<String, Module> nameToModule;

    @Substitute
    public Optional<Module> findModule(String name) {
        if (name.equals("jdk.jfr")) {
            return Optional.empty();
        }

        Objects.requireNonNull(name);
        if ((Object) this == EMPTY_LAYER)
            return Optional.empty();
        Module m = nameToModule.get(name);
        if (m != null)
            return Optional.of(m);

        Optional<Module> opt = Optional.empty();
        for(ModuleLayer layer : layers().collect(Collectors.toSet())) {
            opt = layer.findModule(name);
        }

        return opt;
    }

    @Alias
    Stream<ModuleLayer> layers() {
        return null;
    }
}
