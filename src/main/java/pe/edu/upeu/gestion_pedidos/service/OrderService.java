package pe.edu.upeu.gestion_pedidos.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
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
        // OBTENER USER ID DEL CONTEXTO DE SEGURIDAD
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        System.out.println("\n🔵 INICIANDO syncOrder para usuario: " + userId);

        // 1. Idempotencia
        if (orderRepository.existsById(dto.getId())) {
            System.out.println("⚠️ Orden ya existe (idempotencia): " + dto.getId());
            return orderRepository.findById(dto.getId()).get();
        }

        // 2. Validar Usuario
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        System.out.println("👤 Usuario encontrado: " + user.getEmail() + " (ID: " + user.getId() + ")");

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

        System.out.println("📝 Orden creada con ID: " + dto.getId());

        // 4. Procesar e INSERTAR ITEMS
        String sqlItem = "INSERT INTO order_items (id, order_id, variant_id, quantity, unit_price_snapshot, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW(), NOW())";

        BigDecimal totalCalculado = BigDecimal.ZERO;

        for (OrderItemDTO itemDto : dto.getItems()) {
            ProductVariant variant = productVariantRepository.findById(itemDto.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variante no encontrada: " + itemDto.getVariantId()));

            System.out.println("\n📦 Procesando item: " + variant.getSku());
            System.out.println("   Producto: " + variant.getProduct().getName());
            System.out.println("   Variant ID: " + variant.getId());

            // Leer stock ANTES desde la BD directamente
            Integer stockBD = jdbcTemplate.queryForObject(
                    "SELECT stock_quantity FROM product_variants WHERE id = ?",
                    Integer.class,
                    variant.getId()
            );
            System.out.println("   Stock en BD ANTES: " + stockBD);
            System.out.println("   Cantidad solicitada: " + itemDto.getQuantity());

            // ✅ VALIDAR STOCK DISPONIBLE
            if (stockBD < itemDto.getQuantity()) {
                throw new InsufficientStockException(
                        "Stock insuficiente para: " + variant.getProduct().getName() +
                                " - " + variant.getSize() + " " + variant.getColor() +
                                ". Disponible: " + stockBD +
                                ", Solicitado: " + itemDto.getQuantity()
                );
            }

            // ✅ REDUCIR STOCK USANDO SQL DIRECTO
            int stockNuevo = stockBD - itemDto.getQuantity();
            String sqlUpdateStock = "UPDATE product_variants SET stock_quantity = ? WHERE id = ?";
            int rowsUpdated = jdbcTemplate.update(sqlUpdateStock, stockNuevo, variant.getId());

            System.out.println("   ✅ SQL UPDATE ejecutado. Rows affected: " + rowsUpdated);

            // Verificar que se actualizó
            Integer stockDespues = jdbcTemplate.queryForObject(
                    "SELECT stock_quantity FROM product_variants WHERE id = ?",
                    Integer.class,
                    variant.getId()
            );
            System.out.println("   Stock en BD DESPUÉS: " + stockDespues);
            System.out.println("   📊 Cambio: " + stockBD + " → " + stockDespues);

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

        System.out.println("\n💰 Total de la orden: $" + totalCalculado);

        // 6. Limpiar caché de Hibernate
        entityManager.clear();

        // 7. Devolver la orden fresca desde la BD
        Order ordenFinal = orderRepository.findById(dto.getId()).get();
        System.out.println("✅ Orden sincronizada exitosamente\n");

        return ordenFinal;
    }

    @Transactional
    public Order updateOrder(String orderId, OrderDTO orderDto, String userId) {
        System.out.println("\n🔵 INICIANDO updateOrder");
        System.out.println("   Orden ID: " + orderId);
        System.out.println("   Usuario: " + userId);

        // 1. Buscar la orden
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        // 2. Verificar que la orden pertenece al usuario
        if (!order.getUser().getId().equals(userId)) {
            throw new RuntimeException("No tienes permisos para modificar esta orden");
        }

        // 3. Verificar que la orden está en estado PENDING
        if (!order.getStatus().equals(OrderStatus.PENDING)) {
            throw new RuntimeException("Solo se pueden modificar órdenes pendientes");
        }

        // ✅ 4. DEVOLVER STOCK DE ITEMS ANTIGUOS
        System.out.println("\n🔄 Restaurando stock de items antiguos...");
        for (OrderItem oldItem : order.getItems()) {
            ProductVariant variant = oldItem.getVariant();

            // Leer stock actual desde BD
            Integer stockActual = jdbcTemplate.queryForObject(
                    "SELECT stock_quantity FROM product_variants WHERE id = ?",
                    Integer.class,
                    variant.getId()
            );

            int cantidadDevuelta = oldItem.getQuantity();
            int stockRestaurado = stockActual + cantidadDevuelta;

            // Actualizar usando SQL directo
            String sqlUpdateStock = "UPDATE product_variants SET stock_quantity = ? WHERE id = ?";
            jdbcTemplate.update(sqlUpdateStock, stockRestaurado, variant.getId());

            System.out.println("♻️ Stock restaurado: " + variant.getSku() +
                    " | Antes: " + stockActual +
                    " | Devuelto: " + cantidadDevuelta +
                    " | Después: " + stockRestaurado);
        }

        // 5. Eliminar los items antiguos
        order.getItems().clear();

        // ✅ 6. AGREGAR NUEVOS ITEMS Y REDUCIR STOCK
        System.out.println("\n📦 Procesando nuevos items...");
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemDTO itemDto : orderDto.getItems()) {
            ProductVariant variant = productVariantRepository.findById(itemDto.getVariantId())
                    .orElseThrow(() -> new RuntimeException("Variante no encontrada: " + itemDto.getVariantId()));

            // Leer stock actual desde BD
            Integer stockActual = jdbcTemplate.queryForObject(
                    "SELECT stock_quantity FROM product_variants WHERE id = ?",
                    Integer.class,
                    variant.getId()
            );

            System.out.println("   Item: " + variant.getSku());
            System.out.println("   Stock actual: " + stockActual);
            System.out.println("   Cantidad solicitada: " + itemDto.getQuantity());

            // ✅ VALIDAR STOCK DISPONIBLE
            if (stockActual < itemDto.getQuantity()) {
                throw new RuntimeException(
                        "Stock insuficiente para: " + variant.getProduct().getName() +
                                " - " + variant.getSize() + " " + variant.getColor() +
                                ". Disponible: " + stockActual +
                                ", Solicitado: " + itemDto.getQuantity()
                );
            }

            // ✅ REDUCIR STOCK usando SQL directo
            int stockNuevo = stockActual - itemDto.getQuantity();
            String sqlUpdateStock = "UPDATE product_variants SET stock_quantity = ? WHERE id = ?";
            jdbcTemplate.update(sqlUpdateStock, stockNuevo, variant.getId());

            System.out.println("   ✅ Stock reducido: " + stockActual + " → " + stockNuevo);

            // Crear nuevo OrderItem
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setVariant(variant);
            item.setQuantity(itemDto.getQuantity());

            BigDecimal unitPrice = variant.getProduct().getBasePrice()
                    .add(variant.getAdditionalPrice() != null ? variant.getAdditionalPrice() : BigDecimal.ZERO);
            item.setUnitPriceSnapshot(unitPrice);

            totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(itemDto.getQuantity())));

            order.getItems().add(item);
        }

        // 7. Actualizar el total
        order.setTotalAmount(totalAmount);

        // 8. Si hay dirección de envío en el DTO, actualizarla
        if (orderDto.getShippingAddress() != null && !orderDto.getShippingAddress().isEmpty()) {
            order.setShippingAddress(orderDto.getShippingAddress());
        }

        System.out.println("\n💰 Nuevo total: $" + totalAmount);
        System.out.println("✅ Orden actualizada exitosamente\n");

        return orderRepository.save(order);
    }

    @Transactional
    public Order updateOrderStatus(String orderId, OrderStatus newStatus) {
        System.out.println("\n🔵 INICIANDO updateOrderStatus");
        System.out.println("   Orden ID: " + orderId);
        System.out.println("   Nuevo status: " + newStatus);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        System.out.println("   Status anterior: " + oldStatus);

        // ✅ SI SE CANCELA LA ORDEN, DEVOLVER EL STOCK
        if (newStatus == OrderStatus.CANCELLED && oldStatus != OrderStatus.CANCELLED) {
            System.out.println("\n🚫 Cancelación detectada - Restaurando stock...");

            for (OrderItem item : order.getItems()) {
                ProductVariant variant = item.getVariant();

                // Leer stock actual desde BD
                Integer stockActual = jdbcTemplate.queryForObject(
                        "SELECT stock_quantity FROM product_variants WHERE id = ?",
                        Integer.class,
                        variant.getId()
                );

                int cantidadDevuelta = item.getQuantity();
                int stockRestaurado = stockActual + cantidadDevuelta;

                // Actualizar usando SQL directo
                String sqlUpdateStock = "UPDATE product_variants SET stock_quantity = ? WHERE id = ?";
                jdbcTemplate.update(sqlUpdateStock, stockRestaurado, variant.getId());

                System.out.println("♻️ Stock restaurado: " + variant.getSku() +
                        " | Antes: " + stockActual +
                        " | Devuelto: " + cantidadDevuelta +
                        " | Después: " + stockRestaurado);
            }

            System.out.println("✅ Stock restaurado exitosamente\n");
        }

        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    public List<Order> getOrdersByUser(String userId) {
        System.out.println("📋 getOrdersByUser - Usuario: " + userId);
        List<Order> orders = orderRepository.findByUserId(userId);
        System.out.println("   Órdenes encontradas: " + orders.size());
        return orders;
    }

    public List<Order> getOrdersByUserId(String userId) {
        System.out.println("📋 getOrdersByUserId - Usuario: " + userId);
        List<Order> orders = orderRepository.findByUserId(userId);
        System.out.println("   Órdenes encontradas: " + orders.size());
        return orders;
    }

    // ✅ CORREGIDO: Filtra por usuario autenticado
    public List<Order> getAllOrders() {
        System.out.println("\n🔵 LLAMANDO getAllOrders()");

        // Obtener el userId del token JWT
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        System.out.println("👤 Usuario del JWT: " + userId);

        // Filtrar órdenes solo del usuario autenticado
        List<Order> orders = orderRepository.findByUserId(userId);

        System.out.println("📊 Total de órdenes encontradas para este usuario: " + orders.size());

        if (orders.isEmpty()) {
            System.out.println("⚠️ No se encontraron órdenes para el usuario " + userId);
        } else {
            System.out.println("📝 IDs de órdenes:");
            for (Order order : orders) {
                System.out.println("   - " + order.getId() + " | Status: " + order.getStatus() + " | User: " + order.getUser().getId());
            }
        }

        return orders;
    }
}
