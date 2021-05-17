package de.uzl.itcr.termicron.authentication

import java.nio.charset.StandardCharsets
import java.util.*

class MdrCredentialBasicAuth
/**
 * constructor for a basic authentication credential
 * @param username the username
 * @param password the password
 */(
    /**
     * the username for this credential
     */
    private val username: String,
    /**
     * the password for this credential
     */
    private val password: String
) : MdrCredential {

    /**
     * basic auth credentials never expire
     *
     * @return always false
     */
    override fun isExpired(): Boolean {
        return false
    }

    /**
     * encode the credential to a header in the form base64("username:password")
     * @return the encoded credential header
     */
    override fun encodeCredentialToAuthorizationHeader(): String {
        return "Basic " + Base64.getEncoder().encodeToString(
            String.format("%s:%s", username, password).toByteArray(
                StandardCharsets.UTF_8
            )
        )
    }
}