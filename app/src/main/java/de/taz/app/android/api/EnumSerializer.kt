package de.taz.app.android.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


abstract class EnumSerializer<T : Enum<T>>(
    values: Array<out T>,
    private val defaultValue: T
) : KSerializer<T> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            this::class.qualifiedName ?: "${this::class}AnonymousEnumSerializer",
            PrimitiveKind.STRING
        )

    // Build maps for faster parsing, used @SerialName annotation if present, fall back to name
    private val lookup = values.associateBy({ it }, { it.serialName })
    private val revLookup = values.associateBy { it.serialName }

    private val Enum<T>.serialName: String
        get() = this::class.java.getField(name).getAnnotation(SerialName::class.java)?.value ?: name

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(lookup.getValue(value))
    }

    override fun deserialize(decoder: Decoder): T {
        return revLookup[decoder.decodeString()] ?: defaultValue
    }
}
