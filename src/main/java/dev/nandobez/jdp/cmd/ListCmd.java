package dev.nandobez.jdp.cmd;

import dev.nandobez.jdp.core.PomReader;
import picocli.CommandLine.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(name = "list", mixinStandardHelpOptions = true, aliases = {"ls"}, description = "Show declared dependencies as a table.")
public class ListCmd implements Callable<Integer> {

    @Option(names = {"-p", "--pom"}, description = "Path to pom.xml.", defaultValue = "pom.xml")
    Path pom;

    @Option(names = "--full", description = "Show full groupId / artifactId instead of short names.")
    boolean full;

    public Integer call() throws Exception {
        var p = PomReader.read(pom);
        if (p.deps().isEmpty()) {
            System.out.println(DIM + "no <dependency> entries in " + pom + R);
            return 0;
        }
        var rows = new ArrayList<String[]>();
        int wPkg = 6, wVer = 6, wGrp = 5;
        for (var c : p.deps()) {
            String pkg = full ? c.artifactId() : c.shortName();
            String grp = full ? c.groupId()  : c.shortGroup();
            String ver = c.version() == null ? DIM + "(BOM)" + R : c.version();
            rows.add(new String[]{ pkg, ver, grp });
            wPkg = Math.max(wPkg, pkg.length());
            wVer = Math.max(wVer, visibleLen(ver));
            wGrp = Math.max(wGrp, grp.length());
        }
        table(new String[]{"pacote", "versão", "grupo"}, new int[]{wPkg, wVer, wGrp}, rows);
        System.out.println(DIM + p.deps().size() + " dependencies · " + pom + R);
        return 0;
    }
}
