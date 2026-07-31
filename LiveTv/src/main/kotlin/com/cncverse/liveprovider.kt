package com.cncverse

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class LivePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(LiveProvider())
    }
}