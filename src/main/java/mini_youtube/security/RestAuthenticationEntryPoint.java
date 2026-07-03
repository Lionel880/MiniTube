package mini_youtube.security;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import mini_youtube.dto.Response.ApiErrorResponse;

/**
 * 未登入（或 token 無效/過期）就存取需要登入的 API 時，
 * 統一回傳跟 GlobalExceptionHandler 一致格式的 JSON，而不是 Spring Security 預設的空白 403/401。
 * 這是為了避免像先前遇到的「Postman 回傳空白 403，不知道原因」的情況。
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "請先登入後再操作",
                request.getRequestURI(),
                LocalDateTime.now());

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
