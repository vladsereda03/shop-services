package shop.auth.model;

import jakarta.persistence.*;
import java.util.List;
import java.util.Set;
import lombok.*;

@Access(AccessType.FIELD)
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "\"users\"")
public class User {

  @Id
  @Column(columnDefinition = "bigint")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_gen")
  @SequenceGenerator(name = "user_gen", sequenceName = "user_seq", allocationSize = 1)
  private Long id;

  @Column(unique = true)
  private String username;

  private String password;
  private String email;
  private String fullName;
  private String phone;

  @ElementCollection
  @CollectionTable(name = "user_orders", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "order_id")
  private List<Long> orderIds;

  @Column(name = "cart_id")
  private Long cartId;

  @ElementCollection(fetch = FetchType.EAGER)
  @Enumerated(EnumType.STRING)
  private Set<Role> roles;
}
