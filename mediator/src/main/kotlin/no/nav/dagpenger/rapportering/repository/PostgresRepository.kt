package no.nav.dagpenger.rapportering.repository

import kotliquery.Query
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.DagVisitor
import no.nav.dagpenger.rapportering.Godkjenningsendring
import no.nav.dagpenger.rapportering.Godkjenningslogg
import no.nav.dagpenger.rapportering.IngenRapporteringsplikt
import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.PersonVisitor
import no.nav.dagpenger.rapportering.RapporteringsperiodVisitor
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Godkjent
import no.nav.dagpenger.rapportering.Rapporteringsplikt
import no.nav.dagpenger.rapportering.RapporteringspliktSøknad
import no.nav.dagpenger.rapportering.RapporteringspliktType
import no.nav.dagpenger.rapportering.RapporteringspliktVedtak
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.AktivitetType
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import no.nav.dagpenger.rapportering.tidslinje.Dag
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Duration

internal class PostgresRepository(private val ds: DataSource) :
    PersonRepository,
    RapporteringsperiodeRepository {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun hentRapporteringsperiode(ident: String, uuid: UUID): Rapporteringsperiode? {
        val root = requireNotNull(finnRoot(uuid))
        val kjede = hentKjede(ident, root)
        return kjede.lagKjede { uuid, korrigerer ->
            hentRapporteringsperiodeMedKorrigering(ident, uuid, korrigerer)
        }
    }

    private fun finnRoot(uuid: UUID) = using(sessionOf(ds)) { session ->
        session.run(
            queryOf(
                //language=PostgreSQL
                statement = """
                WITH RECURSIVE find_root AS (
                    SELECT uuid, korrigerer
                    FROM rapporteringsperiode
                    WHERE uuid = :startUuid
                    
                    UNION ALL
                    
                    SELECT rp.uuid, rp.korrigerer
                    FROM rapporteringsperiode rp
                    JOIN find_root fr ON rp.uuid = fr.korrigerer
                )
                SELECT uuid 
                FROM find_root 
                WHERE korrigerer IS NULL
                """.trimIndent(),
                paramMap = mapOf("startUuid" to uuid),
            ).map { it.uuidOrNull("uuid") }.asSingle,
        )
    }

    override fun hentRapporteringsperioder(ident: String) = using(sessionOf(ds)) { session ->
        session.run(
            queryOf(
                //language=PostgreSQL
                statement = """SELECT uuid FROM rapporteringsperiode WHERE person_ident = :ident AND korrigerer IS NULL ORDER BY fom DESC""",
                paramMap = mapOf("ident" to ident),
            ).map { hentRapporteringsperiode(ident, it.uuid("uuid")) }.asList,
        )
    }

    override fun hentRapporteringspliktFor(ident: String) = using(sessionOf(ds)) { session ->
        session.run(
            queryOf(
                //language=PostgreSQL
                statement = """SELECT uuid, opprettet, gjelder_fra, type FROM rapporteringsplikt LEFT JOIN person p ON rapporteringsplikt.person_id = p.id WHERE p.ident = :ident""",
                paramMap = mapOf("ident" to ident),
            ).map {
                val opprettet = it.localDateTime("opprettet")
                val gjelderFra = it.localDateTime("gjelder_fra")
                val type = RapporteringspliktType.valueOf(it.string("type"))
                val uuid = it.uuid("uuid")
                val rapporteringsplikt = when (type) {
                    RapporteringspliktType.Ingen -> IngenRapporteringsplikt(uuid, gjelderFra)
                    RapporteringspliktType.Søknad -> RapporteringspliktSøknad(uuid, gjelderFra)
                    RapporteringspliktType.Vedtak -> RapporteringspliktVedtak(uuid, gjelderFra)
                }
                Pair(opprettet, rapporteringsplikt)
            }.asList,
        )
    }

    override fun hentRapporteringsperiodeFor(ident: String, dato: LocalDate) =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """SELECT uuid FROM rapporteringsperiode WHERE person_ident = :ident AND :dato BETWEEN fom AND tom""",
                    paramMap = mapOf("ident" to ident, "dato" to dato),
                ).map { row ->
                    hentRapporteringsperiode(ident, row.uuid("uuid"))
                }.asSingle,
            )
        }

    override fun finnIdentForPeriode(periodeId: UUID) =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """SELECT person_ident FROM rapporteringsperiode WHERE uuid = :periodeId""",
                    paramMap = mapOf("periodeId" to periodeId),
                ).map { row ->
                    row.string("person_ident")
                }.asSingle,
            )
        }

    private fun hentPerson(ident: String) = using(sessionOf(ds)) { session ->
        session.run(
            queryOf(
                //language=PostgreSQL
                statement = """SELECT ident FROM person WHERE ident = :ident""",
                paramMap = mapOf("ident" to ident),
            ).map { row ->
                Person(
                    row.string("ident"),
                    hentRapporteringsperioder(ident),
                    hentRapporteringspliktFor(ident),
                )
            }.asSingle,
        )
    }

    override fun hentEllerOpprettPerson(ident: String) = if (personFinnes(ident)) {
        hentPerson(ident)!!
    } else {
        insertPerson(ident)
        Person(ident)
    }

    override fun lagre(person: Person) {
        using(sessionOf(ds)) { session ->
            session.transaction { tx ->
                val queries = LagrePersonStatementBuilder(person).queries
                queries.forEach { query ->
                    tx.run(query.asUpdate)
                }

                logger.info("Lagret person med ${queries.size} spørringer")
            }
        }
    }

    override fun hentIdenterMedGodkjentPeriode() = using(sessionOf(ds)) { session ->
        session.run(
            queryOf(
                //language=PostgreSQL
                statement = """SELECT person_ident FROM rapporteringsperiode WHERE tilstand = :tilstand""",
                paramMap = mapOf(
                    "tilstand" to Godkjent.name,
                ),
            ).map { row ->
                row.string("person_ident")
            }.asList,
        )
    }

    override fun hentIdenterMedRapporteringsplikt(): List<String> {
        return using(sessionOf(dataSource = ds)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """SELECT ident FROM person LEFT JOIN rapporteringsplikt r ON person.id = r.person_id WHERE r.type != 'Ingen'""",
                ).map { row ->
                    row.string("ident")
                }.asList,
            )
        }
    }

    private fun hentRapporteringsperiodeMedKorrigering(
        ident: String,
        uuid: UUID,
        korrigerer: Rapporteringsperiode?,
    ): Rapporteringsperiode? {
        return using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """SELECT * FROM rapporteringsperiode WHERE person_ident = :ident AND uuid = :uuid""",
                    paramMap = mapOf("ident" to ident, "uuid" to uuid),
                ).map { it.toRapporteringsperiode(korrigerer) }.asSingle,
            )
        }
    }

    private fun insertPerson(ident: String) {
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """INSERT INTO person(ident) VALUES (:ident)""",
                    paramMap = mapOf("ident" to ident),
                ).asUpdate,
            )
        }
    }

    private fun personFinnes(ident: String) = using(sessionOf(ds)) { session ->
        session.run(
            queryOf(
                //language=PostgreSQL
                statement = """SELECT EXISTS(SELECT 1 FROM person WHERE ident = :ident) AS finnes""",
                paramMap = mapOf("ident" to ident),
            ).map { row ->
                row.boolean("finnes")
            }.asSingle,
        )
    } ?: false

    private fun Row.toRapporteringsperiode(korrigerer: Rapporteringsperiode? = null): Rapporteringsperiode {
        val rapporteringsperiodeId = uuid("uuid")
        val fraOgMed = localDate("fom")
        val tilOgMed = localDate("tom")
        val eksisterendeDager = hentDager(rapporteringsperiodeId)
        val aktiviteter = hentAktiviteterFor(rapporteringsperiodeId)
        val tidslinje = Aktivitetstidslinje(fraOgMed..tilOgMed) { dato ->
            Dag(
                dato = dato,
                aktiviteter = aktiviteter.filter { it.dato == dato }.toMutableList(),
                strategiType = eksisterendeDager[dato],
            )
        }
        val godkjenningslogg = hentGodkjenningslogg(rapporteringsperiodeId)

        return Rapporteringsperiode.rehydrer(
            rapporteringsperiodeId,
            localDate("beregnes_etter"),
            fraOgMed,
            tilOgMed,
            Rapporteringsperiode.TilstandType.valueOf(this.string("tilstand")),
            localDateTime("opprettet"),
            tidslinje,
            godkjenningslogg,
            korrigerer,
        )
    }

    private fun hentGodkjenningslogg(rapporteringsperiodeId: UUID) = using(sessionOf(ds)) { session ->
        session.transaction { tx: TransactionalSession ->
            tx.run(
                queryOf(
                    // language=PostgreSQL
                    """
                    SELECT g.uuid
                    FROM godkjenningsendring g
                    LEFT JOIN godkjenningsendring g2 ON g.id = g2.avgodkjent_av
                    WHERE g.rapporteringsperiode_id = :rapporteringsperiodeId  AND g2.avgodkjent_av IS NULL
                    """.trimIndent(),
                    mapOf("rapporteringsperiodeId" to rapporteringsperiodeId),
                ).map { tx.hentGodkjenningsendring(it.uuid("uuid")) }.asList,
            )
        }
    }.let { Godkjenningslogg(it) }

    private fun TransactionalSession.hentGodkjenningsendring(godkjenningsId: UUID): Godkjenningsendring =
        run(
            queryOf(
                //language=PostgreSQL
                """
                SELECT g.*, (SELECT uuid FROM godkjenningsendring WHERE g.avgodkjent_av=id) AS avgodkjent_av_uuid
                FROM godkjenningsendring g
                WHERE g.uuid = :uuid
                """.trimIndent(),
                mapOf("uuid" to godkjenningsId),
            ).map { row ->
                val utførerIdent = row.string("utfort_id")
                val kilde = when (row.string("utfort_kilde")) {
                    "Sluttbruker" -> Godkjenningsendring.Sluttbruker(utførerIdent)
                    "Saksbehandler" -> Godkjenningsendring.Saksbehandler(utførerIdent)
                    else -> throw IllegalStateException("Ukjent kilde.")
                }

                val avgodkjentAv = row.uuidOrNull("avgodkjent_av_uuid")?.let { hentGodkjenningsendring(it) }
                Godkjenningsendring(
                    row.uuid("uuid"),
                    kilde,
                    row.localDateTime("opprettet"),
                    row.stringOrNull("begrunnelse"),
                    avgodkjentAv,
                )
            }.asSingle,
        ) ?: throw IllegalArgumentException("Kan ikke hente godkjenningsendring som ikke finnes. UUID=$godkjenningsId")

    private fun hentDager(rapporteringsperiodeId: UUID) = using(sessionOf(ds)) { session ->
        session.run(
            queryOf(
                // language=PostgreSQL
                "SELECT dato, strategi FROM dag WHERE rapporteringsperiode_id = :rapporteringsperiodeId",
                mapOf("rapporteringsperiodeId" to rapporteringsperiodeId),
            ).map { row ->
                Pair(row.localDate("dato"), Dag.StrategiType.valueOf(row.string("strategi")))
            }.asList,
        ).associate { it }
    }

    private fun hentKjede(ident: String, uuid: UUID): List<UUID> {
        return using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    /**
                     * Denne spørringen henter hele kjeden av rapporteringsperioder og korrigeringer
                     *
                     * 1. Først bruker den CTE (WITH RECURSIVE) til å rekursivt hente ut perioden vi skal ha
                     * 2. Så gjør den en UNION med perioden som korrigerer
                     * 3. Så kjører den rekursivt til det er tomt
                     * 4. Så henter vi ut alle IDene i (motsatt) rekkefølge vi må bygge de i
                     */
                    //language=PostgreSQL
                    statement = """
                    WITH RECURSIVE linked_structure AS (
                        SELECT a.uuid, a.korrigerer, a.korrigert_av
                        FROM rapporteringsperiode a
                        WHERE a.person_ident=:ident AND a.uuid = :startUuid
                    
                        UNION ALL
                    
                        SELECT a.uuid, a.korrigerer, a.korrigert_av
                        FROM rapporteringsperiode a
                                 JOIN linked_structure ls ON a.uuid = ls.korrigert_av
                    )
                    SELECT uuid, korrigerer
                    FROM linked_structure
                    """.trimIndent(),
                    mapOf(
                        "ident" to ident,
                        "startUuid" to uuid,
                    ),
                ).map {
                    it.uuidOrNull("uuid")
                }.asList,
            )
        }
    }

    private fun <T> List<UUID>.lagKjede(mapper: (UUID, T?) -> T): T? {
        var periode: T? = null
        this.reversed().foldRight(null) { uuid, previous: T? ->
            mapper(uuid, previous).also {
                if (periode == null) periode = it
            }
        }

        return periode
    }

    private fun hentAktiviteterFor(rapporteringsperiodeId: UUID): List<Aktivitet> {
        return using(sessionOf(ds)) { session ->
            session.run(queryOf("SET intervalstyle=iso_8601").asExecute)
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                        |SELECT * FROM aktivitet 
                        |LEFT JOIN dag_aktivitet d ON aktivitet.uuid = d.aktivitet_id
                        |WHERE d.rapporteringsperiode_id = :rapporteringsperiodeId
                    """.trimMargin(),
                    paramMap = mapOf("rapporteringsperiodeId" to rapporteringsperiodeId),
                ).map { row ->
                    Aktivitet.rehydrer(
                        row.uuid("uuid"),
                        row.localDate("dato"),
                        row.string("type"),
                        row.stringOrNull("tid")?.let { Duration.parse(it) },
                        row.string("tilstand"),
                    )
                }.asList,
            )
        }
    }
}

private class LagrePersonStatementBuilder(person: Person) : PersonVisitor, RapporteringsperiodVisitor, DagVisitor {
    private lateinit var rapporteringspliktOpprettet: LocalDateTime
    private lateinit var rapporteringsperiodeId: UUID
    private lateinit var rapporteringspliktId: UUID
    private lateinit var ident: String
    var queries = mutableListOf<Query>()

    init {
        person.accept(this)
    }

    override fun visit(person: Person, ident: String) {
        this.ident = ident
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = """INSERT INTO person(ident) VALUES (:ident) ON CONFLICT DO NOTHING""",
                paramMap = mapOf("ident" to ident),
            ),
        )
    }

    override fun visit(at: LocalDateTime, item: Rapporteringsplikt) {
        this.rapporteringspliktOpprettet = at
        super<PersonVisitor>.visit(at, item)
    }

    override fun visit(
        rapporteringsplikt: Rapporteringsplikt,
        rapporteringspliktId: UUID,
        type: RapporteringspliktType,
    ) {
        this.rapporteringspliktId = rapporteringspliktId
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = """
                    INSERT INTO rapporteringsplikt(uuid, person_id, type, opprettet, gjelder_fra) 
                    SELECT :rapporteringspliktId, id, :type, :opprettet, :gjelderFra 
                    FROM person WHERE ident = :ident 
                    ON CONFLICT (uuid) DO NOTHING 
                """.trimIndent(),
                mapOf(
                    "ident" to ident,
                    "type" to type.name,
                    "rapporteringspliktId" to rapporteringspliktId,
                    "opprettet" to rapporteringspliktOpprettet,
                    "gjelderFra" to rapporteringsplikt.rapporteringspliktFra,
                ),
            ),
        )
    }

    override fun visit(
        rapporteringsperiode: Rapporteringsperiode,
        id: UUID,
        periode: ClosedRange<LocalDate>,
        tilstand: Rapporteringsperiode.TilstandType,
        beregnesEtter: LocalDate,
        korrigerer: Rapporteringsperiode?,
        korrigertAv: Rapporteringsperiode?,
    ) {
        this.rapporteringsperiodeId = id
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = """
                    INSERT INTO rapporteringsperiode (uuid, person_ident, tilstand, beregnes_etter, fom, tom, korrigerer, korrigert_av)
                    VALUES (:uuid,
                            :ident,
                            :tilstand,
                            :beregnesEtter,
                            :fraOgMed,
                            :tilOgMed,
                            :korrigerer,
                            :korrigertAv)
                    ON CONFLICT (uuid) DO UPDATE SET tilstand = :tilstand, korrigerer = :korrigerer, korrigert_av = :korrigertAv
                """.trimIndent(),
                paramMap = mapOf(
                    "uuid" to id,
                    "ident" to ident,
                    "beregnesEtter" to beregnesEtter,
                    "tilstand" to tilstand.name,
                    "fraOgMed" to periode.start,
                    "tilOgMed" to periode.endInclusive,
                    "korrigerer" to korrigerer?.rapporteringsperiodeId,
                    "korrigertAv" to korrigertAv?.rapporteringsperiodeId,
                ),
            ),
        )
    }

    override fun visit(
        godkjenningsendring: Godkjenningsendring,
        id: UUID,
        utførtAv: Godkjenningsendring.Kilde,
        opprettet: LocalDateTime,
        avgodkjent: Godkjenningsendring?,
        begrunnelse: String?,
    ) {
        queries.add(
            queryOf(
                //language=PostgreSQL
                """
                INSERT INTO godkjenningsendring (uuid, rapporteringsperiode_id, opprettet, begrunnelse, utfort_kilde, utfort_id)
                VALUES (:uuid, :rapporteringsperiodeId, :opprettet, :begrunnelse, :utfort_kilde, :utfort_id)
                ON CONFLICT (uuid) DO UPDATE SET avgodkjent_av = (SELECT id FROM godkjenningsendring WHERE uuid = :avgodkjent_id)
                """.trimIndent(),
                mapOf(
                    "uuid" to id,
                    "rapporteringsperiodeId" to rapporteringsperiodeId,
                    "opprettet" to opprettet,
                    "begrunnelse" to begrunnelse,
                    "avgodkjent_id" to avgodkjent?.id,
                    "utfort_kilde" to utførtAv::class.java.simpleName,
                    "utfort_id" to utførtAv.id,
                ),
            ),
        )
    }

    override fun visit(
        dag: Dag,
        dato: LocalDate,
        aktiviteter: List<Aktivitet>,
        muligeAktiviter: List<AktivitetType>,
        strategi: Dag.StrategiType,
    ) {
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = """
                    INSERT INTO dag (rapporteringsperiode_id, dato, strategi)
                    VALUES (:rapporteringsperiodeId, :dato, :strategi)
                    ON CONFLICT (rapporteringsperiode_id, dato) DO UPDATE SET strategi = :strategi
                """.trimIndent(),
                paramMap = mapOf(
                    "rapporteringsperiodeId" to rapporteringsperiodeId,
                    "dato" to dato,
                    "strategi" to strategi.name,
                ),
            ),
        )
    }

    override fun visit(
        aktivitet: Aktivitet,
        uuid: UUID,
        dato: LocalDate,
        tid: Duration,
        type: AktivitetType,
        tilstand: Aktivitet.TilstandType,
    ) {
        if (tilstand == Aktivitet.TilstandType.Slettet) {
            queries.add(
                queryOf(
                    //language=PostgreSQL
                    statement = """DELETE FROM aktivitet WHERE uuid=:uuid""",
                    paramMap = mapOf("uuid" to uuid),
                ),
            )
            return
        }
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = """
                INSERT INTO aktivitet(uuid,
                                      person_ident,
                                      tilstand,
                                      dato,
                                      "type",
                                      tid)
                VALUES (:uuid,
                        :ident,
                        :tilstand,
                        :dato,
                        :type,
                        :tid::interval)
                ON CONFLICT (uuid) DO UPDATE SET tilstand = :tilstand
                """.trimIndent(),
                paramMap = mapOf(
                    "uuid" to aktivitet.uuid,
                    "ident" to ident,
                    "tilstand" to tilstand.name,
                    "dato" to dato,
                    "type" to type.name,
                    "tid" to tid.toIsoString(),
                ),
            ),
        )
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = """
                    INSERT INTO dag_aktivitet (rapporteringsperiode_id, aktivitet_id)
                    VALUES (:rapporteringsperiodeId, :aktivitetId)
                    ON CONFLICT DO NOTHING
                """.trimIndent(),
                paramMap = mapOf(
                    "rapporteringsperiodeId" to rapporteringsperiodeId,
                    "aktivitetId" to aktivitet.uuid,
                ),
            ),
        )
    }
}
