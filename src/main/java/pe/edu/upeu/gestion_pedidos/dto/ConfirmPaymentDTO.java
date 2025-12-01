package pe.edu.upeu.gestion_pedidos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class ConfirmPaymentDTO {
    @NotBlank(message = "El ID de intención de pago es obligatorio")
    private String paymentIntentId;

    @NotBlank(message = "El ID del pedido es obligatorio")
    private String orderId;
}
