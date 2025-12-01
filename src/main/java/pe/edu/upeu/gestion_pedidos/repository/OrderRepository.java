package pe.edu.upeu.gestion_pedidos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.gestion_pedidos.entity.Order;
import pe.edu.upeu.gestion_pedidos.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByUserId(String userId);

    List<Order> findByUserIdOrderByDeviceCreatedAtDesc(String userId);

    List<Order> findByStatus(OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.status = :status")
    List<Order> findByUserIdAndStatus(@Param("userId") String userId, @Param("status") OrderStatus status);

    // Para sincronización: pedidos creados después de una fecha
    @Query("SELECT o FROM Order o WHERE o.createdAt > :since")
    List<Order> findCreatedSince(@Param("since") LocalDateTime since);
}
