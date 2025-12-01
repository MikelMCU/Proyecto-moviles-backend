package pe.edu.upeu.gestion_pedidos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.gestion_pedidos.entity.ProductVariant;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {
    Optional<ProductVariant> findBySku(String sku);

    List<ProductVariant> findByProductId(String productId);

    @Query("SELECT v FROM ProductVariant v WHERE v.product.id = :productId AND v.stockQuantity > 0")
    List<ProductVariant> findAvailableByProductId(@Param("productId") String productId);

    boolean existsBySku(String sku);
}