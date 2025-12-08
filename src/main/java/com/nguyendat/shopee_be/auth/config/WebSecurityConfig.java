package com.nguyendat.shopee_be.auth.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nguyendat.shopee_be.auth.services.OAuth2Service;
import com.nguyendat.shopee_be.auth.services.OAuth2SuccessHandler;
import com.nguyendat.shopee_be.exceptions.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {
    @Autowired
    private OAuth2Service oAuth2Service;

    @Autowired
    private OAuth2SuccessHandler oAuth2SuccessHandler;


    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JWTTokenHelper jwtTokenHelper;

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000,https://ecommerce-fe-pink-one.vercel.app}")
    private String corsAllowedOrigins;

    private static final String[] publicApis = {
            "/api/auth/**",
            "/api/files/**",
            "/files/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests((authorize) -> authorize
            .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/products", "/api/category").permitAll()
            .requestMatchers("/api/orders/me").permitAll()
            .requestMatchers("/oauth2/success").permitAll()
            .requestMatchers("/api/upload/**", "/uploads/**").permitAll()
            .requestMatchers("/return").permitAll()
            .requestMatchers("/ws/**").permitAll()
            .requestMatchers("/api/dashboard/kpi").permitAll()
            .requestMatchers("/api/health").permitAll()
            .requestMatchers("/api/reviews/**").permitAll()
            .requestMatchers("/api/orders/unreviewed").permitAll()
            .requestMatchers("/api/user/profile").permitAll()
            .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/**").hasRole("ADMIN")
            .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(user -> user.userService(oAuth2Service))
                        .successHandler(oAuth2SuccessHandler))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");

                            ErrorResponse errorResponse = ErrorResponse.builder()
                                    .timestamp(LocalDateTime.now())
                                    .status(HttpStatus.UNAUTHORIZED.value())
                                    .error("Unauthorized")
                                    .message("You are not authorized to access this resource")
                                    .path(request.getRequestURI())
                                    .build();

                            // Chuyển ErrorResponse thành JSON string
                            String json = new ObjectMapper().writeValueAsString(errorResponse);

                            response.getWriter().write(json);
                            response.getWriter().flush();
                        }))
                .addFilterBefore(new JWTAuthenticationFilter(jwtTokenHelper, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers(publicApis);
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();
        daoAuthenticationProvider.setUserDetailsService(userDetailsService);
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder());

        return new ProviderManager(daoAuthenticationProvider);

    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder(); 
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        var cors = new org.springframework.web.cors.CorsConfiguration();
        for (String origin : corsAllowedOrigins.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                cors.addAllowedOrigin(trimmed);
            }
        }
        cors.addAllowedHeader("*");
        cors.addAllowedMethod("*");

        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
