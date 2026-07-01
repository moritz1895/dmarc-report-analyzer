package ms.rohde.dmarcanalyzer.core.domain.report;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import ms.rohde.hexagonalarch.annotations.DomainService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Parses a DMARC aggregate report XML document (RFC 7489 / dmarc.org schema)
 * into a {@link DmarcAggregateReport}. Implemented with pure JDK DOM parsing;
 * external entity resolution and DOCTYPE processing are disabled since the
 * XML originates from untrusted external mail senders.
 */
@DomainService
public class DmarcXmlReportParser {

    /**
     * Parses the given XML content into a {@link DmarcAggregateReport}.
     *
     * @throws DmarcReportParseException if the XML is malformed or a
     *                                    required element/attribute is
     *                                    missing or unparseable
     */
    public DmarcAggregateReport parse(String xmlContent) {
        try {
            Document document = newDocumentBuilder().parse(new InputSource(new StringReader(xmlContent)));
            document.getDocumentElement().normalize();
            Element feedback = document.getDocumentElement();

            ReportMetadata metadata = parseReportMetadata(requiredChild(feedback, "report_metadata"));
            PolicyPublished policyPublished = parsePolicyPublished(requiredChild(feedback, "policy_published"));
            List<DmarcReportRecord> records = new ArrayList<>();
            for (Element recordElement : childElements(feedback, "record")) {
                records.add(parseRecord(recordElement));
            }

            return new DmarcAggregateReport(metadata, policyPublished, records);
        } catch (DmarcReportParseException e) {
            throw e;
        } catch (SAXException | IOException | ParserConfigurationException | RuntimeException e) {
            throw new DmarcReportParseException("Failed to parse DMARC aggregate report XML", e);
        }
    }

    private DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private ReportMetadata parseReportMetadata(Element element) {
        String orgName = requiredText(element, "org_name");
        String email = requiredText(element, "email");
        String reportId = requiredText(element, "report_id");
        Element dateRange = requiredChild(element, "date_range");
        Instant begin = parseEpochSeconds(requiredText(dateRange, "begin"));
        Instant end = parseEpochSeconds(requiredText(dateRange, "end"));
        return new ReportMetadata(orgName, email, reportId, begin, end);
    }

    private PolicyPublished parsePolicyPublished(Element element) {
        String domain = requiredText(element, "domain");
        boolean adkimStrict = "s".equalsIgnoreCase(requiredText(element, "adkim"));
        boolean aspfStrict = "s".equalsIgnoreCase(requiredText(element, "aspf"));
        Disposition domainPolicy = Disposition.fromXmlValue(requiredText(element, "p"));
        Disposition subdomainPolicy = Disposition.fromXmlValue(requiredText(element, "sp"));
        int percentageCoverage = optionalText(element, "pct").map(Integer::parseInt).orElse(100);
        return new PolicyPublished(domain, adkimStrict, aspfStrict, domainPolicy, subdomainPolicy, percentageCoverage);
    }

    private DmarcReportRecord parseRecord(Element element) {
        Element row = requiredChild(element, "row");
        String sourceIp = requiredText(row, "source_ip");
        int messageCount = Integer.parseInt(requiredText(row, "count"));
        PolicyEvaluated policyEvaluated = parsePolicyEvaluated(requiredChild(row, "policy_evaluated"));

        String headerFrom = optionalChild(element, "identifiers")
                .flatMap(identifiers -> optionalText(identifiers, "header_from"))
                .orElse(null);

        List<DkimAuthResult> dkimAuthResults = new ArrayList<>();
        List<SpfAuthResult> spfAuthResults = new ArrayList<>();
        var authResults = optionalChild(element, "auth_results");
        if (authResults.isPresent()) {
            for (Element dkim : childElements(authResults.get(), "dkim")) {
                dkimAuthResults.add(new DkimAuthResult(
                        requiredText(dkim, "domain"),
                        DmarcAuthResultValue.fromXmlValue(requiredText(dkim, "result"))));
            }
            for (Element spf : childElements(authResults.get(), "spf")) {
                spfAuthResults.add(new SpfAuthResult(
                        requiredText(spf, "domain"),
                        DmarcAuthResultValue.fromXmlValue(requiredText(spf, "result"))));
            }
        }

        return new DmarcReportRecord(sourceIp, messageCount, policyEvaluated, headerFrom, dkimAuthResults, spfAuthResults);
    }

    private PolicyEvaluated parsePolicyEvaluated(Element element) {
        Disposition disposition = Disposition.fromXmlValue(requiredText(element, "disposition"));
        DmarcAuthResultValue dkimResult = DmarcAuthResultValue.fromXmlValue(requiredText(element, "dkim"));
        DmarcAuthResultValue spfResult = DmarcAuthResultValue.fromXmlValue(requiredText(element, "spf"));
        return new PolicyEvaluated(disposition, dkimResult, spfResult);
    }

    private Instant parseEpochSeconds(String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            throw new DmarcReportParseException("Invalid epoch seconds value: " + value, e);
        }
    }

    private List<Element> childElements(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element childElement && tagName.equals(childElement.getTagName())) {
                result.add(childElement);
            }
        }
        return result;
    }

    private Element requiredChild(Element parent, String tagName) {
        return optionalChild(parent, tagName)
                .orElseThrow(() -> new DmarcReportParseException(
                        "Missing required element <%s> under <%s>".formatted(tagName, parent.getTagName())));
    }

    private Optional<Element> optionalChild(Element parent, String tagName) {
        List<Element> matches = childElements(parent, tagName);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    private String requiredText(Element parent, String tagName) {
        return optionalText(parent, tagName)
                .orElseThrow(() -> new DmarcReportParseException(
                        "Missing required element <%s> under <%s>".formatted(tagName, parent.getTagName())));
    }

    private Optional<String> optionalText(Element parent, String tagName) {
        return optionalChild(parent, tagName).map(Node::getTextContent).map(String::trim);
    }
}
