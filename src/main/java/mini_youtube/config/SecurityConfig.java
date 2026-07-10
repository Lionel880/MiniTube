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
                // 1. 允許前端靜態資源直接存取
                .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**", "/static/**").permitAll()
                // 2. 允許 Vue 頁面路徑直接存取（避免重新整理網頁時報 401）
                .requestMatchers("/login", "/register", "/upload", "/search", "/videos/**").permitAll()
                
                // 3. API 認證規則
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers(
                    "/api/hello",
                    "/api/auth/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/videos", "/api/videos/search").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/videos/**").permitAll()
                
                // 4. 所有其他 /api/ 開頭的後端請求皆需要驗證
                .requestMatchers("/api/**").authenticated()
                // 5. 其餘非 API 的請求皆允許（如其他前端路由）
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
