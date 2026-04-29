package net.rustcore.duel.build;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

class PomDependencyTest {

    @Test
    void jacksonDatabindIsPackagedWithPlugin() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile());

        NodeList dependencies = doc.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if ("com.fasterxml.jackson.core".equals(textOf(dependency, "groupId"))
                    && "jackson-databind".equals(textOf(dependency, "artifactId"))) {
                assertNotEquals("provided", textOf(dependency, "scope"),
                        "jackson-databind must be shaded into the plugin jar; Leaf/Paper does not provide ObjectMapper");
                return;
            }
        }
        fail("jackson-databind dependency is required by RatingClient");
    }

    private static String textOf(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }
}
