# dp-rapportering

## Formål
> Backend-tjeneste for håndtering av rapporteringsperioder/meldekort knyttet til dagpenger. 
> Applikasjonen tilbyr API-endepunkter for å hente, fylle ut, sende inn, endre og journalføre rapporteringsperioder. 
> Tjenesten benyttes av [dp-rapportering-frontend][dp-rapportering-frontend].

## Hovedfunksjonalitet
- Tilbyr REST API for rapporteringsperioder (henting, utfylling, innsending, endring/korrigering).
- Integrerer med [dp-arena-meldeplikt-adapter][dp-arena-meldeplikt-adapter] for å hente meldekort fra Arena.
- Integrerer med personregisteret for uthenting av personstatus og ansvarlig system. 
  - Om ansvarlig system er "Arena", hentes/sendes rapporteringsperioder fra/til dp-arena-meldeplikt-adapter. 
  - Om ansvarlig system er "DP" hentes/sendes rapporteringsperioder fra/til meldekortregisteret.
- Mellomlagrer rapporteringsperioder under utfylling.
- Kjører automatiske slettejobber:
  - Sletter innsendte rapporteringsperioder 5 dager etter innsending.
  - Sletter ikke-innsendte rapporteringsperioder 30 dager etter siste frist.
  - Sletter midlertidige perioder opprettet ved endring/korrigering hver natt.
- Journalfører innsendte rapporteringsperioder i dokarkiv.
- Logger alle innkommende og utgående kall i 90 dager.
- Samler og eksponerer metrikker for rapportering og systemstatus.

## Mer dokumentasjon
- OpenAPI: https://navikt.github.io/dp-rapportering/
- Dagpenger rapportering backend-dokumentasjon: https://dagpenger-dokumentasjon.ansatt.nav.no/innbyggerflate/ramp/losninger/rapportering.html

[dp-rapportering-frontend]: https://github.com/navikt/dp-rapportering-frontend
[dp-arena-meldeplikt-adapter]: https://github.com/navikt/dp-arena-meldeplikt-adapter
