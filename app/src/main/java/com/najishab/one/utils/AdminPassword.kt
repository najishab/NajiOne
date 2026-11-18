package com.najishab.one.utils

import com.najishab.one.models.CloudAccount
import java.security.SecureRandom

/**
 * The password the legacy panel binding (`ADMIN_PASSWORD`) is deployed with.
 *
 * It used to default to `admin`, and a worker whose panel answered to `admin` was taken over by the
 * bots that sweep workers.dev for default panels -- they made users and ran their own traffic through
 * the account, which is how an abuse report could arrive minutes after a deploy with nobody using it.
 * Now an empty or default password is replaced by a random one and stored on the account, so the
 * app's own legacy screen keeps working and nobody else can guess it.
 */
object AdminPassword {
    private val random = SecureRandom()
    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun ensure(account: CloudAccount): String {
        val current = account.najiAdminPassword
        if (!current.isNullOrEmpty() && current != "admin") return current
        val fresh = (1..24).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        account.najiAdminPassword = fresh
        return fresh
    }
}
