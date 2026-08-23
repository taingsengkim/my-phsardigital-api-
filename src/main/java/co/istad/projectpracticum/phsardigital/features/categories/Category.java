package co.istad.projectpracticum.phsardigital.features.categories;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
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
    @Column(length = 1000)
    private String description;
    @Column(nullable = false)
    private Integer level = 1;
    @Column(nullable = false)
    private Integer sortOrder = 0;
    @Column(nullable = false)
    private Boolean isActive = false;
    @Column(nullable = false)
    private Boolean isDeleted = false;

    /** Detects concurrent hierarchy and lifecycle edits instead of losing one silently. */
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version = 0L;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    Category parentCategory;

    @OneToMany(mappedBy = "parentCategory",cascade = CascadeType.REMOVE)
    private List<Category> childCategories = new ArrayList<>();
}
