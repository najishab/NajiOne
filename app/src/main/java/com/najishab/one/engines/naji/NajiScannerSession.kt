package com.najishab.one.engines.naji

import com.najishab.one.models.CloudAccount

object NajiScannerSession {
    var activeAccount: CloudAccount? = null
    var activeUser: NajiUser? = null

    fun start(account: CloudAccount, user: NajiUser) {
        activeAccount = account
        activeUser = user
    }

    fun clear() {
        activeAccount = null
        activeUser = null
    }
}
