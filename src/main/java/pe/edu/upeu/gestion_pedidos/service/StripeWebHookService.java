package pe.edu.upeu.gestion_pedidos.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.gestion_pedidos.entity.Order;
import pe.edu.upeu.gestion_pedidos.entity.Payment;
import pe.edu.upeu.gestion_pedidos.enums.OrderStatus;
import pe.edu.upeu.gestion_pedidos.enums.PaymentStatus;
import pe.edu.upeu.gestion_pedidos.repository.OrderRepository;
import pe.edu.upeu.gestion_pedidos.repository.PaymentRepository;

import java.time.LocalDateTime;

@Service
public class StripeWebHookService {

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Transactional
    public void handleWebhook(String payload, String sigHeader) throws SignatureVerificationException {

        // 2. VERIFICACIÓN
        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

        // 3. PROCESAMIENTO SEGÚN EL TIPO DE EVENTO
        switch (event.getType()) {
            case "payment_intent.succeeded":
                handlePaymentSucceeded(event);
                break;

            case "payment_intent.payment_failed":
                handlePaymentFailed(event);
                break;

            default:
                System.out.println("Evento no manejado: " + event.getType());
        }
    }

    private void handlePaymentSucceeded(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);

        if (paymentIntent != null) {
            String orderId = paymentIntent.getMetadata().get("order_id");

            // B. Actualizamos la Orden
            orderRepository.findById(orderId).ifPresent(order -> {
                order.setStatus(OrderStatus.PAID); // ¡Orden Pagada!
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);

                // C. Registramos/Actualizamos el Pago en nuestra tabla Payments
                savePaymentRecord(order, paymentIntent, PaymentStatus.SUCCEEDED);
            });

            System.out.println("Pago exitoso registrado para la orden: " + orderId);
        }
    }

    private void handlePaymentFailed(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);

        if (paymentIntent != null) {
            String orderId = paymentIntent.getMetadata().get("order_id");

            orderRepository.findById(orderId).ifPresent(order -> {
                savePaymentRecord(order, paymentIntent, PaymentStatus.FAILED);
            });
            System.out.println("Pago fallido para la orden: " + orderId);
        }
    }

    private void savePaymentRecord(Order order, PaymentIntent intent, PaymentStatus status) {
        Payment payment = Payment.builder()
                .order(order)
                .stripePaymentIntentId(intent.getId())
                .amount(new java.math.BigDecimal(intent.getAmount()).divide(new java.math.BigDecimal(100)))
                .currency(intent.getCurrency())
                .status(status)
                .responseJson(intent.toJson())
                .build();

        paymentRepository.save(payment);
    }
}