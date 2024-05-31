package no.nav.dagpenger.rapportering.repository

import kotliquery.Row
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
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("SELECT * FROM rapporteringsperiode WHERE ID = ? AND IDENT = ?", id, ident)
                    .map { it.toRapporteringsperiode() }
                    .asSingle,
            )
        }
    }

    override fun hentRapporteringsperioder(): List<Rapporteringsperiode> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("SELECT * FROM rapporteringsperiode")
                    .map { it.toRapporteringsperiode() }.asList,
            )
        }
    }

    override fun lagreRapporteringsperiode(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    INSERT INTO rapporteringsperiode 
                    (id, ident, kan_sendes, kan_sendes_fra, kan_korrigeres, brutto_belop, status, registrert_arbeidssoker, fom, tom) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    rapporteringsperiode.id,
                    ident,
                    rapporteringsperiode.kanSendes,
                    rapporteringsperiode.kanSendesFra,
                    rapporteringsperiode.kanKorrigeres,
                    rapporteringsperiode.bruttoBelop,
                    rapporteringsperiode.status.name,
                    rapporteringsperiode.registrertArbeidssoker,
                    rapporteringsperiode.periode.fraOgMed,
                    rapporteringsperiode.periode.tilOgMed,
                ).asUpdate,
            )
        }
    }
}

private fun Row.toRapporteringsperiode() =
    Rapporteringsperiode(
        id = this.long("id"),
        kanSendesFra = this.localDate("kan_sendes_fra"),
        kanSendes = this.boolean("kan_sendes"),
        kanKorrigeres = this.boolean("kan_korrigeres"),
        bruttoBelop = this.doubleOrNull("brutto_belop"),
        status = RapporteringsperiodeStatus.TilUtfylling,
        registrertArbeidssoker = this.stringOrNull("registrert_arbeidssoker")?.toBoolean(),
        dager = emptyList(),
        periode =
            Periode(
                fraOgMed = this.localDate("fom"),
                tilOgMed =
                    this.localDate(
                        "tom",
                    ),
            ),
    )
