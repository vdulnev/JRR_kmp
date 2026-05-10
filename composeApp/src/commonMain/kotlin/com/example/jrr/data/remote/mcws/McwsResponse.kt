package com.example.jrr.data.remote.mcws

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
@XmlSerialName("Response", "", "")
data class McwsResponse(
    @XmlSerialName("Status", "", "")
    val status: String,
    val items: List<McwsItem> = emptyList()
) {
    fun toMap(): Map<String, String> = items.associate { it.name to it.value }
}

@Serializable
@XmlSerialName("Item", "", "")
data class McwsItem(
    @XmlSerialName("Name", "", "")
    val name: String,
    @XmlValue(true)
    val value: String = ""
)
