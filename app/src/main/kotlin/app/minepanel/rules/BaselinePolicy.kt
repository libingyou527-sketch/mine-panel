package app.minepanel.rules

/** 前 7 天为基线期：只积累数据，面板显示“数据积累中”，不急着下结论。 */
object BaselinePolicy {
    const val BASELINE_DAYS = 7

    fun isBaseline(sampleDays: Int): Boolean = sampleDays < BASELINE_DAYS
}
