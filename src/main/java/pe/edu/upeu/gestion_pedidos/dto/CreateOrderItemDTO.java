package pe.edu.upeu.gestion_pedidos.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreateOrderItemDTO {
    @NotNull(message = "El ID de la variante es obligatorio")
    private Integer variantId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer quantity;
}
