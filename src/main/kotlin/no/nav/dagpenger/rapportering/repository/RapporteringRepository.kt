package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import java.util.UUID

interface RapporteringRepository {
    fun hentRapporteringsperiode(
        id: Long,
        ident: String,
    ): Rapporteringsperiode?

    fun hentRapporteringsperioder(): List<Rapporteringsperiode>

    fun lagreRapporteringsperiodeOgDager(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    )

    fun lagreAktiviteter(
        rapporteringId: Long,
        dag: Dag,
    )

    fun oppdaterRegistrertArbeidssoker(
        rapporteringId: Long,
        ident: String,
        registrertArbeidssoker: Boolean,
    )

    fun oppdaterRapporteringsperiodeFraArena(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    )

    fun oppdaterRapporteringStatus(
        rapporteringId: Long,
        ident: String,
        status: RapporteringsperiodeStatus,
    )

    fun slettAktivitet(aktivitetId: UUID): Int
}
