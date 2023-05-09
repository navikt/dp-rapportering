package no.nav.dagpenger.aktivitetslogg.serde

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.aktivitetslogg.AktivitetsloggMappingPort
import no.nav.dagpenger.aktivitetslogg.AktivitetsloggVisitor

class AktivitetsloggMap : AktivitetsloggVisitor, AktivitetsloggMappingPort {
    private val aktiviteter = mutableListOf<Map<String, Any>>()
    private val alleKontekster = LinkedHashMap<Map<String, Any>, Int>()

    override fun map(log: Aktivitetslogg): Map<String, List<Map<String, Any>>> {
        log.accept(this)
        return mapOf(
            "aktiviteter" to aktiviteter.toList(),
            "kontekster" to alleKontekster.keys.toList(),
        )
    }
}
