package mini_youtube.config;

import java.util.List;

import lombok.RequiredArgsConstructor;
import mini_youtube.security.JwtAuthenticationFilter;
import mini_youtube.security.RestAccessDeniedHandler;
import mini_youtube.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 未登入或權限不足時，統一回傳 JSON 格式錯誤，方便前端與 Postman 判讀，
            // 不會再出現「不知道為什麼是空白 403」的情況。
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(restAuthenticationEntryPoint)
                .accessDeniedHandler(restAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // /api/auth/me 需要驗證身分，必須寫在 /api/auth/** 這條 permitAll 規則之前，
                // 因為 Spring Security 會採用第一個相符的規則。
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers(
                    "/hello",
                    "/api/auth/**"
                ).permitAll()
                // 影片跟著使用者走：列表與搜尋只回傳自己的影片，必須登入。
                .requestMatchers(HttpMethod.GET, "/api/videos", "/api/videos/search").authenticated()
                // 詳情與串流維持公開（<video> 標籤無法帶 JWT header，擋掉會讓播放器壞掉），
                // 拿到連結的人仍可觀看單支影片。
                .requestMatchers(HttpMethod.GET, "/api/videos/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "https://mini-tube*-lionel880s-projects.vercel.app",
            "https://lionel880.github.io"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
