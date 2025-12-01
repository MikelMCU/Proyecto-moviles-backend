package pe.edu.upeu.gestion_pedidos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class CreateOrderDTO {
    @NotNull(message = "El ID de usuario es obligatorio")
    private Integer userId;

    @NotBlank(message = "La dirección de envío es obligatoria")
    private String shippingAddress;

    @NotEmpty(message = "El pedido debe tener al menos un item")
    @Valid
    private List<CreateOrderItemDTO> items;
}
