package io.codehunters.security.mvc;

import io.codehunters.security.CurrentPrincipal;
import io.codehunters.security.KeycloakPrincipal;
import io.codehunters.security.KeycloakSecurityUtils;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Injects the current {@link KeycloakPrincipal} into controller parameters annotated with {@link
 * CurrentPrincipal}. Resolves on the request thread from the {@code SecurityContext}; yields {@code
 * null} when no user is authenticated.
 */
public class CurrentPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

  /** Creates the resolver. Instantiated by the Spring container or directly in tests. */
  public CurrentPrincipalArgumentResolver() {}

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentPrincipal.class)
        && KeycloakPrincipal.class.isAssignableFrom(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    return KeycloakSecurityUtils.getCurrentUser().orElse(null);
  }
}
