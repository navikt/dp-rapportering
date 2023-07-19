#language: no
Egenskap: Rapporteringsplikt
  Brukere som søker om dagpenger eller får innvilget dagpenger skal få rapporteringsplikt

  Scenariomal: Søknad gir rapporteringsplikt
    Gitt en ny bruker
    Når brukeren søker om dagpenger den "<søknadsdato>" og ønsker dagpenger fra "<ønsketdato>"
    Så skal brukeren få rapporteringsplikt på grunn av "søknad"
    Og rapporteringsplikten gjelder fra "<rapporteringsplikt>"

    Eksempler:
      | søknadsdato | ønsketdato | rapporteringsplikt |
      | 2020-01-15  | 2020-01-20 | 2020-01-20         |
      | 2020-01-20  | 2020-01-15 | 2020-01-20         |

  Scenariomal: Vedtak gir rapporteringsplikt
    Gitt en ny bruker
    Når brukeren får innvilget vedtak om dagpenger med virkningsdato "<virkningsdato>"
    Så skal brukeren få rapporteringsplikt på grunn av "vedtak"
    Og rapporteringsplikten gjelder fra "<rapporteringsplikt>"

    Eksempler:
      | virkningsdato | rapporteringsplikt |
      | 2020-01-15    | 2020-01-15         |
