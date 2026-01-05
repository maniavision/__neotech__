package com.neovation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.frontend.url}")
    String frontendUrl;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users/me").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/payments/confirm-session").permitAll()
                        .requestMatchers("/api/requests/{requestId}/reviews/**").authenticated()
                        .requestMatchers("/api/requests/{requestId}/notes/**").hasAnyRole("ADMIN", "STAFF", "MANAGER")
//                        .requestMatchers(HttpMethod.GET, "/api/requests/user/**").hasAnyRole("ADMIN", "STAFF", "MANAGER")
                        // Only ADMIN, STAFF, or MANAGER can add attachments to any request
//                        .requestMatchers(HttpMethod.POST, "/api/requests/{id}/attachments").hasAnyRole("ADMIN", "STAFF", "MANAGER")
                        // Allow anyone to create a new request
                        .requestMatchers(HttpMethod.POST, "/api/requests").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/requests/**").authenticated()
                        .requestMatchers("/api/countries/**").permitAll()
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "STAFF", "MANAGER")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // *** VERY IMPORTANT: Verify this matches your Angular origin ***
        configuration.setAllowedOrigins(List.of("http://localhost:4200", "http://localhost:53808", "https://www.neovation.net", "https://neovation.net", frontendUrl));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")); // Ensure GET, OPTIONS
        // *** VERY IMPORTANT: Ensure Authorization is allowed (or use *) ***
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type")); // Example: Explicitly list OR use List.of("*")
        // *** VERY IMPORTANT: Ensure this is true ***
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // *** Verify this pattern covers /api/v1/users/me ***
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

