package com.scanrelay.app.util

import com.scanrelay.app.data.model.UpiPaymentData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiRiskAnalyzerTest {

    @Test
    fun `clean payment data produces no risk warnings`() {
        val payment = UpiPaymentData(
            payeeVpa = "merchant@okhdfcbank",
            payeeName = "Acme Store",
            amount = "250.00",
            currency = "INR",
            note = null,
            refId = null,
            rawUri = "upi://pay?pa=merchant@okhdfcbank&pn=Acme%20Store&am=250.00"
        )

        val warnings = UpiRiskAnalyzer.analyze(payment)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `missing payee name triggers a warning`() {
        val payment = UpiPaymentData(
            payeeVpa = "shop@upi",
            payeeName = null,
            amount = "100",
            currency = "INR",
            note = null,
            refId = null,
            rawUri = "upi://pay?pa=shop@upi&am=100"
        )

        val warnings = UpiRiskAnalyzer.analyze(payment)
        assertEquals(1, warnings.size)
        assertEquals("Payee Name Missing", warnings[0].title)
    }

    @Test
    fun `malformed VPA without at-sign triggers severe warning`() {
        val payment = UpiPaymentData(
            payeeVpa = "not-a-vpa",
            payeeName = "Suspicious",
            amount = "100",
            currency = "INR",
            note = null,
            refId = null,
            rawUri = "upi://pay?pa=not-a-vpa&pn=Suspicious&am=100"
        )

        val warnings = UpiRiskAnalyzer.analyze(payment)
        assertTrue(warnings.any { it.isSevere && it.title == "Suspicious UPI ID Format" })
    }

    @Test
    fun `non-INR currency triggers warning`() {
        val payment = UpiPaymentData(
            payeeVpa = "store@bank",
            payeeName = "Global Store",
            amount = "50",
            currency = "USD",
            note = null,
            refId = null,
            rawUri = "upi://pay?pa=store@bank&pn=Global%20Store&am=50&cu=USD"
        )

        val warnings = UpiRiskAnalyzer.analyze(payment)
        assertTrue(warnings.any { it.isSevere && it.title == "Non-INR Currency" })
    }
}
