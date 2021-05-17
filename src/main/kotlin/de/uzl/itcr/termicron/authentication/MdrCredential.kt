package de.uzl.itcr.termicron.authentication

interface MdrCredential {
    /**
     * check if the credential is expired
     * @return Boolean true if the credential is expired
     */
    fun isExpired(): Boolean

    /**
     * encode the credential into a value suitable for passing into the "Authorization" header of a HTTP request
     * @return String the encoded credential
     */
    fun encodeCredentialToAuthorizationHeader(): String?
}