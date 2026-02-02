package shop.client.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ManufacturerDTO {
    private long id;
    private String name;
    private String contacts;
    private String description;
}