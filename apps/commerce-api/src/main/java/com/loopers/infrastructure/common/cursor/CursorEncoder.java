package com.loopers.infrastructure.common.cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class CursorEncoder {

    private final ObjectMapper objectMapper;

    public String encode(Object cursor) {
        try {
            String json = objectMapper.writeValueAsString(cursor);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "커서 인코딩 중 오류가 발생했습니다.");
        }
    }

    public <T> T decode(String encoded, Class<T> type) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 커서입니다.");
        }
    }
}