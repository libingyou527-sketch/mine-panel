package app.minepanel.model

/** 把校验通过的输出压缩成单行 JSON，留存到运行日志；不存原始事件。 */
object ModelOutputJson {

    fun toJson(output: ModelOutput): String {
        val tags = output.statusTags.joinToString(",") { "\"${escape(it)}\"" }
        val memories = output.draftMemories.joinToString(",") { "\"${escape(it)}\"" }
        return "{\"panelNarrative\":\"${escape(output.panelNarrative)}\"," +
            "\"statusTags\":[$tags],\"draftMemories\":[$memories]}"
    }

    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
        .replace("\r", " ")
}
