package dev.nandobez.jdp.core;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.io.File;
import java.nio.file.Path;
import java.util.*;

/** Minimal pom.xml reader: extracts <dependencies> + properties for placeholder resolution. */
public class PomReader {
    public record Pom(Path file, Document doc, List<Coord> deps, Map<String,String> props, String parentVersion) {}

    public static Pom read(Path pomPath) throws Exception {
        var f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        var doc = f.newDocumentBuilder().parse(pomPath.toFile());
        doc.getDocumentElement().normalize();

        Map<String,String> props = new LinkedHashMap<>();
        NodeList propsList = ((Element) doc.getDocumentElement()).getElementsByTagName("properties");
        if (propsList.getLength() > 0) {
            NodeList children = propsList.item(0).getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node n = children.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE) props.put(n.getNodeName(), n.getTextContent().trim());
            }
        }

        String parentVersion = null;
        NodeList parent = doc.getElementsByTagName("parent");
        if (parent.getLength() > 0) {
            parentVersion = childText((Element) parent.item(0), "version");
        }

        List<Coord> deps = new ArrayList<>();
        NodeList depsRoot = doc.getElementsByTagName("dependencies");
        if (depsRoot.getLength() > 0) {
            // Only the top-level <dependencies>, not <dependencyManagement>/<dependencies>
            Node managed = null;
            NodeList dm = doc.getElementsByTagName("dependencyManagement");
            if (dm.getLength() > 0) {
                NodeList inner = ((Element) dm.item(0)).getElementsByTagName("dependencies");
                if (inner.getLength() > 0) managed = inner.item(0);
            }
            for (int i = 0; i < depsRoot.getLength(); i++) {
                Node d = depsRoot.item(i);
                if (d == managed) continue;
                NodeList dep = ((Element) d).getElementsByTagName("dependency");
                for (int j = 0; j < dep.getLength(); j++) {
                    Element e = (Element) dep.item(j);
                    String g = resolve(childText(e, "groupId"), props);
                    String a = resolve(childText(e, "artifactId"), props);
                    String v = resolve(childText(e, "version"), props);
                    deps.add(new Coord(g, a, v));
                }
            }
        }
        return new Pom(pomPath, doc, deps, props, parentVersion);
    }

    private static String childText(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i).getParentNode() == parent) return nl.item(i).getTextContent().trim();
        }
        return null;
    }

    private static String resolve(String v, Map<String,String> props) {
        if (v == null || !v.startsWith("${") || !v.endsWith("}")) return v;
        String key = v.substring(2, v.length() - 1);
        return props.getOrDefault(key, v);
    }
}
