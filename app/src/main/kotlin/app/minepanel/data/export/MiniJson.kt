package app.minepanel.data.export

/**
 * 精简 JSON 解析结果树。
 *
 * 项目不引入 JSON 库：导出 schema 固定且层级浅，手写递归下降解析即可，
 * 同时让解析逻辑在纯 JVM 单测中覆盖非法输入、转义与类型校验。
 */
sealed interface JsonValue {
    data class Obj(val map: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val raw: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    object Null : JsonValue

    fun asObject(): Obj? = this as? Obj
    fun asArray(): Arr? = this as? Arr
    fun asString(): String? = (this as? Str)?.value
    fun asLong(): Long? = (this as? Num)?.raw?.toLongOrNull()
    fun asDouble(): Double? = (this as? Num)?.raw?.toDoubleOrNull()
    fun asInt(): Int? = (this as? Num)?.raw?.toIntOrNull()
    fun asBoolean(): Boolean? = (this as? Bool)?.value
}

object MiniJson {

    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val value = parser.readValue()
        parser.skipWs()
        if (!parser.atEnd()) throw IllegalArgumentException("JSON 末尾有多余内容")
        return value
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun atEnd(): Boolean = index >= text.length

        fun skipWs() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun readValue(): JsonValue {
            skipWs()
            if (index >= text.length) throw IllegalArgumentException("JSON 意外结束")
            return when (text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonValue.Str(readString())
                't', 'f' -> readBool()
                'n' -> readNull()
                else -> readNumber()
            }
        }

        private fun readObject(): JsonValue.Obj {
            val map = mutableMapOf<String, JsonValue>()
            expect('{')
            skipWs()
            if (peek() == '}') { index++; return JsonValue.Obj(map) }
            while (true) {
                skipWs()
                val key = readString()
                skipWs()
                expect(':')
                map[key] = readValue()
                skipWs()
                when (nextChar()) {
                    ',' -> index++
                    '}' -> { index++; break }
                    else -> throw IllegalArgumentException("对象内缺少逗号或右括号")
                }
            }
            return JsonValue.Obj(map)
        }

        private fun readArray(): JsonValue.Arr {
            val items = mutableListOf<JsonValue>()
            expect('[')
            skipWs()
            if (peek() == ']') { index++; return JsonValue.Arr(items) }
            while (true) {
                items += readValue()
                skipWs()
                when (nextChar()) {
                    ',' -> index++
                    ']' -> { index++; break }
                    else -> throw IllegalArgumentException("数组内缺少逗号或右括号")
                }
            }
            return JsonValue.Arr(items)
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (index < text.length) {
                val c = text[index++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> sb.append(readEscape())
                    c.code < 0x20 -> throw IllegalArgumentException("字符串含非法控制字符")
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("字符串未闭合")
        }

        private fun readEscape(): Char = when (text[index++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'b' -> '\b'
            'f' -> '\u000C'
            'u' -> readUnicode()
            else -> throw IllegalArgumentException("非法转义序列")
        }

        private fun readUnicode(): Char {
            if (index + 4 > text.length) throw IllegalArgumentException("Unicode 转义不完整")
            val hex = text.substring(index, index + 4)
            index += 4
            return hex.toInt(16).toChar()
        }

        private fun readBool(): JsonValue.Bool {
            return when {
                text.startsWith("true", index) -> { index += 4; JsonValue.Bool(true) }
                text.startsWith("false", index) -> { index += 5; JsonValue.Bool(false) }
                else -> throw IllegalArgumentException("非法布尔值")
            }
        }

        private fun readNull(): JsonValue {
            if (text.startsWith("null", index)) { index += 4; return JsonValue.Null }
            throw IllegalArgumentException("非法字面量")
        }

        private fun readNumber(): JsonValue.Num {
            val start = index
            while (index < text.length && (text[index].isDigit() || text[index] in "-+.eE")) index++
            if (start == index) throw IllegalArgumentException("非法数值")
            return JsonValue.Num(text.substring(start, index))
        }

        private fun peek(): Char? = if (index < text.length) text[index] else null
        private fun nextChar(): Char = peek() ?: throw IllegalArgumentException("JSON 意外结束")

        private fun expect(c: Char) {
            if (index >= text.length || text[index] != c) throw IllegalArgumentException("期望 '$c'")
            index++
        }
    }
}
