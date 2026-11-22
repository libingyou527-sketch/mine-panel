// mine panel 本地 GGUF 推理桥（JNI）。
// 底层 Context 创建、Token 循环生成、KV Cache 清理逻辑移植自 llama.cpp 官方
// examples/llama.android/lib/src/main/cpp/ai_chat.cpp，避免 API 废弃导致编译失败。
// JNI 层只对外暴露三个方法：init(modelPath) / generate(prompt, maxTokens) / release()。
// Prompt 的 Qwen2.5 ChatML 由 Kotlin 侧拼好后整体传入，底层不再二次套模板。

#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <unistd.h>

#include <sampling.h>
#include "logging.h"
#include "chat.h"
#include "common.h"
#include "llama.h"

namespace {

constexpr int   N_THREADS_MIN        = 2;
constexpr int   N_THREADS_MAX        = 4;
constexpr int   N_THREADS_HEADROOM   = 2;
// 0.5B + 精简输入 + 最多 128 输出，上下文取 2048 以控制驻留内存，降低 OOM 风险。
constexpr int   DEFAULT_CONTEXT_SIZE = 2048;
constexpr int   OVERFLOW_HEADROOM     = 4;
constexpr int   BATCH_SIZE            = 512;
constexpr float DEFAULT_SAMPLER_TEMP  = 0.3f;

// 一次推理所持有的全部原生资源；handle 即指向该结构的指针。
struct EngineContext {
    llama_model            *model     = nullptr;
    llama_context          *context   = nullptr;
    common_batch            batch;
    common_chat_templates_ptr chat_templates;
    common_sampler         *sampler    = nullptr;
};

static bool g_backend_inited = false;

// 照搬官方 init_context：线程数自适应，上下文参数取自官方默认值。
llama_context *init_context(llama_model *model, int n_ctx) {
    if (!model) {
        LOGe("%s: model cannot be null", __func__);
        return nullptr;
    }

    const int n_threads = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX,
                            (int) sysconf(_SC_NPROCESSORS_ONLN) - N_THREADS_HEADROOM));
    LOGi("%s: Using %d threads", __func__, n_threads);

    llama_context_params ctx_params = llama_context_default_params();
    const int trained_context_size = llama_model_n_ctx_train(model);
    if (n_ctx > trained_context_size) {
        LOGw("%s: Model trained with only %d context; enforcing %d",
             __func__, trained_context_size, n_ctx);
    }
    ctx_params.n_ctx         = n_ctx;
    ctx_params.n_batch       = BATCH_SIZE;
    ctx_params.n_ubatch      = BATCH_SIZE;
    ctx_params.n_threads     = n_threads;
    ctx_params.n_threads_batch = n_threads;

    llama_context *context = llama_init_from_model(model, ctx_params);
    if (context == nullptr) {
        LOGe("%s: llama_init_from_model() returned null", __func__);
    }
    return context;
}

common_sampler *new_sampler(llama_model *model, float temp) {
    common_params_sampling sparams;
    sparams.temp = temp;
    return common_sampler_init(model, sparams);
}

// 照搬官方 UTF-8 校验：分片缓存到凑成合法 UTF-8 再输出，避免截断多字节字符。
bool is_valid_utf8(const char *string) {
    if (!string) return true;
    const auto *bytes = (const unsigned char *) string;
    int num;
    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00)            { num = 1; }
        else if ((*bytes & 0xE0) == 0xC0)      { num = 2; }
        else if ((*bytes & 0xF0) == 0xE0)      { num = 3; }
        else if ((*bytes & 0xF8) == 0xF0)      { num = 4; }
        else                                    { return false; }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) return false;
            bytes += 1;
        }
    }
    return true;
}

// 照搬官方 decode_tokens_in_batches：分批解码 prompt，最后一条需要输出 logits。
int decode_prompt(EngineContext *ctx, const llama_tokens &tokens, bool compute_last_logit) {
    for (int i = 0; i < (int) tokens.size(); i += BATCH_SIZE) {
        const int cur = std::min((int) tokens.size() - i, BATCH_SIZE);
        ctx->batch.clear();
        for (int j = 0; j < cur; j++) {
            const llama_token token_id = tokens[i + j];
            const llama_pos position   = i + j;
            const bool want_logit = compute_last_logit && (i + j == (int) tokens.size() - 1);
            ctx->batch.add(token_id, position, 0, want_logit);
        }
        if (llama_process(ctx->context, LLAMA_PROCESS_TYPE_DECODE, ctx->batch.get()) != 0) {
            LOGe("%s: llama_decode failed w/ prompt batch", __func__);
            return 1;
        }
    }
    return 0;
}

void release_context(EngineContext *ctx) {
    if (!ctx) return;
    if (ctx->sampler)   { common_sampler_free(ctx->sampler); ctx->sampler = nullptr; }
    ctx->chat_templates.reset();
    ctx->batch = common_batch();
    if (ctx->context)  { llama_free(ctx->context);  ctx->context  = nullptr; }
    if (ctx->model)    { llama_model_free(ctx->model); ctx->model  = nullptr; }
    delete ctx;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_app_minepanel_model_GgufInferenceEngine_nativeInit(
        JNIEnv *env, jobject /*thiz*/, jstring jmodel_path) {
    if (!g_backend_inited) {
        llama_log_set(mp_android_log_callback, nullptr);
        llama_backend_init();
        g_backend_inited = true;
    }

    const char *model_path = env->GetStringUTFChars(jmodel_path, nullptr);
    LOGi("%s: Loading model: %s", __func__, model_path);

    llama_model_params model_params = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    if (!model) {
        LOGe("%s: model load failed", __func__);
        return 0;
    }

    auto *ctx = new EngineContext();
    ctx->model = model;
    ctx->context = init_context(model, DEFAULT_CONTEXT_SIZE);
    if (!ctx->context) { release_context(ctx); return 0; }

    ctx->batch = common_batch(ctx->context);
    ctx->chat_templates = common_chat_templates_init(model, "");
    ctx->sampler = new_sampler(model, DEFAULT_SAMPLER_TEMP);
    if (!ctx->sampler) { release_context(ctx); return 0; }

    LOGi("%s: Model ready", __func__);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_app_minepanel_model_GgufInferenceEngine_nativeGenerate(
        JNIEnv *env, jobject /*thiz*/, jlong handle, jstring jprompt, jint max_tokens) {
    auto *ctx = reinterpret_cast<EngineContext *>(handle);
    if (!ctx || !ctx->context || !ctx->model) {
        return env->NewStringUTF("");
    }

    // 一次性推理：清掉上一轮 KV，重置采样器状态。
    llama_memory_clear(llama_get_memory(ctx->context), false);
    common_sampler_reset(ctx->sampler);

    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt_text(prompt ? prompt : "");
    env->ReleaseStringUTFChars(jprompt, prompt);

    // Kotlin 已拼好 ChatML；parse_special=true 以识别 <|im_start|>/<|im_end|> 特殊标记。
    auto tokens = common_tokenize(ctx->context, prompt_text, false, true);
    const int max_batch_size = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
    if ((int) tokens.size() > max_batch_size) {
        LOGw("%s: prompt too long, truncating %d tokens", __func__,
             (int) tokens.size() - max_batch_size);
        tokens.resize(max_batch_size);
    }

    if (decode_prompt(ctx, tokens, true) != 0) {
        return env->NewStringUTF("");
    }

    llama_pos current_position = (llama_pos) tokens.size();
    std::ostringstream assistant_ss;
    std::string cached_token_chars;
    int predicted = 0;

    while (predicted < max_tokens) {
        const llama_token new_token_id = common_sampler_sample(ctx->sampler, ctx->context, -1);
        common_sampler_accept(ctx->sampler, new_token_id, true);

        ctx->batch.clear();
        ctx->batch.add(new_token_id, current_position, 0, true);
        if (llama_process(ctx->context, LLAMA_PROCESS_TYPE_DECODE, ctx->batch.get()) != 0) {
            LOGe("%s: decode failed for generated token", __func__);
            break;
        }
        current_position++;

        if (llama_vocab_is_eog(llama_model_get_vocab(ctx->model), new_token_id)) {
            LOGd("%s: EOG, stop generation", __func__);
            break;
        }

        auto new_token_chars = common_token_to_piece(ctx->context, new_token_id);
        cached_token_chars += new_token_chars;
        if (is_valid_utf8(cached_token_chars.c_str())) {
            assistant_ss << cached_token_chars;
            cached_token_chars.clear();
        }
        predicted++;
    }

    return env->NewStringUTF(assistant_ss.str().c_str());
}

JNIEXPORT void JNICALL
Java_app_minepanel_model_GgufInferenceEngine_nativeRelease(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    auto *ctx = reinterpret_cast<EngineContext *>(handle);
    if (!ctx) return;
    LOGi("%s: releasing native context", __func__);
    release_context(ctx);
}

} // extern "C"
