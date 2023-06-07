package no.nav.dagpenger.rapportering

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.PersonHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringsfristHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.repository.PersonRepository
import no.nav.dagpenger.rapportering.serialisering.Jackson.config
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration

private val sikkerlogg = KotlinLogging.logger("tjenestekall.Mediator")

internal class Mediator(
    private val rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository,
) : IHendelseMediator, PersonRepository by personRepository {
    // TODO - override fun behandle(melding: SøknadInnsendtMelding, hendelse: SøknadInnsendtHendelse, context: MessageContext) {
    override fun behandle(hendelse: SøknadInnsendtHendelse) {
        hentPersonOgHåndter(hendelse.ident(), hendelse) { person ->
            person.behandle(hendelse)
        }
    }

    override fun behandle(hendelse: NyAktivitetHendelse) {
        hentPersonOgHåndter(hendelse.ident(), hendelse) { person ->
            person.behandle(hendelse)
        }
    }

    override fun behandle(hendelse: SlettAktivitetHendelse) {
        hentPersonOgHåndter(hendelse.ident(), hendelse) { person ->
            person.behandle(hendelse)
        }
    }

    override fun behandle(hendelse: GodkjennPeriodeHendelse) {
        hentPersonOgHåndter(hendelse.ident(), hendelse) { person ->
            person.behandle(hendelse)
        }
    }

    override fun behandle(hendelse: RapporteringsfristHendelse) {
        hentPersonOgHåndter(hendelse.ident(), hendelse) { person ->
            person.behandle(hendelse)
        }
    }

    private fun <Hendelse : PersonHendelse> hentPersonOgHåndter(
        ident: String,
        hendelse: Hendelse,
        handler: (Person) -> Unit,
    ) {
        val person = hentEllerOpprettPerson(ident)
        person.registrer(UtsendingsObserver(rapidsConnection, hendelse))
        handler(person)
        finalize(person, hendelse)
    }

    private fun finalize(person: Person, hendelse: PersonHendelse) {
        lagre(person)
        if (!hendelse.harAktiviteter()) return
        if (hendelse.harFunksjonelleFeilEllerVerre()) {
            sikkerlogg.info("aktivitetslogg inneholder feil:\n${hendelse.toLogString()}")
        } else {
            sikkerlogg.info("aktivitetslogg inneholder meldinger:\n${hendelse.toLogString()}")
        }
        // TODO: behovMediator.håndter(context, hendelse)
    }
}

class UtsendingsObserver(private val rapidsConnection: RapidsConnection, private val hendelse: PersonHendelse) :
    PersonObserver {

    override fun rapporteringsperiodeInnsendt(event: RapporteringsperiodeObserver.RapporteringsperiodeInnsendt) {
        val ident = hendelse.ident()
        rapidsConnection.publish(
            key = ident,
            message = JsonMessage.newMessage(
                "rapporteringsperiode_innsendt_hendelse",
                mapOf(
                    "ident" to ident,
                    "id" to event.rapporteringsperiodeId,
                    "fom" to event.fom,
                    "tom" to event.tom,
                    "dager" to event.dager.map { DagJsonBuilder(it).json },
                ),
            ).toJson(),
        )
    }

    private class DagJsonBuilder(dag: Dag) : DagVisitor {
        init {
            dag.accept(this)
        }

        private val mapper = jacksonObjectMapper().also { it.config() }
        private lateinit var dato: LocalDate
        private val aktiviteter = mutableListOf<Map<String, Any>>()
        val json: String
            get() = mapOf(
                "dato" to dato,
                "aktiviteter" to aktiviteter,
            ).let { mapper.writeValueAsString(json) }

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
            aktiviteter.add(
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
