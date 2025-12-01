package pe.edu.upeu.gestion_pedidos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.gestion_pedidos.entity.Product;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    @Query("SELECT p FROM Product p WHERE p.deletedAt IS NULL")
    List<Product> findAllActive();

    // Productos por categoría (activos)
    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.deletedAt IS NULL")
    List<Product> findByCategoryIdAndActive(@Param("categoryId") String categoryId);

    // Productos eliminados después de una fecha (para sincronización)
    @Query("SELECT p FROM Product p WHERE p.deletedAt > :since")
    List<Product> findDeletedSince(@Param("since") LocalDateTime since);
}
