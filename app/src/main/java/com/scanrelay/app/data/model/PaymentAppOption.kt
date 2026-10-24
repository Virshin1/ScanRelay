package com.scanrelay.app.data.model

data class PaymentAppOption(
    val id: String,
    val displayName: String,
    val packageName: String?,
    val description: String
) {
    companion object {
        val ALL = listOf(
            PaymentAppOption(
                id = "AUTO",
                displayName = "Auto / System Default",
                packageName = null,
                description = "Opens default UPI app or system chooser"
            ),
            PaymentAppOption(
                id = "GPAY",
                displayName = "Google Pay",
                packageName = "com.google.android.apps.nbu.paisa.user",
                description = "Handles personal (P2P) & merchant QRs smoothly"
            ),
            PaymentAppOption(
                id = "PHONEPE",
                displayName = "PhonePe",
                packageName = "com.phonepe.app",
                description = "Direct PhonePe (Merchant QRs work directly; P2P can use copied UPI ID)"
            ),
            PaymentAppOption(
                id = "PAYTM",
                displayName = "Paytm",
                packageName = "net.one97.paytm",
                description = "Direct Paytm for merchant and personal payments"
            ),
            PaymentAppOption(
                id = "NAVI",
                displayName = "Navi UPI",
                packageName = "com.naviapp",
                description = "Direct Navi UPI launch"
            ),
            PaymentAppOption(
                id = "BHIM",
                displayName = "BHIM UPI",
                packageName = "in.org.npci.upiapp",
                description = "Official NPCI UPI app"
            ),
            PaymentAppOption(
                id = "CRED",
                displayName = "CRED UPI",
                packageName = "com.dreamplug.androidapp",
                description = "Direct CRED UPI launch"
            )
        )

        fun findByPackage(packageName: String?): PaymentAppOption {
            if (packageName.isNullOrBlank()) return ALL[0]
            return ALL.find { it.packageName == packageName } ?: ALL[0]
        }
    }
}
