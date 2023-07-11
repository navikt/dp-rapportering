package no.nav.dagpenger.rapportering

import java.time.LocalDateTime
import java.util.UUID

class Godkjenningslogg(godkjenninger: List<Godkjenningsendring> = emptyList()) {
    private val godkjenninger = godkjenninger.toMutableList()

    fun leggTil(godkjenningsendring: Godkjenningsendring) {
        require(!godkjent()) { "Kan ikke godkjenne noe som allerede er godkjent" }
        godkjenninger.add(godkjenningsendring)
    }

    fun avgodkjenn(godkjenningsendring: Godkjenningsendring) {
        require(godkjent()) { "Må være godkjent for å kunne avgodkjenne" }
        gjeldende().avgodkjenn(godkjenningsendring)
    }

    private fun gjeldende() = godkjenninger.last()
    fun godkjent() = godkjenninger.lastOrNull()?.godkjent() ?: false
    fun accept(visitor: RapporteringsperiodVisitor) {
        godkjenninger.forEach { it.accept(visitor) }
    }
}

class Godkjenningsendring(
    val id: UUID,
    private val utførtAv: Kilde,
    private val opprettet: LocalDateTime,
    private val begrunnelse: String? = null,
    private var avgodkjentAv: Godkjenningsendring? = null,
) {
    private val avgodkjent = avgodkjentAv?.opprettet

    constructor(utførtAv: Sluttbruker) : this(UUID.randomUUID(), utførtAv, LocalDateTime.now())

    constructor(utførtAv: Saksbehandler, begrunnelse: String) : this(
        UUID.randomUUID(),
        utførtAv,
        LocalDateTime.now(),
        begrunnelse = begrunnelse,
    )

    fun avgodkjenn(godkjenningsendring: Godkjenningsendring) {
        avgodkjentAv = godkjenningsendring
    }

    fun godkjent() = avgodkjentAv == null
    fun kanEndre(kilde: Kilde) = kilde == utførtAv
    fun accept(visitor: RapporteringsperiodVisitor) {
        avgodkjentAv?.accept(visitor)
        visitor.visit(this, id, utførtAv, opprettet, avgodkjentAv, begrunnelse)
    }

    sealed class Kilde(val id: String) {
        abstract fun kanEndre(godkjenningsendring: Godkjenningsendring): Boolean
    }

    data class Saksbehandler(private val saksbehandlerId: String) : Kilde(saksbehandlerId) {
        override fun kanEndre(godkjenningsendring: Godkjenningsendring) = true
    }

    data class Sluttbruker(private val ident: String) : Kilde(ident) {
        override fun kanEndre(godkjenningsendring: Godkjenningsendring) = godkjenningsendring.kanEndre(this)
    }
}
