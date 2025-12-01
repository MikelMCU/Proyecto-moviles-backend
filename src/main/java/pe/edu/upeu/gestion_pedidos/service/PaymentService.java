package pe.edu.upeu.gestion_pedidos.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.gestion_pedidos.dto.*;
import pe.edu.upeu.gestion_pedidos.entity.Order;
import pe.edu.upeu.gestion_pedidos.entity.Payment;
import pe.edu.upeu.gestion_pedidos.enums.OrderStatus;
import pe.edu.upeu.gestion_pedidos.enums.PaymentStatus;
import pe.edu.upeu.gestion_pedidos.exception.InvalidOperationException;
import pe.edu.upeu.gestion_pedidos.exception.PaymentProcessingException;
import pe.edu.upeu.gestion_pedidos.exception.ResourceNotFoundException;
import pe.edu.upeu.gestion_pedidos.repository.OrderRepository;
import pe.edu.upeu.gestion_pedidos.repository.PaymentRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    @Transactional
    public PaymentIntentResponseDTO createPaymentIntent(CreatePaymentIntentDTO request) {
        // Verificar que la orden existe
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));

        // Verificar que la orden está pendiente
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOperationException("La orden no está en estado pendiente");
        }

        try {
            // Convertir el monto a centavos
            long amountInCents = request.getAmount().multiply(new java.math.BigDecimal(100)).longValue();

            // Crear el PaymentIntent en Stripe
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(request.getCurrency().toLowerCase())
                    .putMetadata("order_id", order.getId())
                    .putMetadata("user_id", order.getUser().getId())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            // Guardar el pago en la base de datos
            Payment payment = Payment.builder()
                    .order(order)
                    .stripePaymentIntentId(paymentIntent.getId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status(PaymentStatus.PENDING)
                    .responseJson(objectMapper.writeValueAsString(paymentIntent.toJson()))
                    .build();

            paymentRepository.save(payment);

            log.info("PaymentIntent creado: {} para orden: {}", paymentIntent.getId(), order.getId());

            return PaymentIntentResponseDTO.builder()
                    .paymentIntentId(paymentIntent.getId())
                    .clientSecret(paymentIntent.getClientSecret())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status(paymentIntent.getStatus())
                    .build();

        } catch (StripeException e) {
            log.error("Error creando PaymentIntent: {}", e.getMessage());
            throw new PaymentProcessingException("Error procesando el pago: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado: {}", e.getMessage());
            throw new PaymentProcessingException("Error inesperado procesando el pago");
        }
    }

    @Transactional
    public PaymentDTO confirmPayment(ConfirmPaymentDTO request) {
        // Buscar el pago por el PaymentIntent ID
        Payment payment = paymentRepository.findByStripePaymentIntentId(request.getPaymentIntentId())
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));

        try {
            // Recuperar el PaymentIntent de Stripe
            PaymentIntent paymentIntent = PaymentIntent.retrieve(request.getPaymentIntentId());

            // Actualizar el estado del pago basado en Stripe
            payment.setStatus(mapStripeStatus(paymentIntent.getStatus()));
            payment.setResponseJson(objectMapper.writeValueAsString(paymentIntent.toJson()));
            payment = paymentRepository.save(payment);

            // Si el pago fue exitoso, actualizar el estado de la orden
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                Order order = payment.getOrder();
                order.setStatus(OrderStatus.PAID);
                orderRepository.save(order);
                log.info("Orden {} marcada como PAID", order.getId());
            }

            log.info("Pago confirmado: {} con estado: {}", payment.getId(), payment.getStatus());

            return mapToPaymentDTO(payment);

        } catch (StripeException e) {
            log.error("Error confirmando pago: {}", e.getMessage());
            throw new PaymentProcessingException("Error confirmando el pago: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado: {}", e.getMessage());
            throw new PaymentProcessingException("Error inesperado confirmando el pago");
        }
    }

    @Transactional
    public void handleWebhook(StripeWebhookDTO webhook) {
        log.info("Procesando webhook de Stripe: {}", webhook.getEventType());

        try {
            Payment payment = paymentRepository.findByStripePaymentIntentId(webhook.getPaymentIntentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));

            // Actualizar el estado del pago
            PaymentStatus newStatus = mapStripeStatusString(webhook.getStatus());
            payment.setStatus(newStatus);
            payment.setResponseJson(webhook.getRawPayload());
            paymentRepository.save(payment);

            // Si el pago fue exitoso, actualizar la orden
            if (newStatus == PaymentStatus.SUCCEEDED) {
                Order order = payment.getOrder();
                order.setStatus(OrderStatus.PAID);
                orderRepository.save(order);
                log.info("Orden {} actualizada a PAID por webhook", order.getId());
            }

            // Si el pago falló, podríamos restaurar el stock
            if (newStatus == PaymentStatus.FAILED) {
                log.warn("Pago {} falló. Considera restaurar el stock.", payment.getId());
            }

        } catch (Exception e) {
            log.error("Error procesando webhook: {}", e.getMessage());
            throw new PaymentProcessingException("Error procesando webhook");
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentDTO> getOrderPayments(String orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(this::mapToPaymentDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaymentDTO getPaymentById(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));

        return mapToPaymentDTO(payment);
    }

    // ========== HELPERS ==========

    private PaymentStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "canceled" -> PaymentStatus.CANCELLED;
            case "requires_payment_method", "requires_confirmation", "requires_action" -> PaymentStatus.PENDING;
            default -> PaymentStatus.FAILED;
        };
    }

    private PaymentStatus mapStripeStatusString(String status) {
        return mapStripeStatus(status);
    }

    private PaymentDTO mapToPaymentDTO(Payment payment) {
        return PaymentDTO.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .stripePaymentIntentId(payment.getStripePaymentIntentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
