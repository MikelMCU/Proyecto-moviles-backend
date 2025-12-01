package pe.edu.upeu.gestion_pedidos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.gestion_pedidos.entity.Category;
import pe.edu.upeu.gestion_pedidos.exception.ResourceNotFoundException;
import pe.edu.upeu.gestion_pedidos.repository.CategoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // 1. Listar todas las categorías
    public List<Category> getAll() {
        return categoryRepository.findAll();
    }

    // 2. Buscar por ID
    public Category getById(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + id));
    }

    // 3. Guardar
    @Transactional
    public Category save(Category category) {
        return categoryRepository.save(category);
    }

    // 4. Actualizar
    @Transactional
    public Category update(String id, Category categoryActualizada) {
        Category categoriaExistente = getById(id);
        categoriaExistente.setName(categoryActualizada.getName());
        categoriaExistente.setImageUrl(categoryActualizada.getImageUrl());
        return categoryRepository.save(categoriaExistente);
    }

    // 5. Eliminar
    @Transactional
    public void delete(String id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("No se puede eliminar. Categoría no encontrada: " + id);
        }
        categoryRepository.deleteById(id);
    }
}