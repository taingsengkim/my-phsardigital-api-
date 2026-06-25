package co.istad.sengkim.phsardigital.features.categories;

import co.istad.sengkim.phsardigital.config.config.BasedEntity;
import co.istad.sengkim.phsardigital.features.file.FileUpload;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.resilience.annotation.ConcurrencyLimit;

import java.util.List;
import java.util.UUID;

@Setter
@Getter
@NoArgsConstructor
@Entity
@Table(name = "categories")
public class Category extends BasedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(nullable = false,length = 150)
    private String name;
    @Column(nullable = false,unique = true,length = 150)
    private String slug;

    @ManyToOne
    @JoinColumn(name = "icon_file_id")
    private FileUpload iconFile;
    @Column(length = 250)
    private String description;
    @Column(nullable = false)
    private Integer level = 1;
    private Integer sortOrder = 0;
    private Boolean isActive;
    @Column(nullable = false)
    private Boolean isDeleted;
    @ManyToOne
    @JoinColumn(name = "parent_id")
    Category parentCategory;

    @OneToMany(mappedBy = "parentCategory",cascade = CascadeType.REMOVE)
    private List<Category> childCategories;
}
