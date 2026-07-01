package ms.rohde.dmarcanalyzer.core.domain.analysis;

import ms.rohde.hexagonalarch.annotations.DomainValueObject;

/**
 * The subject and body text of a summary email, composed purely from domain
 * data by {@link EmailSummaryComposer}.
 */
@DomainValueObject
public record ComposedEmailSummary(String subject, String bodyText) {
}
