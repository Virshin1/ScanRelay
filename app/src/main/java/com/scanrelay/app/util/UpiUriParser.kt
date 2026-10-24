package com.scanrelay.app.util

import com.scanrelay.app.data.model.UpiPaymentData
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object UpiUriParser {

    sealed class ParseResult {
        data class Success(val data: UpiPaymentData) : ParseResult()
        data class Invalid(val reason: String) : ParseResult()
    }

    data class EmvTag(val tag: String, val length: Int, val value: String)

    /**
     * Validates whether a raw string can be parsed into a valid UPI payment data.
     */
    fun isValidUpiUri(raw: String?): Boolean {
        return parse(raw) is ParseResult.Success
    }

    /**
     * Parses and extracts UPI parameters from a raw QR code string.
     * Supports:
     * 1. Standard UPI URI: upi://pay?... (and mixed-case / upi:pay?)
     * 2. BharatQR / EMVCo MPM QR format: starts with "000201" (used by Navi, Paytm, PhonePe, merchant QRs)
     * 3. Web-based UPI intent links: http(s)://... with pa=... parameter
     */
    fun parse(raw: String?): ParseResult {
        if (raw.isNullOrBlank()) {
            return ParseResult.Invalid("Empty QR code content")
        }

        val trimmed = raw.trim().removeSurrounding("\"").removeSurrounding("'")

        // 1. EMVCo / BharatQR Standard Format (starts with 000201)
        if (trimmed.startsWith("000201")) {
            val emvResult = parseBharatQr(trimmed)
            if (emvResult is ParseResult.Success) {
                return emvResult
            }
        }

        // 2. Reject HTTP(S) URLs for security (only upi://pay and BharatQR allowed)
        if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
            return ParseResult.Invalid("HTTP/HTTPS URLs are strictly rejected for security (only upi://pay and BharatQR accepted)")
        }

        // 3. Standard upi://pay URI
        return parseStandardUpiUri(trimmed)
    }

    /**
     * Parses BharatQR / EMVCo Merchant-Presented Mode (MPM) format (starting with 000201).
     * Extracts UPI virtual payment address (Tag 26/27 sub-tag 01 or with '@'), payee name (Tag 59),
     * amount (Tag 54), currency (Tag 53), transaction note/ref (Tag 62 sub-tags 05/08).
     */
    fun parseBharatQr(raw: String): ParseResult {
        try {
            val tags = parseEmvTlv(raw)
            var pa: String? = null
            var pn: String? = null
            var am: String? = null
            var cu: String = "INR"
            var tn: String? = null
            var tr: String? = null
            var mc: String? = null

            var mid: String? = null
            var tid: String? = null
            var sign: String? = null
            var url: String? = null

            for (tag in tags) {
                when (tag.tag) {
                    in "26".."51" -> {
                        val subTags = parseEmvTlv(tag.value)
                        for (sub in subTags) {
                            if (sub.value.startsWith("upi://pay", ignoreCase = true)) {
                                return parse(sub.value)
                            }
                            when (sub.tag) {
                                "01" -> pa = sub.value.trim()
                                "02" -> {
                                    val v = sub.value.trim()
                                    if (v.startsWith("http", ignoreCase = true)) url = v
                                    else if (v.length > 50) sign = v
                                    else mid = v
                                }
                                "03" -> mid = sub.value.trim()
                                "04" -> tid = sub.value.trim()
                                else -> {
                                    if (pa == null && sub.value.contains("@")) {
                                        pa = sub.value.trim()
                                    }
                                }
                            }
                        }
                    }
                    "52" -> mc = tag.value.trim()
                    "53" -> {
                        val currCode = tag.value.trim()
                        cu = if (currCode == "356") "INR" else currCode
                    }
                    "54" -> am = tag.value.trim()
                    "59" -> pn = tag.value.trim()
                    "62" -> {
                        val subTags = parseEmvTlv(tag.value)
                        for (sub in subTags) {
                            when (sub.tag) {
                                "03" -> if (mid == null) mid = sub.value.trim()
                                "05" -> tr = sub.value.trim()
                                "07" -> if (tid == null) tid = sub.value.trim()
                                "08" -> tn = sub.value.trim()
                            }
                        }
                    }
                    "64" -> sign = tag.value.trim()
                }
            }

            if (pa.isNullOrBlank() || !pa.contains("@")) {
                return ParseResult.Invalid("BharatQR missing valid payee VPA (pa)")
            }

            return buildUpiPaymentData(
                pa = pa,
                pn = pn,
                am = am,
                cu = cu,
                tn = tn,
                tr = tr,
                mc = mc,
                mid = mid,
                tid = tid,
                sign = sign,
                url = url
            )
        } catch (e: Exception) {
            return ParseResult.Invalid("Failed to parse BharatQR: ${e.message}")
        }
    }

    /**
     * Parses nested TLV blocks in EMVCo QR code string.
     */
    fun parseEmvTlv(content: String): List<EmvTag> {
        val list = mutableListOf<EmvTag>()
        var index = 0
        while (index + 4 <= content.length) {
            val tag = content.substring(index, index + 2)
            val lenStr = content.substring(index + 2, index + 4)
            val len = lenStr.toIntOrNull() ?: break
            index += 4
            if (index + len > content.length) break
            val value = content.substring(index, index + len)
            list.add(EmvTag(tag, len, value))
            index += len
        }
        return list
    }



    /**
     * Parses standard upi://pay URI strings with strict scheme and host validation.
     */
    private fun parseStandardUpiUri(raw: String): ParseResult {
        var normalized = raw.trim()
        if (normalized.startsWith("upi:pay?", ignoreCase = true)) {
            normalized = "upi://pay?" + normalized.substring("upi:pay?".length)
        }

        // Fast-path prefix check (case-insensitive)
        val lower = normalized.lowercase(Locale.ROOT)
        if (!lower.startsWith("upi://pay")) {
            return ParseResult.Invalid("Not a UPI payment URI (must start with upi://pay or BharatQR 000201)")
        }

        val qIdx = normalized.indexOf("?")
        val prefix = if (qIdx >= 0) normalized.substring(0, qIdx) else normalized
        val rawQuery = if (qIdx >= 0) normalized.substring(qIdx + 1) else ""

        val lowerPrefix = prefix.lowercase(Locale.ROOT)
        // Strict host check: blocks upi://pay.evil.com, upi://evil.com/pay, upi://pay:8080, etc.
        if (lowerPrefix != "upi://pay" && lowerPrefix != "upi://pay/") {
            return ParseResult.Invalid("Forbidden host or scheme. Must strictly be 'upi://pay'")
        }

        if (rawQuery.isBlank()) {
            return ParseResult.Invalid("Missing query parameters in UPI URI")
        }

        val queryParams = parseQueryParameters(rawQuery)

        // "pa" (payee address) is required
        val pa = queryParams["pa"]?.trim()
        if (pa.isNullOrBlank()) {
            return ParseResult.Invalid("Missing required 'pa' (Payee Address) parameter")
        }

        // Fix any unencoded spaces in the raw URI for valid Android Intent parsing
        val safeRawUri = if (normalized.contains(" ")) {
            normalized.replace(" ", "%20")
        } else {
            normalized
        }

        return ParseResult.Success(
            UpiPaymentData(
                payeeVpa = pa,
                payeeName = queryParams["pn"]?.trim()?.ifEmpty { null },
                amount = queryParams["am"]?.trim()?.ifEmpty { null },
                currency = queryParams["cu"]?.trim() ?: "INR",
                note = queryParams["tn"]?.trim()?.ifEmpty { null },
                refId = queryParams["tr"]?.trim()?.ifEmpty { null },
                rawUri = safeRawUri
            )
        )
    }

    private fun buildUpiPaymentData(
        pa: String,
        pn: String?,
        am: String?,
        cu: String,
        tn: String?,
        tr: String?,
        mc: String?,
        mode: String? = null,
        orgid: String? = null,
        sign: String? = null,
        mid: String? = null,
        tid: String? = null,
        url: String? = null
    ): ParseResult {
        // Construct canonical upi://pay URI with properly encoded parameters
        val qb = StringBuilder("pa=").append(pa)
        if (!pn.isNullOrBlank()) {
            qb.append("&pn=").append(URLEncoder.encode(pn, StandardCharsets.UTF_8.name()).replace("+", "%20"))
        }
        if (!mc.isNullOrBlank()) {
            qb.append("&mc=").append(mc)
        }
        if (!am.isNullOrBlank()) {
            qb.append("&am=").append(am)
        }
        qb.append("&cu=").append(cu)
        if (!tn.isNullOrBlank()) {
            qb.append("&tn=").append(URLEncoder.encode(tn, StandardCharsets.UTF_8.name()).replace("+", "%20"))
        }
        if (!tr.isNullOrBlank()) {
            qb.append("&tr=").append(tr)
        }
        if (!mode.isNullOrBlank()) {
            qb.append("&mode=").append(mode)
        }
        if (!orgid.isNullOrBlank()) {
            qb.append("&orgid=").append(orgid)
        }
        if (!sign.isNullOrBlank()) {
            qb.append("&sign=").append(sign)
        }
        if (!mid.isNullOrBlank()) {
            qb.append("&mid=").append(mid)
        }
        if (!tid.isNullOrBlank()) {
            qb.append("&tid=").append(tid)
        }
        if (!url.isNullOrBlank()) {
            qb.append("&url=").append(url)
        }

        val canonicalUri = "upi://pay?$qb"

        return ParseResult.Success(
            UpiPaymentData(
                payeeVpa = pa,
                payeeName = if (pn.isNullOrEmpty()) null else pn,
                amount = if (am.isNullOrEmpty()) null else am,
                currency = cu,
                note = if (tn.isNullOrEmpty()) null else tn,
                refId = if (tr.isNullOrEmpty()) null else tr,
                rawUri = canonicalUri
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
        } catch (_: Exception) {
            value
        }
    }
}
