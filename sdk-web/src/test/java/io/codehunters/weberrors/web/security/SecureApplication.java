package io.codehunters.weberrors.web.security;

import io.codehunters.weberrors.web.ProblemAccessDeniedHandler;
import io.codehunters.weberrors.web.ProblemAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** An application that wires the SDK's security handlers into its filter chain. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecureApplication {

  /** The user without the admin role. */
  public static final String USER = "clerk";

  /** The user's password. */
  public static final String PASSWORD = "clerk-password";

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ProblemAuthenticationEntryPoint entryPoint,
      ProblemAccessDeniedHandler accessDeniedHandler) {
    return http.authorizeHttpRequests(
            authorize ->
                authorize.requestMatchers("/admin").hasRole("ADMIN").anyRequest().authenticated())
        .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .build();
  }

  @Bean
  UserDetailsService users() {
    return new InMemoryUserDetailsManager(
        User.withUsername(USER).password("{noop}" + PASSWORD).roles("CLERK").build());
  }
}
