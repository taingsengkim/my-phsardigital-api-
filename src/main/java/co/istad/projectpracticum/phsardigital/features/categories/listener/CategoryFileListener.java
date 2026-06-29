package co.istad.projectpracticum.phsardigital.features.categories.listener;

import co.istad.projectpracticum.phsardigital.core.event.FileDeletedEvent;
import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CategoryFileListener {
    private final CategoryRepository categoryRepository;
    @EventListener
    @Transactional
    public void handle(FileDeletedEvent event){
        List<Category> categories = categoryRepository.findAllByIconFile_ObjectName(event.objectName());
        for (Category category : categories){
            category.setIconFile(null);
        }
    }
}
