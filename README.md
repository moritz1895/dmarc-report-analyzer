# dmarc-report-analyzer

Überwacht ein beliebiges Postmaster-Postfach (Adresse konfigurierbar über die Umgebungsvariable `POSTMASTER_EMAIL`, z. B. `postmaster@example.com`) per IMAP auf eingehende DMARC-Aggregatreports (RUA), wertet jeden Report per Claude Haiku 4.5 aus und schickt eine leicht verständliche Zusammenfassung mit konkreten Handlungsempfehlungen per SMTP an denselben Postmaster zurück.

## Funktionsweise

```
Mailserver (Postmaster-Postfach, z. B. postmaster@example.com)
        │  DMARC-Reports (XML, meist .gz/.zip-Anhang)
        ▼
  IMAP-Adapter  ──►  Domain-Parser  ──►  Statistik  ──►  Claude Haiku 4.5  ──►  SMTP-Adapter
                                                                                     │
                                                                                     ▼
                                                                     Zusammenfassung + Empfehlungen
                                                                       an dasselbe Postmaster-Postfach
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
- Ein IMAP-/SMTP-fähiges Postmaster-Postfach (beliebige Adresse)
- Ein Anthropic-API-Key ([console.anthropic.com](https://console.anthropic.com))

## Schnellstart mit Docker Compose

```bash
cp .env.example .env
# .env mit echten Werten befüllen: POSTMASTER_EMAIL, IMAP_HOST, SMTP_HOST,
# IMAP_PASSWORD, SMTP_PASSWORD, ANTHROPIC_API_KEY
docker compose up -d
```

`docker-compose.yml` leitet `POSTMASTER_EMAIL`/`IMAP_HOST`/`SMTP_HOST` aus `.env` in die darunterliegenden `DMARC_ANALYZER_MAIL_*`-Variablen der Anwendung weiter (siehe Konfigurationstabellen unten) — beim direkten Start ohne Docker Compose (`mvn spring-boot:run` oder ein eigenes Deployment) sind stattdessen die `DMARC_ANALYZER_MAIL_*`-Variablen selbst zu setzen.

## Deployment auf dem Homeserver

`Dockerfile.deploy` und `docker-compose.server.yml` sind für den produktiven Betrieb auf einem
Homeserver ohne eigene Docker-Registry gedacht (`docker-compose.yml`/`Dockerfile` bleiben für den
lokalen Schnellstart). `Dockerfile.deploy` baut kein Jar mehr selbst, sondern kopiert nur ein
bereits lokal gebautes Jar in ein schlankes Runtime-Image — nötig, weil `ms.rohde:hexagonal-arch-*`
ausschließlich im lokalen `.m2`-Cache liegt und ein isolierter Docker-Build es nicht auflösen kann.

```bash
# 1. Jar lokal bauen (hat Zugriff auf das lokale .m2-Cache)
mvn clean package -DskipTests

# 2. Runtime-Image daraus bauen
docker build -f Dockerfile.deploy -t dmarc-report-analyzer:latest .

# 3. Image zum Server übertragen (kein Registry-Push nötig)
docker save dmarc-report-analyzer:latest | gzip | ssh user@homeserver 'gunzip | docker load'

# 4. Einmalig (oder bei Änderungen): .env und docker-compose.server.yml auf den Server kopieren
scp .env docker-compose.server.yml user@homeserver:/srv/docker/dmarc-analyzer/

# 5. Auf dem Server: Container mit dem neuen Image (neu) starten
ssh user@homeserver 'cd /srv/docker/dmarc-analyzer && docker compose -f docker-compose.server.yml up -d'
```

`docker compose up -d` erkennt automatisch, dass sich die `latest`-Image-ID durch `docker load`
geändert hat, und ersetzt den laufenden Container entsprechend — ein manuelles `down`/`up` ist
normalerweise nicht nötig.

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
| `dmarc-analyzer.mail.imap.username` | `DMARC_ANALYZER_MAIL_IMAP_USERNAME` | – (erforderlich) | Postfach-Login (das Postmaster-Postfach, z. B. `postmaster@example.com`) |
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
| `dmarc-analyzer.mail.smtp.recipient-address` | `DMARC_ANALYZER_MAIL_RECIPIENT` | – (erforderlich) | Empfänger der Zusammenfassung (i. d. R. dasselbe Postmaster-Postfach) |

### KI-Analyse (Claude)

| Property | Umgebungsvariable | Default | Beschreibung |
|---|---|---|---|
| — | `ANTHROPIC_API_KEY` | – (erforderlich) | Anthropic-API-Key (vom SDK automatisch aus der Umgebung gelesen) |
| `dmarc-analyzer.ai.anthropic.model` | `DMARC_ANALYZER_AI_ANTHROPIC_MODEL` | `claude-haiku-4-5` | Zu verwendendes Claude-Modell |

### Zeitplan

| Property | Umgebungsvariable | Default | Beschreibung |
|---|---|---|---|
| `dmarc-analyzer.schedule.cron` | `DMARC_ANALYZER_SCHEDULE_CRON` | `0 */15 * * * *` | Cron-Ausdruck für das Polling-Intervall |

### Logging

| Property | Umgebungsvariable | Default | Beschreibung |
|---|---|---|---|
| — | `DMARC_ANALYZER_LOG_LEVEL` | `INFO` | Log-Level für `ms.rohde.dmarcanalyzer`. Auf `DEBUG` setzen, um pro Lauf/Mail nachzuvollziehen, was der Service tut — z. B. wie viele ungelesene Mails gefunden wurden, warum eine Mail ohne passenden DMARC-Anhang übersprungen wird, oder wann IMAP/SMTP/Anthropic-Aufrufe starten und enden. |

## Hinweis zum Postfach

Der Service markiert erfolgreich verarbeitete E-Mails über das IMAP-`\Seen`-Flag. Wird dasselbe Postfach zusätzlich manuell von einem Menschen gelesen, kann das zu Kollisionen führen — für den produktiven Betrieb empfiehlt sich ein dediziertes Postfach bzw. ein dedizierter Ordner ausschließlich für DMARC-Reports (z. B. per serverseitiger Weiterleitungsregel).
