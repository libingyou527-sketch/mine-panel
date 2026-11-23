package app.minepanel.model

/**
 * 模型输出未通过输出 schema / 人读校验。
 * 属可预期的降级情形（弱模型复述占位、格式不合规等），上层应回退模板并记为模板降级，
 * 不与 OOM、原生崩溃等真正的运行错误混为一类。
 */
class SchemaValidationException(message: String) : RuntimeException(message)
