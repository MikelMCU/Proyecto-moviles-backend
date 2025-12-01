package pe.edu.upeu.gestion_pedidos.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class ProductVariantDTO {
    private Integer id;

    @NotNull(message = "El ID del producto es obligatorio")
    private Integer productId;

    @NotBlank(message = "El SKU es obligatorio")
    private String sku;

    private String size;
    private String color;

    @NotNull(message = "La cantidad en stock es obligatoria")
    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stockQuantity;

    private BigDecimal additionalPrice;
    private BigDecimal finalPrice;
}
