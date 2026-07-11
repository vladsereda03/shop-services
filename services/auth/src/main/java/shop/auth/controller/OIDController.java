package shop.auth.controller;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import shop.auth.model.User;
import shop.auth.model.dto.UserDTO;
import shop.auth.repository.UserRepository;

@RestController
@RequiredArgsConstructor
public class OIDController {

  private final UserRepository userRepository;

  @GetMapping("/connect/userinfo")
  @JsonView(UserDTO.InternalView.class)
  public UserDTO userInfo(@AuthenticationPrincipal Jwt jwt) {

    User user =
        userRepository
            .findByUsername(jwt.getSubject())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    return new UserDTO(user.getUsername(), user.getEmail(), user.getFullName(), user.getPhone());
  }
}
