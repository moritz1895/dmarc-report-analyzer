package ms.rohde.dmarcanalyzer.core.domain.report;

import ms.rohde.hexagonalarch.annotations.DomainValueObject;

/**
 * A single DKIM signature evaluation from a record's
 * {@code auth_results/dkim} element. A record can carry several of these
 * when the message was signed with multiple DKIM signatures.
 */
@DomainValueObject
public record DkimAuthResult(String domain, DmarcAuthResultValue result) {
}
