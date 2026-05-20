package com.example.server.interceptor;

import com.example.server.entity.MediaFile;
import com.example.server.exception.TokenQuotaExceededException;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.service.TokenUsageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AiTokenQuotaInterceptor implements HandlerInterceptor {

    private final TokenUsageService tokenUsageService;
    private final MediaFileMapper mediaFileMapper;

    public AiTokenQuotaInterceptor(TokenUsageService tokenUsageService, MediaFileMapper mediaFileMapper) {
        this.tokenUsageService = tokenUsageService;
        this.mediaFileMapper = mediaFileMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = resolveUserId(request);
        if (!tokenUsageService.hasQuota(userId)) {
            throw new TokenQuotaExceededException("今日 AI 算力已耗尽");
        }
        request.setAttribute("aiUserId", userId);
        return true;
    }

    private Long resolveUserId(HttpServletRequest request) {
        Long userId = parseLong(request.getHeader("X-User-Id"));
        if (userId != null) {
            return userId;
        }

        userId = parseLong(request.getParameter("userId"));
        if (userId != null) {
            return userId;
        }

        Long mediaId = parseLong(request.getParameter("id"));
        if (mediaId == null) {
            return null;
        }

        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        return mediaFile == null ? null : mediaFile.getUserId();
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
