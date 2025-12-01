package pe.edu.upeu.gestion_pedidos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<Order>> getAll() {
        String userId = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getById(@PathVariable String id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    @PostMapping("/sync")
    public ResponseEntity<Order> syncOrder(@RequestBody OrderDTO orderDto) {
        return ResponseEntity.ok(orderService.syncOrder(orderDto));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateStatus(@PathVariable String id, @RequestParam OrderStatus status) {
        return ResponseEntity.ok(orderService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelOrder(@PathVariable String id) {
        orderService.updateStatus(id, OrderStatus.CANCELLED);
        return ResponseEntity.noContent().build();
    }
}