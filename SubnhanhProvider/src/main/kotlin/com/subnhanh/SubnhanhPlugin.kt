
package com.subnhanh

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.subnhanh.SubNhanhProvider

@CloudstreamPlugin
class SubnhanhPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(SubNhanhProvider())
    }
}