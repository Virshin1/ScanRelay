package com.scanrelay.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiUriParserTest {

    @Test
    fun `valid standard UPI URI parses successfully`() {
        val raw = "upi://pay?pa=merchant@okaxis&pn=Grocery%20Store&am=150.50&cu=INR&tn=Invoice%2042&tr=REF123456"
        val result = UpiUriParser.parse(raw)

        assertTrue(result is UpiUriParser.ParseResult.Success)
        val data = (result as UpiUriParser.ParseResult.Success).data
        assertEquals("merchant@okaxis", data.payeeVpa)
        assertEquals("Grocery Store", data.payeeName)
        assertEquals("150.50", data.amount)
        assertEquals("INR", data.currency)
        assertEquals("Invoice 42", data.note)
        assertEquals("REF123456", data.refId)
        assertEquals("₹150.50", data.displayAmount)
        assertEquals(raw, data.rawUri)
    }

    @Test
    fun `mixed-case scheme and host are accepted`() {
        val rawUpper = "UPI://PAY?pa=supermart@upi&pn=Supermart&am=500"
        val resultUpper = UpiUriParser.parse(rawUpper)
        assertTrue(resultUpper is UpiUriParser.ParseResult.Success)

        val rawMixed = "uPi://PaY?pa=supermart@upi"
        val resultMixed = UpiUriParser.parse(rawMixed)
        assertTrue(resultMixed is UpiUriParser.ParseResult.Success)
    }

    @Test
    fun `valid UPI URI with trailing slash is accepted`() {
        val raw = "upi://pay/?pa=chaiwala@paytm&am=20"
        val result = UpiUriParser.parse(raw)
        assertTrue(result is UpiUriParser.ParseResult.Success)
        val data = (result as UpiUriParser.ParseResult.Success).data
        assertEquals("chaiwala@paytm", data.payeeVpa)
        assertEquals("20", data.amount)
    }

    @Test
    fun `missing optional fields are handled cleanly`() {
        val raw = "upi://pay?pa=user@okhdfcbank"
        val result = UpiUriParser.parse(raw)
        assertTrue(result is UpiUriParser.ParseResult.Success)

        val data = (result as UpiUriParser.ParseResult.Success).data
        assertEquals("user@okhdfcbank", data.payeeVpa)
        assertNull(data.payeeName)
        assertNull(data.amount)
        assertNull(data.note)
        assertNull(data.refId)
        assertEquals("Amount not set", data.displayAmount)
        assertEquals("Unknown Payee", data.displayName)
    }

    @Test
    fun `percent-encoded characters in name and note are correctly decoded`() {
        val raw = "upi://pay?pa=shop@upi&pn=Alice%20%26%20Bob%20Bakery&tn=Coffee%20%2B%20Croissant"
        val result = UpiUriParser.parse(raw)
        assertTrue(result is UpiUriParser.ParseResult.Success)

        val data = (result as UpiUriParser.ParseResult.Success).data
        assertEquals("Alice & Bob Bakery", data.payeeName)
        assertEquals("Coffee + Croissant", data.note)
    }

    @Test
    fun `malicious host injection is rejected`() {
        // Subdomain / evil domain spoofing
        assertFalse(UpiUriParser.isValidUpiUri("upi://pay.evil.com?pa=hacker@upi"))
        assertFalse(UpiUriParser.isValidUpiUri("upi://evil.com/pay?pa=hacker@upi"))
        assertFalse(UpiUriParser.isValidUpiUri("upi://pay.phishing.org/?pa=hacker@upi"))

        // UserInfo spoofing
        assertFalse(UpiUriParser.isValidUpiUri("upi://pay@evil.com?pa=hacker@upi"))
        assertFalse(UpiUriParser.isValidUpiUri("upi://user:pass@pay?pa=hacker@upi"))

        // Explicit port injection
        assertFalse(UpiUriParser.isValidUpiUri("upi://pay:8080?pa=hacker@upi"))

        // Disallowed subpaths
        assertFalse(UpiUriParser.isValidUpiUri("upi://pay/malicious/path?pa=hacker@upi"))
    }

    @Test
    fun `non-upi schemes are strictly rejected`() {
        assertFalse(UpiUriParser.isValidUpiUri("http://pay?pa=someone@upi"))
        assertFalse(UpiUriParser.isValidUpiUri("https://pay?pa=someone@upi"))
        assertFalse(UpiUriParser.isValidUpiUri("intent://pay?pa=someone@upi#Intent;scheme=upi;end"))
        assertFalse(UpiUriParser.isValidUpiUri("file:///pay?pa=someone@upi"))
        assertFalse(UpiUriParser.isValidUpiUri("content://pay?pa=someone@upi"))
        assertFalse(UpiUriParser.isValidUpiUri("javascript:alert(1)"))
    }

    @Test
    fun `missing pa required parameter is rejected`() {
        assertFalse(UpiUriParser.isValidUpiUri("upi://pay?pn=Store&am=100"))
        assertFalse(UpiUriParser.isValidUpiUri("upi://pay?pa=&am=100"))
        assertFalse(UpiUriParser.isValidUpiUri("upi://pay"))
    }

    @Test
    fun `empty or blank input is rejected`() {
        assertFalse(UpiUriParser.isValidUpiUri(null))
        assertFalse(UpiUriParser.isValidUpiUri(""))
        assertFalse(UpiUriParser.isValidUpiUri("   "))
        assertFalse(UpiUriParser.isValidUpiUri("random string"))
    }
}
