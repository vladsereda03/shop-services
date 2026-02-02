package shop.product.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Access(AccessType.FIELD)
@Entity
@Getter
@Setter
public class Manufacturer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", columnDefinition = "TEXT")
    private String name;

    @Column(name = "contacts", columnDefinition = "TEXT")
    private String contacts;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JsonIgnore
    @ManyToMany(mappedBy = "manufacturers", fetch = FetchType.EAGER)
    private List<Good> goods;

    public Manufacturer() {
    }

    public Manufacturer(String name, String contacts, String description) {
        this.name = name;
        this.contacts = contacts;
        this.description = description;
    }
}
