package com.bu.paperclip.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToLong

/**
 * The server may send fractional values (e.g. cents as 75.4335444) where the models declare whole
 * numbers. These serializers accept ints and decimals (rounded) so one odd field never fails a whole response.
 * Registered for every model in Models.kt via @file:UseSerializers.
 */
private fun Decoder.lenientLong(): Long {
    if (this !is JsonDecoder) return decodeLong()
    val el = decodeJsonElement()
    if (el is JsonNull || el !is JsonPrimitive) return 0
    el.content.toLongOrNull()?.let { return it }
    val d = el.content.toDoubleOrNull() ?: return 0
    return if (d.isFinite()) d.roundToLong() else 0
}

object LenientLongSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long = decoder.lenientLong()
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

object LenientIntSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int = decoder.lenientLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}
