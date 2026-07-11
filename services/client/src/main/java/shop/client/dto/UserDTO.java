package shop.client.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
  private String username;
  private String fullName;
  private String email;
  private String phone;
}
