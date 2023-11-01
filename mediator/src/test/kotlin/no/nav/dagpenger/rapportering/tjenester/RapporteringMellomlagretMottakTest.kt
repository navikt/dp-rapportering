package no.nav.dagpenger.rapportering.tjenester

import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.RapporteringMellomlagretHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class RapporteringMellomlagretMottakTest {
    private val rapid = TestRapid()
    private val mediator = mockk<IHendelseMediator>(relaxed = true)

    init {
        RapporteringMellomlagretMottak(rapid, mediator)
    }

    @Test
    fun `vi tar imot og håndterer rapportering mellomlagret hendelser`() {
        rapid.sendTestMessage(løstBehovJSON)

        verify {
            mediator.behandle(any<RapporteringMellomlagretHendelse>())
        }
    }
}

@Language("JSON")
private val løstBehovJSON =
    """
     {
    	"@event_name": "behov",
    	"@behovId": "eb1ae7a9-d314-4f4a-a5e0-360b537ca11f",
    	"@behov": [
    		"MellomlagreRapportering"
    	],
    	"meldingsreferanseId": "d0ce2eef-ab53-4b06-acf3-4c85386dc561",
    	"ident": "12345678910",
    	"MellomlagreRapportering": {
    		"periodeId": "6c43443b-5048-450c-964b-0235f89449fa",
    		"json": "{\"timestamp\":\"2023-10-23T18:53:07.614763446\", \"claims\":{ \"sub\":{\"missing\":false,\"null\":false},\"iss\":{\"missing\":false,\"null\":false}}, \"image\":\"ghcr.io/navikt/dp-rapportering-frontend:294a16920167022439d00c646b2c03e5742a1470\", \"kildekode\":\"294a16920167022439d00c646b2c03e5742a1470\", \"klient\":\"node\", \"språk\":\"no-NB\", \"rapportering\":{ \"2023-07-31\":{\"Arbeid\":54000000000000}, \"2023-08-01\":{\"Arbeid\":54000000000000}, \"2023-08-02\":{\"Arbeid\":28800000000000}, \"2023-08-03\":{}, \"2023-08-04\":{}, \"2023-08-05\":{}, \"2023-08-06\":{}, \"2023-08-07\":{}, \"2023-08-08\":{}, \"2023-08-09\":{\"Syk\":172800000000000}, \"2023-08-10\":{}, \"2023-08-11\":{\"Ferie\":172800000000000}, \"2023-08-12\":{}, \"2023-08-13\":{} }, \"@id\":\"99c0df05-6ce7-4bf4-b46a-80c4bc3b1041\", \"@opprettet\":\"2023-10-23T18:53:07.730688948\", \"system_read_count\":0, \"system_participating_services\":[{\"id\": \"99c0df05-6ce7-4bf4-b46a-80c4bc3b1041\", \"service\": \"dp-rapportering\"}]}"
    	},
    	"@id": "9359ecf5-58e6-4e6f-b4a6-bf7f16ff49f8",
    	"@opprettet": "2023-10-24T13:56:25.925991300",
    	"system_read_count": 2,
    	"system_participating_services": [
    		{
    			"id": "30ef9625-196a-445b-9b4e-67e0e6a5118d",
    			"service": "dp-rapportering"
    		},
    		{
    			"id": "30ef9625-196a-445b-9b4e-67e0e6a5118d",
    			"time": "2023-10-24T13:56:24.474673700"
    		},
    		{
    			"id": "9359ecf5-58e6-4e6f-b4a6-bf7f16ff49f8",
    			"time": "2023-10-24T13:56:25.925991300"
    		}
    	],
    	"@løsning": {
    		"MellomlagreRapportering": [
    			{
    				"metainfo": {
    					"innhold": "netto.pdf",
    					"filtype": "PDF",
    					"variant": "NETTO"
    				},
    				"urn": "urn:vedlegg:journalpostId/netto.pdf"
    			}
    		]
    	},
    	"@forårsaket_av": {
    		"id": "30ef9625-196a-445b-9b4e-67e0e6a5118d",
    		"opprettet": "2023-10-23T18:53:08.056035121",
    		"event_name": "behov",
    		"behov": [
    			"MellomlagreRapportering"
    		]
    	}
    }
    """.trimIndent()
