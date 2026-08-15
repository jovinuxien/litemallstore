package org.linlinjava.litemall.goods.infrastructure.configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 26: guards the logging config that OOM-killed this service in production.
 *
 * <p>{@code /app/logs} is a tmpfs and its pages are charged to the container's memory cgroup, so
 * the log file competes with the JVM for the same 1.5 GiB. With DEBUG on
 * {@code org.linlinjava.litemall} and a rolling policy that only rotated at midnight and capped
 * nothing, 611 MB was written in under two hours and the container was killed mid-enrichment.
 *
 * <p>Nothing else fails when this regresses — logging "works", the file just grows until the JVM
 * dies hours later, which reads as an unrelated crash.
 */
public class LogbackConfigTest {

    private static final String RESOURCE = "logback-spring.xml";

    private String raw() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "logback-spring.xml missing from the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Document dom() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(raw().getBytes(StandardCharsets.UTF_8)));
    }

    private List<Element> elements(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        List<Element> out = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            out.add((Element) nodes.item(i));
        }
        return out;
    }

    private String childText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    /** Every file appender must roll on SIZE and carry a total cap — not just a daily rotation. */
    @Test
    public void everyFileAppenderIsSizeCapped() throws Exception {
        List<Element> fileAppenders = new ArrayList<>();
        for (Element a : elements(dom(), "appender")) {
            if ("ch.qos.logback.core.rolling.RollingFileAppender".equals(a.getAttribute("class"))) {
                fileAppenders.add(a);
            }
        }
        assertEquals(2, fileAppenders.size(), "expected the log + error file appenders");

        for (Element appender : fileAppenders) {
            String name = appender.getAttribute("name");
            NodeList policies = appender.getElementsByTagName("rollingPolicy");
            assertEquals(1, policies.getLength(), name + ": one rolling policy expected");
            assertEquals("ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy",
                    ((Element) policies.item(0)).getAttribute("class"),
                    name + ": a time-only policy lets one day grow without bound — that is the OOM");

            assertNotNull(childText(appender, "maxFileSize"), name + ": no maxFileSize");
            assertNotNull(childText(appender, "totalSizeCap"), name + ": no totalSizeCap");
            assertNotNull(childText(appender, "maxHistory"), name + ": no maxHistory");
            assertTrue(childText(appender, "fileNamePattern").contains("%i"),
                    name + ": SizeAndTimeBasedRollingPolicy requires %i in fileNamePattern");
        }
    }

    /** Production must not log the per-request / per-SQL DEBUG lines that filled the tmpfs. */
    @Test
    public void productionLogsAtInfoAndOtherProfilesKeepDebug() throws Exception {
        Element prod = null;
        Element nonProd = null;
        for (Element p : elements(dom(), "springProfile")) {
            if ("prod".equals(p.getAttribute("name"))) {
                prod = p;
            } else if ("!prod".equals(p.getAttribute("name"))) {
                nonProd = p;
            }
        }
        assertNotNull(prod, "no <springProfile name=\"prod\"> block");
        assertNotNull(nonProd, "no <springProfile name=\"!prod\"> block — dev would lose DEBUG");

        NodeList prodLoggers = prod.getElementsByTagName("logger");
        assertTrue(prodLoggers.getLength() > 0, "prod profile sets no logger levels");
        for (int i = 0; i < prodLoggers.getLength(); i++) {
            Element logger = (Element) prodLoggers.item(i);
            assertEquals("INFO", logger.getAttribute("level"),
                    "prod logger " + logger.getAttribute("name") + " must not be DEBUG");
        }
    }

    /** logback's own status printer wrote hundreds of lines into the file this config bounds. */
    @Test
    public void logbackInternalDebugPrintingIsOff() throws Exception {
        assertTrue(raw().contains("<configuration debug=\"false\">"),
                "configuration debug must be false");
    }

    /**
     * Proves the file is VALID LOGBACK, not merely well-formed XML — a typo in an appender or
     * policy would otherwise surface only when the service boots. The springProfile elements are
     * a Spring Boot extension that plain Joran cannot resolve, so they are stripped first; what
     * remains is exactly the appender/rolling/filter configuration under test.
     */
    @Test
    public void theConfigurationIsValidLogback() throws Exception {
        String stripped = raw().replaceAll("(?s)<springProfile.*?</springProfile>", "");
        LoggerContext context = new LoggerContext();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(new ByteArrayInputStream(stripped.getBytes(StandardCharsets.UTF_8)));

        List<String> errors = new ArrayList<>();
        for (Status status : context.getStatusManager().getCopyOfStatusList()) {
            if (status.getLevel() == Status.ERROR) {
                errors.add(status.getMessage());
            }
        }
        assertTrue(errors.isEmpty(), "logback rejected the configuration: " + errors);
        context.stop();
    }

    /**
     * The XML is only half the story: Spring's logging.level.* OVERRIDES logback-spring.xml, and
     * Spring resolves the MOST SPECIFIC logger key. application.yml sets
     * org.linlinjava.litemall.db: DEBUG — every SQL statement, ~14k lines a minute — which no
     * package-level override can hold down. It has to be countered at the same specificity, in
     * the prod profile. That single line is what OOM-killed this service.
     */
    @Test
    public void prodSilencesTheSqlDebugFirehose() throws Exception {
        String prodYml;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config/application-prod.yml")) {
            assertNotNull(in, "application-prod.yml missing from the classpath");
            prodYml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(prodYml.matches("(?s).*org\\.linlinjava\\.litemall\\.db:\\s*(INFO|WARN|ERROR).*"),
                "application-prod.yml must pin org.linlinjava.litemall.db above DEBUG — "
                        + "application.yml sets it to DEBUG and the more specific key wins");
    }
}
