package pe.edu.upeu.gestion_pedidos.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.gestion_pedidos.dto.*;
import pe.edu.upeu.gestion_pedidos.service.PaymentService;
import pe.edu.upeu.gestion_pedidos.service.StripeWebHookService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final StripeWebHookService stripeWebHookService;

    // 1. Crear Intención de Pago
    @PostMapping("/create-intent")
    public ResponseEntity<PaymentIntentResponseDTO> createPaymentIntent(
            @RequestBody CreatePaymentIntentDTO request) {
        return ResponseEntity.ok(paymentService.createPaymentIntent(request));
    }

    // 2. Confirmar
    @PostMapping("/confirm")
    public ResponseEntity<PaymentDTO> confirmPayment(@RequestBody ConfirmPaymentDTO request) {
        return ResponseEntity.ok(paymentService.confirmPayment(request));
    }

    // 3. Ver pagos de una orden
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentDTO>> getOrderPayments(@PathVariable String orderId) {
        return ResponseEntity.ok(paymentService.getOrderPayments(orderId));
    }

    // 4. Ver un pago específico
    @GetMapping("/{id}")
    public ResponseEntity<PaymentDTO> getPaymentById(@PathVariable String id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    // 5. WEBHOOK
    @PostMapping("/webhooks/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            stripeWebHookService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok("Webhook processed");
        } catch (Exception e) {
            log.error("Error en Webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}