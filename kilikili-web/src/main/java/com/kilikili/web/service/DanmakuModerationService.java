package com.kilikili.web.service;

import com.kilikili.common.exception.BusinessException;
import com.kilikili.web.ai.DeepSeekClient;
import com.kilikili.web.entity.SensitiveWord;
import com.kilikili.web.mapper.SensitiveWordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * 弹幕关键词过滤：先查本地敏感词库（归一化后匹配，忽略全半角、大小写、空格与标点），
 * 未命中且配置了 DeepSeek 时再做一次 AI 语义审核。
 * 本地命中 BLOCK / AI 判定 REJECT 直接拦截；REVIEW 放行但记录复核日志。
 */
@Service
public class DanmakuModerationService {
    private static final Logger log = LoggerFactory.getLogger(DanmakuModerationService.class);
    private static final long CACHE_TTL_MS = 60_000L;

    private final SensitiveWordMapper wordMapper;
    private final DeepSeekClient deepSeekClient;
    private final boolean aiEnabled;

    private volatile List<SensitiveWord> cache = List.of();
    private volatile long cacheLoadedAt = 0L;

    public DanmakuModerationService(SensitiveWordMapper wordMapper, DeepSeekClient deepSeekClient,
                                    @Value("${kilikili.ai.danmaku-moderation-enabled:true}") boolean aiEnabled) {
        this.wordMapper = wordMapper;
        this.deepSeekClient = deepSeekClient;
        this.aiEnabled = aiEnabled;
    }

    public void check(long userId, long videoId, String content) {
        String normalized = normalize(content);
        if (normalized.isEmpty()) throw new BusinessException("弹幕内容不能为空");

        for (SensitiveWord word : words()) {
            String target = normalize(word.word());
            if (target.isEmpty() || !normalized.contains(target)) continue;
            if ("REVIEW".equalsIgnoreCase(word.action())) {
                log.warn("弹幕命中复核关键词，已放行待人工复核: userId={}, videoId={}, word={}, content={}",
                        userId, videoId, word.word(), content);
                continue;
            }
            log.warn("弹幕被本地词库拦截: userId={}, videoId={}, word={}, content={}",
                    userId, videoId, word.word(), content);
            throw new BusinessException("弹幕包含违规关键词「" + word.word() + "」，请修改后再发送");
        }

        if (!aiEnabled || !deepSeekClient.available()) return;
        try {
            DeepSeekClient.Verdict verdict = deepSeekClient.moderateDanmaku(content);
            if ("REJECT".equals(verdict.result())) {
                log.warn("弹幕被 AI 审核拒绝: userId={}, videoId={}, score={}, reason={}, content={}",
                        userId, videoId, verdict.score(), verdict.reason(), content);
                throw new BusinessException("弹幕未通过智能审核：" + (verdict.reason().isBlank() ? "内容存在风险" : verdict.reason()));
            }
            if ("REVIEW".equals(verdict.result())) {
                log.warn("弹幕 AI 审核标记复核，已放行: userId={}, videoId={}, score={}, reason={}, content={}",
                        userId, videoId, verdict.score(), verdict.reason(), content);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            // AI 不可用或超时不阻塞用户发送，避免弹幕功能整体不可用
            log.warn("弹幕智能审核调用失败，已放行: userId={}, videoId={}, error={}",
                    userId, videoId, exception.getMessage());
        }
    }

    private List<SensitiveWord> words() {
        long now = System.currentTimeMillis();
        if (now - cacheLoadedAt > CACHE_TTL_MS) {
            try {
                cache = wordMapper.listEnabled();
                cacheLoadedAt = now;
            } catch (Exception exception) {
                log.warn("敏感词库加载失败，使用旧缓存: {}", exception.getMessage());
            }
        }
        return cache;
    }

    /** NFKC 归一化 + 小写 + 去掉空白和标点，抵御「加 微 信」「加V信」这类变形 */
    private static String normalize(String value) {
        if (value == null) return "";
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (Character.isLetterOrDigit(ch)) builder.append(ch);
        }
        return builder.toString();
    }
}
