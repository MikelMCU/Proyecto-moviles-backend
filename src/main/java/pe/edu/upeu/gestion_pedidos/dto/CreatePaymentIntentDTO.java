package pe.edu.upeu.gestion_pedidos.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreatePaymentIntentDTO {
    @NotBlank(message = "El ID del pedido es obligatorio")
    private String orderId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.50", message = "El monto mínimo es 0.50")
    private BigDecimal amount;

    @NotBlank(message = "La moneda es obligatoria")
    @Size(min = 3, max = 3, message = "La moneda debe ser un código de 3 letras")
    private String currency;
}
