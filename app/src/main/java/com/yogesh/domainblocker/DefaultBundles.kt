package com.yogesh.domainblocker


object DefaultBundles {

    fun getBundles(): List<DomainBundle> {
        return listOf(
            DomainBundle(
                name = "Strict Ad Blocker",
                isEnabled = true,
                domains = listOf(
                    "ads.google.com",
                    "adservice.google.com",
                    "doubleclick.net",
                    "pagead2.googlesyndication.com",
                    "applovin.com",
                    "ads.yahoo.com",
                    "ads.facebook.com",
                    "googlesyndication.com",
                    "googleadservices.com",
                    "googletagservices.com",
                    "googletagmanager.com",
                    "adservice.google.com",
                    "adservice.google.co.in",
                    "pagead2.googlesyndication.com",
                    "googleads.g.doubleclick.net",
                    "securepubads.g.doubleclick.net",
                    "pubads.g.doubleclick.net",
                    "partner.googleadservices.com"
                )
            ),
            DomainBundle(
                name = "Social Media",
                isEnabled = false,
                domains = listOf(
                    "facebook.com",
                    "instagram.com",
                    "tiktok.com",
                    "twitter.com",
                    "x.com",
                    "snapchat.com",
                    "reddit.com"
                )
            ),
            DomainBundle(
                name = "Telemetry & Analytics",
                isEnabled = true,
                domains = listOf(
                    "app-measurement.com",
                    "crashlytics.com",
                    "telemetry.microsoft.com",
                    "vortex.data.microsoft.com",
                    "mixpanel.com"
                )
            )
        )
    }
}