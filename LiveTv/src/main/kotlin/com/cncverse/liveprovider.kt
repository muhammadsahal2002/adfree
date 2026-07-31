package com.phisher98.cloudplay

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class SimpleLivePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(SimpleLiveProvider())
    }
}