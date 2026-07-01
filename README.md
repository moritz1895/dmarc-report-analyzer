# dmarc-report-analyzer

Überwacht das Postfach `postmaster@rohde.ms` per IMAP auf eingehende DMARC-Aggregatreports (RUA), wertet jeden Report per Claude Haiku 4.5 aus und schickt eine leicht verständliche Zusammenfassung mit konkreten Handlungsempfehlungen per SMTP an denselben Postmaster zurück.

## Funktionsweise

```
Mailserver (postmaster@rohde.ms)
        │  DMARC-Reports (XML, meist .gz/.zip-Anhang)
        ▼
  IMAP-Adapter  ──►  Domain-Parser  ──►  Statistik  ──►  Claude Haiku 4.5  ──►  SMTP-Adapter
                                                                                     │
                                                                                     ▼
                                                                     Zusammenfassung + Empfehlungen
                                                                       an postmaster@rohde.ms
```

Der Service läuft als geplanter Hintergrundjob (konfigurierbarer Cron, Standard: alle 15 Minuten), öffnet keinen eingehenden Port und benötigt keine Datenbank — jede E-Mail wird direkt verarbeitet und über ein IMAP-Flag als bearbeitet markiert.

## Warum Claude Haiku 4.5 statt eines eingebetteten Modells?

DMARC-Aggregatreports sind kleine XML-Dateien, die bei einer typischen Domain in einstelliger bis niedriger zweistelliger Zahl pro Tag eintreffen. Bei Claude Haiku 4.5 ($1 / $5 pro 1M Tokens) liegen die Kosten pro Report im Bereich von Bruchteilen eines Cents — selbst bei hohem Volumen bleiben die monatlichen Kosten im Cent- bis niedrigen Euro-Bereich. Ein eingebettetes lokales Modell würde dagegen mehrere hundert MB bis GB an Gewichten ins Docker-Image bringen, dauerhaft RAM/CPU binden und bei der eigentlichen Aufgabe — Text- und Reasoning-Verständnis über SPF/DKIM/DMARC-Zusammenhänge — deutlich schlechtere Ergebnisse liefern. Die Cloud-Lösung hält den Service dadurch sowohl günstiger als auch tatsächlich "leichtgewichtiger" (kein ML-Runtime-Overhead im Container).

## Architektur

Hexagonale Architektur (Ports & Adapters), siehe [`hexagonal-arch`](https://github.com/) Annotation-Bibliothek:

- `core/domain` — reines DMARC-Domänenmodell (Reportstruktur, XML-Parser, Statistikberechnung), keine Framework-Abhängigkeiten.
- `core/app` — `DmarcReportProcessingService`: orchestriert Abruf, Parsing, KI-Analyse, Versand und Markierung als bearbeitet.
- `ports/inbound` — `DmarcReportProcessingUseCase` (vom Scheduler aufgerufen).
- `ports/outbound` — `MailboxPort` (IMAP), `EmailSenderPort` (SMTP), `DmarcAnalysisAiPort` (Claude).
- `adapters/inbound/scheduler` — Spring `@Scheduled`-Trigger.
- `adapters/outbound/imap` — Jakarta-Mail-IMAP-Adapter (inkl. Entpacken von `.gz`/`.zip`-Anhängen).
- `adapters/outbound/smtp` — Jakarta-Mail-SMTP-Adapter.
- `adapters/outbound/ai` — Anthropic-Java-SDK-Adapter (Claude Haiku 4.5, strukturierte Ausgabe).

## Anforderungen

- Java 25 (für lokale Builds)
- Docker (für den containerisierten Betrieb)
- Ein IMAP-/SMTP-fähiges Postfach `postmaster@rohde.ms`
- Ein Anthropic-API-Key ([console.anthropic.com](https://console.anthropic.com))

## Schnellstart mit Docker Compose

```bash
cp .env.example .env
# .env mit echten Zugangsdaten befüllen (IMAP_PASSWORD, SMTP_PASSWORD, ANTHROPIC_API_KEY)
docker compose up -d
```

## Lokal bauen und starten

```bash
mvn clean install          # Build inkl. aller Tests (JUnit 5, ArchUnit)
mvn spring-boot:run        # Lokal starten (Umgebungsvariablen s.u. müssen gesetzt sein)
```

## Konfiguration

Alle Parameter werden über `application.yml` bzw. Umgebungsvariablen gesetzt.

### IMAP (eingehende DMARC-Reports)

| Property | Umgebungsvariable | Default | Beschreibung |
|---|---|---|---|
| `dmarc-analyzer.mail.imap.host` | `DMARC_ANALYZER_MAIL_IMAP_HOST` | – (erforderlich) | IMAP-Server-Hostname |
| `dmarc-analyzer.mail.imap.port` | `DMARC_ANALYZER_MAIL_IMAP_PORT` | `993` | IMAP-Port |
| `dmarc-analyzer.mail.imap.username` | `DMARC_ANALYZER_MAIL_IMAP_USERNAME` | – (erforderlich) | Postfach-Login (`postmaster@rohde.ms`) |
| `dmarc-analyzer.mail.imap.password` | `DMARC_ANALYZER_MAIL_IMAP_PASSWORD` | – (erforderlich) | Postfach-Passwort |
| `dmarc-analyzer.mail.imap.use-ssl` | `DMARC_ANALYZER_MAIL_IMAP_USE_SSL` | `true` | Implizites TLS (IMAPS) statt STARTTLS |
| `dmarc-analyzer.mail.imap.folder` | `DMARC_ANALYZER_MAIL_IMAP_FOLDER` | `INBOX` | Zu überwachender Ordner |

### SMTP (ausgehende Zusammenfassung)

| Property | Umgebungsvariable | Default | Beschreibung |
|---|---|---|---|
| `dmarc-analyzer.mail.smtp.host` | `DMARC_ANALYZER_MAIL_SMTP_HOST` | – (erforderlich) | SMTP-Server-Hostname |
| `dmarc-analyzer.mail.smtp.port` | `DMARC_ANALYZER_MAIL_SMTP_PORT` | `587` | SMTP-Port |
| `dmarc-analyzer.mail.smtp.username` | `DMARC_ANALYZER_MAIL_SMTP_USERNAME` | – (erforderlich) | SMTP-Login |
| `dmarc-analyzer.mail.smtp.password` | `DMARC_ANALYZER_MAIL_SMTP_PASSWORD` | – (erforderlich) | SMTP-Passwort |
| `dmarc-analyzer.mail.smtp.use-start-tls` | `DMARC_ANALYZER_MAIL_SMTP_USE_STARTTLS` | `true` | STARTTLS statt implizitem TLS |
| `dmarc-analyzer.mail.smtp.from-address` | `DMARC_ANALYZER_MAIL_SMTP_FROM_ADDRESS` | – (erforderlich) | Absenderadresse der Zusammenfassung |
| `dmarc-analyzer.mail.smtp.recipient-address` | `DMARC_ANALYZER_MAIL_RECIPIENT` | – (erforderlich) | Empfänger der Zusammenfassung (i. d. R. `postmaster@rohde.ms`) |

### KI-Analyse (Claude)

| Property | Umgebungsvariable | Default | Beschreibung |
|---|---|---|---|
| — | `ANTHROPIC_API_KEY` | – (erforderlich) | Anthropic-API-Key (vom SDK automatisch aus der Umgebung gelesen) |
| `dmarc-analyzer.ai.anthropic.model` | `DMARC_ANALYZER_AI_ANTHROPIC_MODEL` | `claude-haiku-4-5` | Zu verwendendes Claude-Modell |

### Zeitplan

| Property | Umgebungsvariable | Default | Beschreibung |
|---|---|---|---|
| `dmarc-analyzer.schedule.cron` | `DMARC_ANALYZER_SCHEDULE_CRON` | `0 */15 * * * *` | Cron-Ausdruck für das Polling-Intervall |

## Hinweis zum Postfach

Der Service markiert erfolgreich verarbeitete E-Mails über das IMAP-`\Seen`-Flag. Wird dasselbe Postfach zusätzlich manuell von einem Menschen gelesen, kann das zu Kollisionen führen — für den produktiven Betrieb empfiehlt sich ein dediziertes Postfach bzw. ein dedizierter Ordner ausschließlich für DMARC-Reports (z. B. per serverseitiger Weiterleitungsregel).
