package app.minepanel.model

import app.minepanel.core.model.EventCategory
import app.minepanel.core.model.ModelStatus
import app.minepanel.core.model.label

/**
 * 模板降级引擎：模型缺失、校验失败、超时或 OOM 时使用。
 * 只依据结论性数据生成中性回顾，不产出分值、不做诊断，也不提及具体应用。
 */
class TemplateFallbackEngine : InferenceEngine {

    override val engineName: String = "template"

    /** 聊天兜底：内置模型不可用时给出中性提示，不编造回答。 */
    override suspend fun chat(prompt: String): String =
        "内置模型暂不可用，这次先不展开回复，稍后再试。"

    override suspend fun infer(input: ModelInput): ModelOutput {
        val dominantCategory = input.categoryMinutes.maxByOrNull { it.value }?.key
        val categoryLabel = EventCategory.fromName(dominantCategory).label
        val dayText = if (input.sampleDays > 0) "近 ${input.sampleDays} 天" else "近期"
        val scopeText = input.periodLabel ?: dayText
        val timePhrase = screenTimePhrase(input.totalScreenTimeMin)
        val focusText = if (categoryLabel == "其他") "活动类别暂未细分" else "以${categoryLabel}类活动为主"
        val emptySample = input.sampleDays <= 0 && input.totalScreenTimeMin <= 0

        val narrative = if (emptySample) {
            "近期记录较少，暂不足以形成回顾。"
        } else {
            "${scopeText}${timePhrase}，$focusText。"
        }
        val tags = listOfNotNull(categoryLabel.takeIf { it.isNotBlank() }, "本地记录")
        val memories = if (emptySample) {
            listOf("可继续记录使用情况，稍后再生成回顾。")
        } else {
            listOf("本周期$focusText，可在面板查看六维参考分值。")
        }

        return ModelOutput(
            panelNarrative = narrative,
            statusTags = tags,
            draftMemories = memories,
            status = ModelStatus.FALLBACK_TEMPLATE,
            engineName = engineName
        )
    }

    private fun screenTimePhrase(totalMin: Int): String {
        if (totalMin <= 0) return "几乎没有屏幕使用"
        val hours = totalMin / 60
        val mins = totalMin % 60
        val amount = if (hours > 0) "${hours} 小时 ${mins} 分" else "${mins} 分钟"
        return "屏幕使用约 $amount"
    }
}
