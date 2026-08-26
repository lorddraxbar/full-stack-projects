package com.secphils.config;

import com.secphils.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // public
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/landing").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/landing/contact").permitAll()
                .requestMatchers("/api/v1/reviews/public").permitAll()
                // swagger + actuator
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // admin-only
                .requestMatchers("/api/v1/admin/**", "/api/v1/roles/**", "/api/v1/permissions/**", "/api/v1/settings/**").hasRole("ADMIN")
                // user management is admin-only (prevents a USER from creating/escalating admins)
                .requestMatchers(HttpMethod.POST, "/api/v1/users/set-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/me").authenticated()
                .requestMatchers("/api/v1/users/me/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/**", "/api/v1/users").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/companies").hasAnyRole("ADMIN", "USER")
                .requestMatchers("/api/v1/companies/me/**").authenticated()
                // browsing another company's client team is staff-only; clients use /companies/me/team
                .requestMatchers(HttpMethod.GET, "/api/v1/companies/*/team").hasAnyRole("ADMIN", "USER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/companies/**").hasAnyRole("ADMIN", "USER")
                .requestMatchers(HttpMethod.POST, "/api/v1/services", "/api/v1/dropdowns/**").hasAnyRole("ADMIN", "USER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/services/**", "/api/v1/dropdowns/**").hasAnyRole("ADMIN", "USER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/services/**", "/api/v1/dropdowns/**").hasAnyRole("ADMIN", "USER")
                // announcements: reads need any auth (role scoping enforced in the controller);
                // writes are staff-only — clients can read but never create/edit/delete
                .requestMatchers(HttpMethod.POST, "/api/v1/announcements").hasAnyRole("ADMIN", "USER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/announcements/**").hasAnyRole("ADMIN", "USER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/announcements/**").hasAnyRole("ADMIN", "USER")
                // service category management is admin-only (categories drive the public landing tabs)
                .requestMatchers(HttpMethod.POST, "/api/v1/service-categories").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/service-categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/service-categories/**").hasRole("ADMIN")
                // everything else requires authentication
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Insufficient permissions\"}");
                })
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(
                "http://localhost:3000", "http://localhost:5173",
                "http://10.0.1.78:3000", "http://192.168.1.11:3000"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
