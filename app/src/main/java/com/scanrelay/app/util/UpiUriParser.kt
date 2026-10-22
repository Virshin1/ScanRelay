package com.scanrelay.app.util

import com.scanrelay.app.data.model.UpiPaymentData
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object UpiUriParser {

    sealed class ParseResult {
        data class Success(val data: UpiPaymentData) : ParseResult()
        data class Invalid(val reason: String) : ParseResult()
    }

    /**
     * Validates whether a raw string strictly conforms to a UPI payment URI:
     * - Scheme: "upi" (case-insensitive)
     * - Authority / Host: strictly "pay" (no subdomains, ports, or userInfo)
     * - Path: empty or "/"
     * - Required query param: "pa" (payee VPA)
     */
    fun isValidUpiUri(raw: String?): Boolean {
        return parse(raw) is ParseResult.Success
    }

    /**
     * Parses and extracts UPI parameters from a raw QR code string.
     */
    fun parse(raw: String?): ParseResult {
        if (raw.isNullOrBlank()) {
            return ParseResult.Invalid("Empty QR code content")
        }

        val trimmed = raw.trim()

        // Fast-path prefix check (case-insensitive)
        val lower = trimmed.lowercase(Locale.ROOT)
        if (!lower.startsWith("upi://pay")) {
            return ParseResult.Invalid("Not a UPI payment URI (must start with upi://pay)")
        }

        val uri: URI = try {
            URI.create(trimmed)
        } catch (e: Exception) {
            return ParseResult.Invalid("Malformed URI structure: ${e.message}")
        }

        // Scheme verification: must be strictly "upi"
        val scheme = uri.scheme
        if (scheme == null || !scheme.equals("upi", ignoreCase = true)) {
            return ParseResult.Invalid("Invalid scheme: '$scheme' (expected 'upi')")
        }

        // Authority verification: must strictly be "pay"
        // This blocks evil payloads like:
        // - upi://pay.evil.com
        // - upi://attacker.com/pay
        // - upi://pay@attacker.com
        // - upi://pay:8080
        val host = uri.host
        val authority = uri.authority

        if (authority == null) {
            return ParseResult.Invalid("Missing authority in URI")
        }

        if (!authority.equals("pay", ignoreCase = true)) {
            return ParseResult.Invalid("Forbidden authority '$authority'. Host must strictly be 'pay'.")
        }

        if (host != null && !host.equals("pay", ignoreCase = true)) {
            return ParseResult.Invalid("Forbidden host '$host'")
        }

        if (uri.userInfo != null) {
            return ParseResult.Invalid("URI contains forbidden user info")
        }

        if (uri.port != -1) {
            return ParseResult.Invalid("URI contains forbidden port specification")
        }

        // Path should be empty or "/"
        val path = uri.path
        if (path != null && path.isNotEmpty() && path != "/") {
            return ParseResult.Invalid("Forbidden URI path: '$path'")
        }

        // Parse query string
        val rawQuery = uri.rawQuery
        if (rawQuery.isNullOrBlank()) {
            return ParseResult.Invalid("Missing query parameters in UPI URI")
        }

        val queryParams = parseQueryParameters(rawQuery)

        // "pa" (payee address) is required
        val pa = queryParams["pa"]?.trim()
        if (pa.isNullOrBlank()) {
            return ParseResult.Invalid("Missing required 'pa' (Payee Address) parameter")
        }

        val pn = queryParams["pn"]?.trim()
        val am = queryParams["am"]?.trim()
        val cu = queryParams["cu"]?.trim() ?: "INR"
        val tn = queryParams["tn"]?.trim()
        val tr = queryParams["tr"]?.trim()

        return ParseResult.Success(
            UpiPaymentData(
                payeeVpa = pa,
                payeeName = if (pn.isNullOrEmpty()) null else pn,
                amount = if (am.isNullOrEmpty()) null else am,
                currency = cu,
                note = if (tn.isNullOrEmpty()) null else tn,
                refId = if (tr.isNullOrEmpty()) null else tr,
                rawUri = trimmed
            )
        )
    }

    private fun parseQueryParameters(rawQuery: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val pairs = rawQuery.split("&")
        for (pair in pairs) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = decode(pair.substring(0, idx)).lowercase(Locale.ROOT)
                val value = if (idx < pair.length - 1) {
                    decode(pair.substring(idx + 1))
                } else {
                    ""
                }
                params[key] = value
            } else if (idx < 0 && pair.isNotEmpty()) {
                val key = decode(pair).lowercase(Locale.ROOT)
                params[key] = ""
            }
        }
        return params
    }

    private fun decode(value: String): String {
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            value
        }
    }
}
