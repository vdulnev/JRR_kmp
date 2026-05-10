package com.example.jrr.data.remote.lookup

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("Response", "", "")
data class LookupResponse(
    val ip: String? = null,
    val port: String? = null,
    val localiplist: String? = null,
    val status: String? = null
)
