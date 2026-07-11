package shop.product.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Base64;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Good implements Serializable {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @Column(name = "name", columnDefinition = "TEXT")
  private String name;

  private long priceKopeck;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "category", columnDefinition = "TEXT")
  private String category;

  private byte[] image;

  @Access(AccessType.FIELD)
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "good_manufacturer",
      joinColumns = @JoinColumn(name = "good_id", referencedColumnName = "id"),
      inverseJoinColumns = @JoinColumn(name = "manufacturer_id", referencedColumnName = "id"))
  private List<Manufacturer> manufacturers;

  private int quantity;

  public Good() {}

  public Good(
      String name,
      long priceKopeck,
      String description,
      String category,
      byte[] image,
      List<Manufacturer> manufacturers) {
    this.name = name;
    this.priceKopeck = priceKopeck;
    this.description = description;
    this.category = category;
    this.image = image;
    this.manufacturers = manufacturers;
  }

  @Transient
  @JsonIgnore
  public double getPriceGrn() {
    return (double) (priceKopeck) / 100;
  }

  @Transient
  @JsonIgnore
  public String getImageBase64() {
    return Base64.getEncoder().encodeToString(image);
  }
}
