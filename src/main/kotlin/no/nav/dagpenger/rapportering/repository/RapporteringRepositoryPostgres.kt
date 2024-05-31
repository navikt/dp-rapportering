package no.nav.dagpenger.rapportering.repository

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import javax.sql.DataSource

class RapporteringRepositoryPostgres(private val dataSource: DataSource) : RapporteringRepository {
    override fun hentRapporteringsperiode(
        id: Long,
        ident: String,
    ): Rapporteringsperiode? {
        TODO("Not yet implemented")
    }

    override fun hentRapporteringsperioder(): List<Rapporteringsperiode> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("SELECT * FROM rapporteringsperiode")
                    .map {
                        Rapporteringsperiode(
                            id = it.long("id"),
                            kanSendesFra = it.localDate("kan_sendes_fra"),
                            kanSendes = it.boolean("kan_sendes"),
                            kanKorrigeres = it.boolean("kan_korrigeres"),
                            bruttoBelop = it.double("brutto_belop"),
                            status = RapporteringsperiodeStatus.TilUtfylling,
                            registrertArbeidssoker = it.boolean("registrert_arbeidssoker"),
                            dager = emptyList(),
                            periode =
                                Periode(
                                    fraOgMed = it.localDate("fom"),
                                    tilOgMed =
                                        it.localDate(
                                            "tom",
                                        ),
                                ),
                        )
                    }.asList,
            )
        }
    }

    override fun lagreRapporteringsperiode(rapporteringsperiode: Rapporteringsperiode): Rapporteringsperiode {
        TODO("Not yet implemented")
    }
}
