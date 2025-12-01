package pe.edu.upeu.gestion_pedidos.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    @JsonBackReference
    private Product product;

    @Column(unique = true, nullable = false)
    private String sku;

    private String size;
    private String color;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "additional_price")
    private BigDecimal additionalPrice;

    @Column(name = "image_url")
    private String imageUrl;
}
