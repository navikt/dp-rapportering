package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import java.util.UUID

interface RapporteringRepository {
    fun hentRapporteringsperiode(
        id: Long,
        ident: String,
    ): Rapporteringsperiode?

    fun hentRapporteringsperioder(): List<Rapporteringsperiode>

    fun lagreRapporteringsperiode(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    )

    fun lagreAktiviteter(
        rapporteringId: Long,
        dag: Dag,
    )

    fun slettAktivitet(aktivitetId: UUID): Int
}
