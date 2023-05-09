package no.nav.dagpenger.aktivitetslogg

// En spesifikk kontekst i en aktivitetslogg
// En kontekst representerer det som behandles
// https://martinfowler.com/bliki/DDD_Aggregate.html
interface Aktivitetskontekst {
    fun toSpesifikkKontekst(): SpesifikkKontekst
}

// En spesifikk kontekst som også har sin egen aktivitetslogg
interface Subaktivitetskontekst : Aktivitetskontekst {
    val aktivitetslogg: Aktivitetslogg
}
