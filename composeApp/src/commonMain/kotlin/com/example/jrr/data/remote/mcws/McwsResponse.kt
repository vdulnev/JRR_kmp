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
    fun toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        items.forEach { 
            // Store with original case but we'll use a case-insensitive accessor or normalize
            map[it.name.lowercase()] = it.value 
        }
        return map
    }
}

@Serializable
@XmlSerialName("Item", "", "")
data class McwsItem(
    @XmlSerialName("Name", "", "")
    val name: String,
    @XmlValue(true)
    val value: String = ""
)
