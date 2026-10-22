package com.scanrelay.app.util

import com.scanrelay.app.data.model.UpiPaymentData
import java.util.Locale

data class RiskWarning(
    val title: String,
    val description: String,
    val isSevere: Boolean = false
)

object UpiRiskAnalyzer {

    fun analyze(data: UpiPaymentData): List<RiskWarning> {
        val warnings = mutableListOf<RiskWarning>()

        // 1. Missing payee name check
        if (data.payeeName.isNullOrBlank()) {
            warnings.add(
                RiskWarning(
                    title = "Payee Name Missing",
                    description = "This QR code does not declare a payee name. Confirm you recognize the UPI ID.",
                    isSevere = false
                )
            )
        }

        // 2. VPA format validation (<user>@<handle>)
        val vpa = data.payeeVpa.trim()
        val atCount = vpa.count { it == '@' }
        if (atCount != 1) {
            warnings.add(
                RiskWarning(
                    title = "Suspicious UPI ID Format",
                    description = "The UPI ID '$vpa' does not follow the standard user@bank format.",
                    isSevere = true
                )
            )
        } else {
            val parts = vpa.split("@")
            val user = parts[0]
            val handle = parts[1]

            if (user.isBlank() || handle.isBlank()) {
                warnings.add(
                    RiskWarning(
                        title = "Incomplete UPI ID",
                        description = "The UPI address is missing either username or bank handle.",
                        isSevere = true
                    )
                )
            } else if (!handle.matches(Regex("^[a-zA-Z0-9.-]+$"))) {
                warnings.add(
                    RiskWarning(
                        title = "Unusual Bank Handle",
                        description = "The bank handle '@$handle' contains unusual characters.",
                        isSevere = false
                    )
                )
            }

            // 3. Heuristic: Name vs VPA mismatch
            if (!data.payeeName.isNullOrBlank()) {
                val nameLower = data.payeeName.lowercase(Locale.ROOT)
                val userLower = user.lowercase(Locale.ROOT)

                // If payee name looks like an email or phone number while VPA is different
                if (nameLower.contains("@") && !nameLower.contains(userLower)) {
                    warnings.add(
                        RiskWarning(
                            title = "Name Format Warning",
                            description = "Payee name resembles an email address that does not match the UPI ID.",
                            isSevere = false
                        )
                    )
                }
            }
        }

        // 4. Check for unusual currency
        if (!data.currency.equals("INR", ignoreCase = true)) {
            warnings.add(
                RiskWarning(
                    title = "Non-INR Currency",
                    description = "Currency is '${data.currency}'. Standard Indian UPI uses INR.",
                    isSevere = true
                )
            )
        }

        return warnings
    }
}
