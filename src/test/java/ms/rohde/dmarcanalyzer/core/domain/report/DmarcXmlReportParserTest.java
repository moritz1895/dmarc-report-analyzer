package ms.rohde.dmarcanalyzer.core.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DmarcXmlReportParserTest {

    private final DmarcXmlReportParser parser = new DmarcXmlReportParser();

    private static final String VALID_REPORT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feedback>
              <report_metadata>
                <org_name>google.com</org_name>
                <email>noreply-dmarc-support@google.com</email>
                <report_id>1234567890</report_id>
                <date_range>
                  <begin>1735689600</begin>
                  <end>1735776000</end>
                </date_range>
              </report_metadata>
              <policy_published>
                <domain>example.com</domain>
                <adkim>r</adkim>
                <aspf>r</aspf>
                <p>quarantine</p>
                <sp>quarantine</sp>
                <pct>100</pct>
              </policy_published>
              <record>
                <row>
                  <source_ip>203.0.113.5</source_ip>
                  <count>3</count>
                  <policy_evaluated>
                    <disposition>none</disposition>
                    <dkim>fail</dkim>
                    <spf>pass</spf>
                  </policy_evaluated>
                </row>
                <identifiers>
                  <header_from>example.com</header_from>
                </identifiers>
                <auth_results>
                  <dkim>
                    <domain>example.com</domain>
                    <result>fail</result>
                  </dkim>
                  <spf>
                    <domain>example.com</domain>
                    <result>pass</result>
                  </spf>
                </auth_results>
              </record>
              <record>
                <row>
                  <source_ip>198.51.100.9</source_ip>
                  <count>7</count>
                  <policy_evaluated>
                    <disposition>reject</disposition>
                    <dkim>PASS</dkim>
                    <spf>PASS</spf>
                  </policy_evaluated>
                </row>
                <auth_results>
                  <dkim>
                    <domain>example.com</domain>
                    <result>pass</result>
                  </dkim>
                  <dkim>
                    <domain>example.com</domain>
                    <result>fail</result>
                  </dkim>
                  <spf>
                    <domain>example.com</domain>
                    <result>pass</result>
                  </spf>
                </auth_results>
              </record>
            </feedback>
            """;

    @Test
    void parse_givenValidReportXml_thenMetadataIsParsedCorrectly() {
        DmarcAggregateReport report = parser.parse(VALID_REPORT_XML);

        ReportMetadata metadata = report.metadata();
        assertThat(metadata.orgName()).isEqualTo("google.com");
        assertThat(metadata.email()).isEqualTo("noreply-dmarc-support@google.com");
        assertThat(metadata.reportId()).isEqualTo("1234567890");
        assertThat(metadata.dateRangeBegin()).isEqualTo(Instant.ofEpochSecond(1735689600L));
        assertThat(metadata.dateRangeEnd()).isEqualTo(Instant.ofEpochSecond(1735776000L));
    }

    @Test
    void parse_givenValidReportXml_thenPolicyPublishedIsParsedCorrectly() {
        DmarcAggregateReport report = parser.parse(VALID_REPORT_XML);

        PolicyPublished policyPublished = report.policyPublished();
        assertThat(policyPublished.domain()).isEqualTo("example.com");
        assertThat(policyPublished.adkimStrict()).isFalse();
        assertThat(policyPublished.aspfStrict()).isFalse();
        assertThat(policyPublished.domainPolicy()).isEqualTo(Disposition.QUARANTINE);
        assertThat(policyPublished.subdomainPolicy()).isEqualTo(Disposition.QUARANTINE);
        assertThat(policyPublished.percentageCoverage()).isEqualTo(100);
    }

    @Test
    void parse_givenReportWithoutPct_thenPercentageCoverageDefaultsTo100() {
        String xmlWithoutPct = VALID_REPORT_XML.replace("<pct>100</pct>", "");

        DmarcAggregateReport report = parser.parse(xmlWithoutPct);

        assertThat(report.policyPublished().percentageCoverage()).isEqualTo(100);
    }

    @Test
    void parse_givenValidReportXml_thenFirstRecordIsParsedCorrectly() {
        DmarcAggregateReport report = parser.parse(VALID_REPORT_XML);

        DmarcReportRecord firstRecord = report.records().get(0);
        assertThat(firstRecord.sourceIp()).isEqualTo("203.0.113.5");
        assertThat(firstRecord.messageCount()).isEqualTo(3);
        assertThat(firstRecord.headerFrom()).isEqualTo("example.com");
        assertThat(firstRecord.policyEvaluated())
                .isEqualTo(new PolicyEvaluated(Disposition.NONE, DmarcAuthResultValue.FAIL, DmarcAuthResultValue.PASS));
        assertThat(firstRecord.dkimAuthResults())
                .containsExactly(new DkimAuthResult("example.com", DmarcAuthResultValue.FAIL));
        assertThat(firstRecord.spfAuthResults())
                .containsExactly(new SpfAuthResult("example.com", DmarcAuthResultValue.PASS));
        assertThat(firstRecord.isFullyAligned()).isFalse();
    }

    @Test
    void parse_givenRecordWithMultipleDkimSignatures_thenAllAreCollected() {
        DmarcAggregateReport report = parser.parse(VALID_REPORT_XML);

        DmarcReportRecord secondRecord = report.records().get(1);
        assertThat(secondRecord.dkimAuthResults()).hasSize(2);
        assertThat(secondRecord.dkimAuthResults())
                .extracting(DkimAuthResult::result)
                .containsExactly(DmarcAuthResultValue.PASS, DmarcAuthResultValue.FAIL);
    }

    @Test
    void parse_givenRecordWithoutIdentifiers_thenHeaderFromIsNull() {
        DmarcAggregateReport report = parser.parse(VALID_REPORT_XML);

        DmarcReportRecord secondRecord = report.records().get(1);
        assertThat(secondRecord.headerFrom()).isNull();
        assertThat(secondRecord.isFullyAligned()).isTrue();
    }

    @Test
    void parse_givenValidReportXml_thenTotalMessageCountSumsAllRecords() {
        DmarcAggregateReport report = parser.parse(VALID_REPORT_XML);

        assertThat(report.totalMessageCount()).isEqualTo(10);
    }

    @Test
    void parse_givenLowercaseStrictAlignmentValues_thenParsedAsStrict() {
        String xml = VALID_REPORT_XML.replace("<adkim>r</adkim>", "<adkim>s</adkim>")
                .replace("<aspf>r</aspf>", "<aspf>s</aspf>");

        DmarcAggregateReport report = parser.parse(xml);

        assertThat(report.policyPublished().adkimStrict()).isTrue();
        assertThat(report.policyPublished().aspfStrict()).isTrue();
    }

    @Test
    void parse_givenMalformedXml_thenThrowsDmarcReportParseException() {
        String malformedXml = "<feedback><report_metadata><org_name>broken</feedback>";

        assertThatThrownBy(() -> parser.parse(malformedXml))
                .isInstanceOf(DmarcReportParseException.class);
    }

    @Test
    void parse_givenXmlMissingRequiredElement_thenThrowsDmarcReportParseException() {
        String xmlMissingOrgName = VALID_REPORT_XML.replace("<org_name>google.com</org_name>", "");

        assertThatThrownBy(() -> parser.parse(xmlMissingOrgName))
                .isInstanceOf(DmarcReportParseException.class);
    }

    @Test
    void parse_givenUnparseableDateValue_thenThrowsDmarcReportParseException() {
        String xmlWithBadDate = VALID_REPORT_XML.replace("<begin>1735689600</begin>", "<begin>not-a-number</begin>");

        assertThatThrownBy(() -> parser.parse(xmlWithBadDate))
                .isInstanceOf(DmarcReportParseException.class);
    }

    @Test
    void parse_givenXxeAttemptViaDoctype_thenThrowsDmarcReportParseExceptionWithoutLeakingFileContent() {
        String xxeXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE feedback [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <feedback>
                  <report_metadata>
                    <org_name>&xxe;</org_name>
                    <email>a@b.com</email>
                    <report_id>1</report_id>
                    <date_range><begin>1</begin><end>2</end></date_range>
                  </report_metadata>
                  <policy_published>
                    <domain>example.com</domain>
                    <adkim>r</adkim>
                    <aspf>r</aspf>
                    <p>none</p>
                    <sp>none</sp>
                  </policy_published>
                </feedback>
                """;

        assertThatThrownBy(() -> parser.parse(xxeXml))
                .isInstanceOf(DmarcReportParseException.class);
    }
}
