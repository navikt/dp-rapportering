package no.nav.dagpenger.rapportering

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.dagpenger.rapportering.hendelser.PersonHendelse
import no.nav.dagpenger.rapportering.serialisering.Jackson.config
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration

class UtsendingsObserver(private val rapidsConnection: RapidsConnection, private val hendelse: PersonHendelse) :
    PersonObserver {

    override fun rapporteringsperiodeInnsendt(event: RapporteringsperiodeObserver.RapporteringsperiodeInnsendt) {
        val ident = hendelse.ident()
        val dager = event.dager.map { DagJsonBuilder(it).json }
        rapidsConnection.publish(
            key = ident,
            message = JsonMessage.newMessage(
                "rapporteringsperiode_innsendt_hendelse",
                mapOf(
                    "ident" to ident,
                    "rapporteringsId" to event.rapporteringsperiodeId,
                    "fom" to event.fom,
                    "tom" to event.tom,
                    "dager" to dager,
                ),
            ).toJson(),
        )
    }

    private class DagJsonBuilder(dag: Dag) : DagVisitor {
        private val mapper = jacksonObjectMapper().also { it.config() }

        private lateinit var dato: LocalDate
        private val aktiviteter = mutableListOf<Map<String, Any>>()

        init {
            dag.accept(this)
        }

        val json: Map<String, Any>
            get() = mapOf(
                "dato" to dato,
                "aktiviteter" to aktiviteter,
            )

        override fun visit(
            dag: Dag,
            dato: LocalDate,
            aktiviteter: List<Aktivitet>,
            muligeAktiviter: List<Aktivitet.AktivitetType>,
        ) {
            this.dato = dato
        }

        override fun visit(
            aktivitet: Aktivitet,
            uuid: UUID,
            dato: LocalDate,
            tid: Duration,
            type: Aktivitet.AktivitetType,
            tilstand: Aktivitet.TilstandType,
        ) {
            this.aktiviteter.add(
                mapOf(
                    "type" to type,
                    "tid" to tid.toIsoString(),
                ),
            )
        }
    }

    override fun rapporteringsperiodeEndret(event: RapporteringsperiodeObserver.RapporteringsperiodeEndret) {
        rapidsConnection.publish(
            hendelse.ident(),
            JsonMessage.newMessage(
                "rapporteringsperiode_endret",
                mapOf(
                    "rapporteringsperiodeId" to event.rapporteringsperiodeId,
                    "gjeldendeTilstand" to event.gjeldendeTilstand,
                    "fom" to event.fom,
                    "tom" to event.tom,
                ),
            ).toJson(),
        )
    }
}
