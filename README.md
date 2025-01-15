# dp-rapportering
## Formål
> Levere endepunkter som gjør det mulig å hente, fylle ut, sende inn og endre rapporteringsperioder. 
Endepunktene benyttes av [**`dp-rapportering-frontend`**][dp-rapportering-frontend] som eksponerer funksjonalitetene til bruker.

## Funksjonalitet
- Henter og sender rapporteringsperioder via [**`dp-arena-meldeplikt-adapter`**][dp-arena-meldeplikt-adapter] som 
konverterer meldekort til rapporteringsperioder og motsatt.
- Mellomlagrer rapporteringsperioder som er til utfylling. 
- Slettejobb kjører hver natt:
  - Mellomlagrede rapporteringsperioder som er sendt inn blir slettet 5 dager etter innsending. 
  - Mellomlagrede rapporteringsperioder som er forbi siste frist for innsending (og ikke er sendt inn) blir slettet 30 dager etter siste dag i perioden.
  - Midlertidige rapporteringsperioder som blir opprettet i forbinnelse med endring/korrigering blir slettet hver natt.
- Jornalfører innsendte rapporteringsperioder i dokarkiv.
- Kall-logg for alle innkommende og utgående kall lagres i 90 dager.

## Mer dokumentasjon
- OpenAPI: https://navikt.github.io/dp-rapportering/
- Rapportering i dagpenger-dokumentasjon: https://dagpenger-dokumentasjon.ansatt.nav.no/innbyggerflate/losninger/rapportering/backend

[dp-rapportering-frontend]: https://github.com/navikt/dp-rapportering-frontend
[dp-arena-meldeplikt-adapter]: https://github.com/navikt/dp-arena-meldeplikt-adapter
