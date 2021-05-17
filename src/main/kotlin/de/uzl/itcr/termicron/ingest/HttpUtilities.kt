package de.uzl.itcr.termicron.ingest

import java.net.http.HttpRequest

/**
 * extension method to a HttpRequest.Builder to add the Accept header with Json
 *
 * @return this HttpRequestBuilder
 */
fun HttpRequest.Builder.addAcceptHeader(): HttpRequest.Builder = this.header("Accept", "application/json")

/**
 * extension method to a HttpRequest.Builder to add the Content-Type header with Json
 *
 * @return this HttpRequestBuilder
 */
fun HttpRequest.Builder.addPayloadHeader(): HttpRequest.Builder = this.header("Content-Type", "application/json")