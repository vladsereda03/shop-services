package shop.client.dto;

import lombok.*;

import java.util.Base64;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GoodDTO {
    private long id;
    private String name;
    private long priceKopeck;
    private String description;
    private String category;
    private byte[] image;

    private List<ManufacturerDTO> manufacturers;

    private int quantity;


    public double getPriceGrn() {
        return (double) (priceKopeck) / 100;
    }
    public String getImageBase64() {
        return Base64.getEncoder().encodeToString(image);
    }
}
