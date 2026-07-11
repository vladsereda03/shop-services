package shop.product.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import shop.product.model.Manufacturer;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ManufacturerDTO {
  private long id;
  private String name;
  private String contacts;
  private String description;

  public ManufacturerDTO(Manufacturer manufacturer) {
    this.id = manufacturer.getId();
    this.name = manufacturer.getName();
    this.contacts = manufacturer.getContacts();
    this.description = manufacturer.getDescription();
  }
}
