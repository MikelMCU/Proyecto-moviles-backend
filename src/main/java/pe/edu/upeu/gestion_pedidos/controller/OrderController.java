package pe.edu.upeu.gestion_pedidos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.gestion_pedidos.dto.OrderDTO;
import pe.edu.upeu.gestion_pedidos.entity.Order;
import pe.edu.upeu.gestion_pedidos.enums.OrderStatus;
import pe.edu.upeu.gestion_pedidos.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        System.out.println("\n Obteniendo órdenes del usuario autenticado");

        List<Order> orders = orderService.getAllOrders();

        System.out.println("Retornando " + orders.size() + " órdenes\n");
        return ResponseEntity.ok(orders);
    }


    @GetMapping("/{id}")
    public ResponseEntity<Order> getById(@PathVariable String id) {
        System.out.println(id);

        // Obtener el userId del JWT
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        System.out.println(userId);

        Order order = orderService.findById(id);

        if (!order.getUser().getId().equals(userId)) {
            System.out.println(" La orden " + id + " pertenece a " + order.getUser().getId());
            throw new RuntimeException("No tienes permisos para ver esta orden");
        }

        System.out.println("Orden encontrada y validada\n");
        return ResponseEntity.ok(order);
    }

    @PostMapping("/sync")
    public ResponseEntity<Order> syncOrder(@RequestBody OrderDTO orderDto) {
        System.out.println();
        System.out.println(orderDto.getId());
        System.out.println(orderDto.getItems().size());

        Order order = orderService.syncOrder(orderDto);

        System.out.println("Orden sincronizada exitosamente\n");
        return ResponseEntity.ok(order);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Order> updateOrder(
            @PathVariable String id,
            @RequestBody OrderDTO orderDto) {

        System.out.println("\n🌐 PUT /api/orders/" + id);

        // Obtener el userId del JWT
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        System.out.println(userId);

        Order updatedOrder = orderService.updateOrder(id, orderDto, userId);

        System.out.println(" Orden actualizada exitosamente\n");
        return ResponseEntity.ok(updatedOrder);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateStatus(
            @PathVariable String id,
            @RequestParam OrderStatus status) {

        // Obtener el userId del JWT para validación
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        System.out.println(userId);

        // Validar que la orden pertenece al usuario
        Order order = orderService.findById(id);
        if (!order.getUser().getId().equals(userId)) {
            throw new RuntimeException("No tienes permisos para modificar esta orden");
        }

        Order updatedOrder = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(updatedOrder);
    }
}
