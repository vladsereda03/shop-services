package shop.client.config;

import org.springframework.context.ApplicationListener;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.oidc.authentication.event.OidcUserRefreshedEvent;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// final step of the refresh chain: Spring Security only publishes OidcUserRefreshedEvent,
// persisting the refreshed principal is left to the application. Without this the session
// keeps the login-time OidcUser and its stale ID token
@Component
public class OidcUserSessionRefreshListener implements ApplicationListener<OidcUserRefreshedEvent> {

  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  @Override
  public void onApplicationEvent(OidcUserRefreshedEvent event) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(event.getAuthentication());
    SecurityContextHolder.setContext(context);

    // token refresh always happens while serving an HTTP request, so the request
    // is available here; guard anyway in case that ever changes
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
        && attributes.getResponse() != null) {
      securityContextRepository.saveContext(
          context, attributes.getRequest(), attributes.getResponse());
    }
  }
}
