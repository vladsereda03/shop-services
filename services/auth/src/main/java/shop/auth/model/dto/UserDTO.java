package shop.auth.model.dto;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserDTO {

    public interface PublicView {}
    public interface InternalView extends PublicView {}

    @JsonView(PublicView.class)
    private String username;

    private String password;

    @JsonView(InternalView.class)
    private String email;

    @JsonView(PublicView.class)
    private String fullName;

    @JsonView(InternalView.class)
    private String phone;

    public UserDTO(String username, String email, String fullName, String phone) {
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
    }
}
