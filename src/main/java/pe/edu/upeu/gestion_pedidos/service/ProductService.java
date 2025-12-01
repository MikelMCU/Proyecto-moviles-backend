package pe.edu.upeu.gestion_pedidos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.gestion_pedidos.dto.ProductDTO;
import pe.edu.upeu.gestion_pedidos.entity.Category;
import pe.edu.upeu.gestion_pedidos.entity.Product;
import pe.edu.upeu.gestion_pedidos.entity.ProductVariant;
import pe.edu.upeu.gestion_pedidos.exception.ResourceNotFoundException;
import pe.edu.upeu.gestion_pedidos.repository.CategoryRepository;
import pe.edu.upeu.gestion_pedidos.repository.ProductRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // 1. LISTAR
    public List<Product> findAll() {
        return productRepository.findAllActive();
    }

    // 2. BUSCAR POR ID
    public Product findById(String id) {
        return productRepository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
    }

    // 3. BUSCAR POR CATEGORÍA
    public List<Product> findByCategory(String categoryId) {
        return productRepository.findByCategoryIdAndActive(categoryId);
    }

    // 4. GUARDAR
    @Transactional
    public Product save(ProductDTO dto) {
        Product product;
        if (dto.getId() != null && !dto.getId().isEmpty()) {
            product = productRepository.findById(dto.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no existe"));
        } else {
            product = new Product();
        }

        // Mapeo básico
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setBasePrice(dto.getBasePrice());
        product.setIsActive(true);

        // Categoría
        if (dto.getCategoryId() != null) {
            Category category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
            product.setCategory(category);
        }

        if (dto.getVariants() != null && !dto.getVariants().isEmpty()) {
            List<ProductVariant> variants = dto.getVariants().stream().map(variantDto -> {
                ProductVariant variant = new ProductVariant();
                variant.setSku(variantDto.getSku());
                variant.setSize(variantDto.getSize());
                variant.setColor(variantDto.getColor());
                variant.setStockQuantity(variantDto.getStockQuantity());
                variant.setAdditionalPrice(variantDto.getAdditionalPrice());
                variant.setProduct(product);
                return variant;
            }).collect(Collectors.toList());

            product.setVariants(variants);
        }

        return productRepository.save(product);
    }

    // 5. ELIMINAR
    @Transactional
    public void delete(String id) {
        Product product = findById(id);
        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    // 6. SINCRONIZACIÓN
    public List<Product> findDeletedSince(LocalDateTime date) {
        return productRepository.findDeletedSince(date);
    }
}