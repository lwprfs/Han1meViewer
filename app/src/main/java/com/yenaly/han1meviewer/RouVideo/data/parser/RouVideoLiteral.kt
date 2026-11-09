package com.yenaly.han1meviewer.RouVideo.data.parser

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object RouVideoLiteral {

    private val LOADER_START = Regex("""l:\${'$'}R\[\d+]=\{""")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun loaderObject(html: String): JsonObject {
        val match = LOADER_START.find(html)
            ?: error("Page data not found (the site layout may have changed)")
        val value = Reader(html, match.range.last).readValue()
        return value as? JsonObject ?: error("Page data is not an object")
    }

    fun decodeEv(d: String, k: Int): String {
        val bytes = try {
            Base64.decode(d, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            Base64.decode(d, Base64.URL_SAFE)
        }
        val sb = StringBuilder(bytes.size)
        for (b in bytes) sb.append(((b.toInt() and 0xFF) - k).toChar())
        return sb.toString()
    }

    fun streamPathFromEv(ev: JsonObject?): String? {
        ev ?: return null
        val d = ev["d"]?.jsonPrimitive?.contentOrNull ?: return null
        val k = ev["k"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt() ?: return null
        return runCatching {
            json.parseToJsonElement(decodeEv(d, k)).jsonObject["videoUrl"]
                ?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private class Reader(private val s: String, private var i: Int) {

        fun readValue(): JsonElement {
            skipWs()
            if (i >= s.length) fail("unexpected end")
            val c = s[i]
            return when {
                c == '{' -> readObject()
                c == '[' -> readArray()
                c == '"' || c == '\'' -> JsonPrimitive(readString())
                c == '!' -> {
                    val flag = s.getOrNull(i + 1)
                    i += 2
                    JsonPrimitive(flag == '0')
                }
                c == '$' -> readRef()
                c == '-' || c == '+' || c == '.' || c.isDigit() -> readNumber()
                c == '(' -> {
                    i++
                    val inner = readValue()
                    skipWs()
                    expect(')')
                    inner
                }
                c.isLetter() || c == '_' -> readWord()
                else -> fail("unexpected '$c'")
            }
        }

        private fun readObject(): JsonObject {
            expect('{')
            val map = LinkedHashMap<String, JsonElement>()
            while (true) {
                skipWs()
                if (i >= s.length) fail("unterminated object")
                if (s[i] == '}') { i++; break }
                val key = readKey()
                skipWs()
                expect(':')
                map[key] = readValue()
                skipWs()
                if (i < s.length && s[i] == ',') i++
            }
            return JsonObject(map)
        }

        private fun readArray(): JsonArray {
            expect('[')
            val list = ArrayList<JsonElement>()
            while (true) {
                skipWs()
                if (i >= s.length) fail("unterminated array")
                if (s[i] == ']') { i++; break }
                if (s[i] == ',') { i++; list.add(JsonNull); continue }
                list.add(readValue())
                skipWs()
                if (i < s.length && s[i] == ',') i++
            }
            return JsonArray(list)
        }

        private fun readKey(): String {
            val c = s[i]
            if (c == '"' || c == '\'') return readString()
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '$')) i++
            if (i == start) fail("bad key")
            return s.substring(start, i)
        }

        private fun readString(): String {
            val quote = s[i++]
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i++]
                if (c == quote) break
                if (c != '\\') { sb.append(c); continue }
                if (i >= s.length) fail("bad escape")
                when (val e = s[i++]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'v' -> sb.append('\u000B')
                    '0' -> sb.append('\u0000')
                    'u' -> {
                        sb.append(s.substring(i, i + 4).toInt(16).toChar())
                        i += 4
                    }
                    'x' -> {
                        sb.append(s.substring(i, i + 2).toInt(16).toChar())
                        i += 2
                    }
                    '\n' -> Unit
                    '\r' -> if (s.getOrNull(i) == '\n') i++
                    else -> sb.append(e)
                }
            }
            return sb.toString()
        }

        private fun readNumber(): JsonElement {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val text = s.substring(start, i)
            if (s.getOrNull(i) == 'n') i++
            text.toLongOrNull()?.let { return JsonPrimitive(it) }
            val d = text.toDoubleOrNull() ?: fail("bad number '$text'")
            return JsonPrimitive(d)
        }

        private fun readRef(): JsonElement {
            if (!s.startsWith("\$R[", i)) fail("unexpected '$'")
            val close = s.indexOf(']', i)
            if (close < 0) fail("bad reference")
            i = close + 1
            skipWs()
            return if (s.getOrNull(i) == '=') {
                i++
                readValue()
            } else {
                JsonNull
            }
        }

        private fun readWord(): JsonElement {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '$')) i++
            return when (val word = s.substring(start, i)) {
                "null", "undefined", "NaN", "Infinity" -> JsonNull
                "true" -> JsonPrimitive(true)
                "false" -> JsonPrimitive(false)
                "void" -> {
                    skipWs()
                    readNumber()
                    JsonNull
                }
                "new" -> {
                    skipWs()
                    val ctor = StringBuilder()
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) ctor.append(s[i++])
                    skipWs()
                    expect('(')
                    val arg = readValue()
                    skipWs()
                    expect(')')
                    arg
                }
                else -> fail("unexpected word '$word'")
            }
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun expect(c: Char) {
            if (i >= s.length || s[i] != c) fail("expected '$c'")
            i++
        }

        private fun fail(message: String): Nothing =
            throw IllegalStateException("Page data parse error at $i: $message")
    }
}
