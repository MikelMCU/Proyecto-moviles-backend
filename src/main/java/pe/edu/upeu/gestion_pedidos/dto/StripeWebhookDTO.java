package pe.edu.upeu.gestion_pedidos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class StripeWebhookDTO {
    private String eventType;
    private String paymentIntentId;
    private String status;
    private String rawPayload;
}
