package ge.kiu.library.service;

import ge.kiu.library.dto.CategoryRequest;
import ge.kiu.library.dto.CategoryResponse;
import ge.kiu.library.dto.CategoryStatsView;
import ge.kiu.library.entity.Book;
import ge.kiu.library.entity.Category;
import ge.kiu.library.exception.DuplicateResourceException;
import ge.kiu.library.exception.InvalidRequestException;
import ge.kiu.library.exception.ResourceNotFoundException;
import ge.kiu.library.repository.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name");

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Page<CategoryResponse> getCategories(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidRequestException("Cannot sort categories by '" + order.getProperty()
                        + "'. Allowed: " + SORTABLE_FIELDS);
            }
        }
        return categoryRepository.findAll(pageable).map(CategoryResponse::from);
    }

    public List<CategoryResponse> getAllCategoriesSorted() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    public CategoryResponse getCategory(Long id) {
        return CategoryResponse.from(findCategory(id));
    }

    public CategoryResponse getCategoryByName(String name) {
        return categoryRepository.findByNameIgnoreCase(name.trim())
                .map(CategoryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with name: " + name));
    }

    public List<CategoryStatsView> getStatistics() {
        return categoryRepository.findCategoryStatistics();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new DuplicateResourceException("Category '" + request.name() + "' already exists");
        }
        Category category = new Category(request.name().trim(), request.description());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw new DuplicateResourceException("Category '" + request.name() + "' already exists");
        }
        category.setName(request.name().trim());
        category.setDescription(request.description());
        return CategoryResponse.from(category);
    }

    /** Removes the category from every book (join table rows) and then deletes it. */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = findCategory(id);
        for (Book book : new ArrayList<>(category.getBooks())) {
            book.removeCategory(category);
        }
        categoryRepository.delete(category);
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}
