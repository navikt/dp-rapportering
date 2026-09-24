package no.nav.dagpenger.rapportering.api

import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode

data class RapporteringsperiodeRequest(
    val id: String,
    val dager: List<Dag>,
    val begrunnelseEndring: String? = null,
    val registrertArbeidssoker: Boolean? = null,
    val rapporteringstype: String? = null,
)

fun RapporteringsperiodeRequest.toRapporteringsperiode(periodeFraDb: Rapporteringsperiode): Rapporteringsperiode =
    periodeFraDb.copy(
        dager = dager,
        begrunnelseEndring = begrunnelseEndring ?: periodeFraDb.begrunnelseEndring,
        rapporteringstype = rapporteringstype ?: periodeFraDb.rapporteringstype,
        sporsmalOmRegistrertArbeidssoker =
            periodeFraDb.sporsmalOmRegistrertArbeidssoker.copy(
                svarFraBruker = registrertArbeidssoker ?: periodeFraDb.sporsmalOmRegistrertArbeidssoker.svarFraBruker,
            ),
    )
