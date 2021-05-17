package de.uzl.itcr.termicron.ingest

import java.net.http.HttpRequest

fun HttpRequest.Builder.addAcceptHeader(): HttpRequest.Builder = this.header("Accept", "application/json")
fun HttpRequest.Builder.addPayloadHeader(): HttpRequest.Builder = this.header("Content-Type", "application/json")