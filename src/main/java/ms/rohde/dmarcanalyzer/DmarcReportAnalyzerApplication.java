package ms.rohde.dmarcanalyzer;

import ms.rohde.hexagonalarch.spring.ArchComponentScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@ArchComponentScan("ms.rohde.dmarcanalyzer")
@EnableScheduling
public class DmarcReportAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DmarcReportAnalyzerApplication.class, args);
    }
}
