package com.scanrelay.app.data.model

data class UpiPaymentData(
    val payeeVpa: String,           // pa: Payee VPA / UPI ID (required)
    val payeeName: String?,         // pn: Payee Name
    val amount: String?,            // am: Transaction Amount
    val currency: String = "INR",   // cu: Currency (default INR)
    val note: String?,              // tn: Transaction Note
    val refId: String?,             // tr: Transaction Reference ID
    val rawUri: String              // Original raw URI
) {
    val displayAmount: String
        get() = if (!amount.isNullOrBlank()) "₹$amount" else "Amount not set"

    val displayName: String
        get() = if (!payeeName.isNullOrBlank()) payeeName else "Unknown Payee"
}
