package no.nav.dagpenger.rapportering.utils

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import com.openhtmltopdf.util.XRLog
import mu.KLogging
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.jsoup.nodes.Element
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.function.Consumer
import java.util.logging.Level

class PDFGenerator {
    companion object : KLogging()

    init {
        // By default, com.openhtmltopdf has INFO log level
        // Changing it to WARNING
        XRLog.listRegisteredLoggers().forEach(
            Consumer { logger: String? ->
                XRLog.setLevel(
                    logger,
                    Level.WARNING,
                )
            },
        )
    }

    private val fontFamily = "Open Sans"
    private val font = this::class.java.getResource("/OpenSans-Regular.ttf")!!.readBytes()
    private val colorProfile = this::class.java.getResource("/sRGB2014.icc")!!.readBytes()

    fun createPDFA(html: String): ByteArray {
        // By default, PdfRendererBuilder requires strict XML
        // We can convert our HTML to HTML5 using Jsoup and then convert HTML5 to W3C DOM Document
        // This W3C DOM Document can be used instead of strict XML
        val html5 = Jsoup.parse(html, "UTF-8")

        // We also have to explicitly state in our HTML which font we want to use
        val head: Element = html5.head()
        head.append(
            """
            <style>
                * {
                    font-family: "$fontFamily";
                    font-size: 14px;
                    line-height: 150%;
                }
                h1 {
                    text-align: center;
                    font-size: 28px;
                    font-weight: bold;
                }
                svg {
                    margin: auto;
                }
                .info {
                    color: lightgrey;
                }
                .forklaring {
                    margin-left: 20px;
                    padding: 5px;
                    background-color: #EEEEEE;
                }
            </style>
            """.trimIndent(),
        )

        // Create PDF
        // We must provide fonts explicitly because PDF/A doesn't allow to use embedded PDF fonts
        // We must also explicitly provide color profile
        val pdf =
            ByteArrayOutputStream()
                .apply {
                    PdfRendererBuilder()
                        .useFont({ ByteArrayInputStream(font) }, fontFamily)
                        .useColorProfile(colorProfile)
                        .usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_U)
                        .useSVGDrawer(BatikSVGDrawer())
                        .withW3cDocument(W3CDom().fromJsoup(html5), null)
                        .toStream(this)
                        .run()
                }.toByteArray()

        // Check that our PDF is PDF/A-compliant
        require(verifyCompliance(pdf)) { "Non-compliant PDF/A :(" }

        return pdf
    }

    private fun verifyCompliance(
        input: ByteArray,
        flavour: PDFAFlavour = PDFAFlavour.PDFA_2_U,
    ): Boolean {
        val pdf = ByteArrayInputStream(input)
        VeraGreenfieldFoundryProvider.initialise()
        val validator = Foundries.defaultInstance().createValidator(flavour, false)
        val result = Foundries.defaultInstance().createParser(pdf).use { validator.validate(it) }

        val failures = result.testAssertions.filter { it.status != TestAssertion.Status.PASSED }

        failures.forEach { test ->
            logger.warn(test.message)
            logger.warn("Location ${test.location.context} ${test.location.level}")
            logger.warn("Status ${test.status}")
            logger.warn("Test number ${test.ruleId.testNumber}")
        }

        return failures.isEmpty()
    }
}
