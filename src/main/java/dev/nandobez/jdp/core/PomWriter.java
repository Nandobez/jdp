package dev.nandobez.jdp.core;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.file.*;

/** Adds/removes <dependency> entries from a pom.xml while preserving the rest. */
public class PomWriter {

    public static void add(Path pomPath, Coord c) throws Exception {
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomPath.toFile());
        Element root = doc.getDocumentElement();
        Element deps = firstTopLevel(root, "dependencies");
        if (deps == null) {
            deps = doc.createElement("dependencies");
            root.appendChild(deps);
        }
        // already there?
        NodeList existing = deps.getElementsByTagName("dependency");
        for (int i = 0; i < existing.getLength(); i++) {
            Element e = (Element) existing.item(i);
            if (c.groupId().equals(text(e, "groupId")) && c.artifactId().equals(text(e, "artifactId"))) {
                if (c.version() != null) setOrCreate(doc, e, "version", c.version());
                write(doc, pomPath);
                return;
            }
        }
        Element dep = doc.createElement("dependency");
        appendChild(doc, dep, "groupId", c.groupId());
        appendChild(doc, dep, "artifactId", c.artifactId());
        if (c.version() != null) appendChild(doc, dep, "version", c.version());
        deps.appendChild(dep);
        write(doc, pomPath);
    }

    public static boolean remove(Path pomPath, String groupOrShort, String artifactOrShort) throws Exception {
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomPath.toFile());
        Element deps = firstTopLevel(doc.getDocumentElement(), "dependencies");
        if (deps == null) return false;
        NodeList all = deps.getElementsByTagName("dependency");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            String g = text(e, "groupId");
            String a = text(e, "artifactId");
            if (matches(g, a, groupOrShort, artifactOrShort)) {
                deps.removeChild(e);
                write(doc, pomPath);
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String g, String a, String wantG, String wantA) {
        if (wantA == null) return false;
        if (wantG != null && !g.contains(wantG)) return false;
        return a.equals(wantA) || a.endsWith("-" + wantA) || a.equals(wantA);
    }

    private static Element firstTopLevel(Element root, String tag) {
        NodeList c = root.getChildNodes();
        for (int i = 0; i < c.getLength(); i++) {
            if (c.item(i) instanceof Element e && e.getTagName().equals(tag)) return e;
        }
        return null;
    }

    private static String text(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i).getParentNode() == parent) return nl.item(i).getTextContent().trim();
        }
        return null;
    }

    private static void appendChild(Document doc, Element parent, String tag, String value) {
        Element el = doc.createElement(tag);
        el.setTextContent(value);
        parent.appendChild(el);
    }

    private static void setOrCreate(Document doc, Element parent, String tag, String value) {
        NodeList nl = parent.getElementsByTagName(tag);
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i).getParentNode() == parent) { nl.item(i).setTextContent(value); return; }
        }
        appendChild(doc, parent, tag, value);
    }

    private static void write(Document doc, Path pomPath) throws Exception {
        Transformer tf = TransformerFactory.newInstance().newTransformer();
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
        tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        var sw = new StringWriter();
        tf.transform(new DOMSource(doc), new StreamResult(sw));
        Files.writeString(pomPath, sw.toString());
    }
}
