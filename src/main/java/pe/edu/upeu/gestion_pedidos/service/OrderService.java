package pe.edu.upeu.gestion_pedidos.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.gestion_pedidos.dto.OrderDTO;
import pe.edu.upeu.gestion_pedidos.dto.OrderItemDTO;
import pe.edu.upeu.gestion_pedidos.entity.*;
import pe.edu.upeu.gestion_pedidos.enums.OrderStatus;
import pe.edu.upeu.gestion_pedidos.exception.InsufficientStockException;
import pe.edu.upeu.gestion_pedidos.exception.ResourceNotFoundException;
import pe.edu.upeu.gestion_pedidos.repository.OrderItemRepository;
import pe.edu.upeu.gestion_pedidos.repository.OrderRepository;
import pe.edu.upeu.gestion_pedidos.repository.ProductVariantRepository;
import pe.edu.upeu.gestion_pedidos.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository productVariantRepository;
    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public Order findById(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada: " + id));
    }

    @Transactional
    public Order syncOrder(OrderDTO dto) {
        // 1. Idempotencia
        if (orderRepository.existsById(dto.getId())) {
            return orderRepository.findById(dto.getId()).get();
        }

        // 2. Validar Usuario
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // 3. INSERTAR CABECERA
        String sqlOrder = "INSERT INTO orders (id, user_id, status, total_amount, shipping_address, device_created_at, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())";

        jdbcTemplate.update(sqlOrder,
                dto.getId(),
                user.getId(),
                OrderStatus.PENDING.name(),
                BigDecimal.ZERO,
                dto.getShippingAddress(),
                dto.getDeviceCreatedAt()
        );

        // 4. Procesar e INSERTAR ITEMS
        String sqlItem = "INSERT INTO order_items (id, order_id, variant_id, quantity, unit_price_snapshot, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW(), NOW())";

        BigDecimal totalCalculado = BigDecimal.ZERO;

        for (OrderItemDTO itemDto : dto.getItems()) {
            ProductVariant variant = productVariantRepository.findById(itemDto.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variante no encontrada: " + itemDto.getVariantId()));

            // Stock
            if (variant.getStockQuantity() < itemDto.getQuantity()) {
                throw new InsufficientStockException("Stock insuficiente");
            }
            variant.setStockQuantity(variant.getStockQuantity() - itemDto.getQuantity());
            productVariantRepository.save(variant);

            // Precio
            BigDecimal precioFinal = variant.getProduct().getBasePrice().add(
                    variant.getAdditionalPrice() != null ? variant.getAdditionalPrice() : BigDecimal.ZERO
            );

            // INSERTAR ITEM DIRECTO A LA BD
            String itemId = java.util.UUID.randomUUID().toString();
            jdbcTemplate.update(sqlItem,
                    itemId,
                    dto.getId(),
                    variant.getId(),
                    itemDto.getQuantity(),
                    precioFinal
            );

            totalCalculado = totalCalculado.add(precioFinal.multiply(new BigDecimal(itemDto.getQuantity())));
        }

        // 5. ACTUALIZAR TOTAL
        String sqlUpdateTotal = "UPDATE orders SET total_amount = ? WHERE id = ?";
        jdbcTemplate.update(sqlUpdateTotal, totalCalculado, dto.getId());

        // 6. Limpiar caché de Hibernate
        entityManager.clear();

        // 7. Devolver la orden fresca desde la BD
        return orderRepository.findById(dto.getId()).get();
    }

    @Transactional
    public Order updateStatus(String id, OrderStatus status) {
        Order order = findById(id);
        order.setStatus(status);
        return orderRepository.save(order);
    }
    public List<Order> getOrdersByUser(String userId) {
        return orderRepository.findByUserId(userId);
    }
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}