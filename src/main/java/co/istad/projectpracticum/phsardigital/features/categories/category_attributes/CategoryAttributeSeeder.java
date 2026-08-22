package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static co.istad.projectpracticum.phsardigital.features.categories.category_attributes.AttributeDataType.*;

/**
 * Gives the categories a shop actually stocks the schema they would have in a shop —
 * a shirt with a size, a phone with a Display section — so the feature is usable without
 * an administrator typing sixty attribute definitions in by hand first.
 *
 * <p>Deliberately conservative about what it touches. It creates no categories: a shop
 * that does not sell watches should not acquire a watches schema. It skips any category
 * that already has attributes of its own, deleted ones included — an administrator who
 * removed the seeded {@code gsm} meant it, and a restart must not put it back. So the
 * whole thing is a no-op on the second run and on every run after an edit.
 *
 * <p>Turn it off with {@code app.categories.seed-attributes=false}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.categories.seed-attributes",
        havingValue = "true", matchIfMissing = true)
public class CategoryAttributeSeeder implements ApplicationRunner {

    private static final List<String> COLOURS = List.of(
            "Black", "White", "Grey", "Navy", "Blue", "Red", "Green",
            "Yellow", "Beige", "Brown", "Pink", "Multicolour");

    private static final List<String> CONDITIONS = List.of(
            "New", "Like new", "Used", "Refurbished", "For parts");

    private final CategoryRepository categoryRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Blueprint blueprint : blueprints()) {
            findCategory(blueprint).ifPresent(category -> seed(category, blueprint));
        }
    }

    private void seed(Category category, Blueprint blueprint) {
        if (categoryAttributeRepository.existsByCategory_Uuid(category.getUuid())) {
            return;
        }

        List<CategoryAttribute> attributes = new ArrayList<>();
        for (int position = 0; position < blueprint.specs().size(); position++) {
            attributes.add(toEntity(category, blueprint.specs().get(position), position));
        }
        categoryAttributeRepository.saveAll(attributes);
        log.info("Seeded {} attributes onto category '{}'.", attributes.size(), category.getSlug());
    }

    /**
     * Position within the blueprint becomes the sort order, so the spec table comes out
     * in the order these are written below — which is the order they were written to be
     * read in.
     */
    private CategoryAttribute toEntity(Category category, Spec spec, int position) {
        CategoryAttribute attribute = new CategoryAttribute();
        attribute.setCategory(category);
        attribute.setCode(spec.code);
        attribute.setLabel(spec.label);
        attribute.setGroupName(spec.group);
        attribute.setGroupSortOrder(spec.groupOrder);
        attribute.setSortOrder(position);
        attribute.setDataType(spec.dataType);
        attribute.setUnit(spec.unit);
        attribute.setRequired(spec.required);
        attribute.setFilterable(spec.filterable);
        attribute.setMinValue(spec.min);
        attribute.setMaxValue(spec.max);
        attribute.setIsDeleted(false);

        for (int i = 0; i < spec.options.size(); i++) {
            CategoryAttributeOption option = new CategoryAttributeOption();
            option.setAttribute(attribute);
            option.setValue(spec.options.get(i));
            option.setSortOrder(i);
            attribute.getOptions().add(option);
        }
        return attribute;
    }

    /** The first of the blueprint's slugs that this shop actually has a category for. */
    private Optional<Category> findCategory(Blueprint blueprint) {
        for (String slug : blueprint.slugs()) {
            Optional<Category> category = categoryRepository.findBySlugAndIsDeletedFalse(slug);
            if (category.isPresent()) {
                return category;
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ blueprints

    private List<Blueprint> blueprints() {
        return List.of(
                phones(), laptops(), shirts(), shoes(), watches(), headphones());
    }

    private Blueprint phones() {
        return new Blueprint(
                List.of("phones", "smartphones", "mobile-phones", "mobiles", "phone"),
                List.of(
                        spec("brand", "Brand").group("General", 0).required().filterable(),
                        spec("condition", "Condition").group("General", 0)
                                .type(SELECT).options(CONDITIONS).required().filterable(),
                        spec("os", "Operating system").group("General", 0)
                                .type(SELECT).options("Android", "iOS", "HarmonyOS", "Other").filterable(),
                        spec("colour", "Colour").group("General", 0).filterable(),
                        spec("dual_sim", "Dual SIM").group("General", 0).type(BOOLEAN),
                        spec("warranty_months", "Warranty").group("General", 0)
                                .type(NUMBER).unit("months").range(0.0, 60.0),

                        spec("screen_size", "Screen size").group("Display", 1)
                                .type(NUMBER).unit("in").range(3.0, 12.0).filterable(),
                        spec("resolution", "Resolution").group("Display", 1),
                        spec("refresh_rate", "Refresh rate").group("Display", 1)
                                .type(NUMBER).unit("Hz").range(30.0, 240.0).filterable(),
                        spec("panel", "Panel type").group("Display", 1)
                                .type(SELECT).options("AMOLED", "OLED", "IPS LCD", "LCD").filterable(),

                        spec("chipset", "Chipset").group("Performance", 2).filterable(),
                        spec("ram", "RAM").group("Performance", 2).type(SELECT)
                                .options("2 GB", "3 GB", "4 GB", "6 GB", "8 GB", "12 GB", "16 GB")
                                .required().filterable(),
                        spec("storage", "Storage").group("Performance", 2).type(SELECT)
                                .options("32 GB", "64 GB", "128 GB", "256 GB", "512 GB", "1 TB")
                                .required().filterable(),

                        spec("main_camera", "Main camera").group("Camera", 3)
                                .type(NUMBER).unit("MP").range(0.0, 250.0),
                        spec("front_camera", "Front camera").group("Camera", 3)
                                .type(NUMBER).unit("MP").range(0.0, 100.0),

                        spec("battery_capacity", "Battery").group("Battery", 4)
                                .type(NUMBER).unit("mAh").range(500.0, 15000.0),
                        spec("charging_speed", "Charging speed").group("Battery", 4)
                                .type(NUMBER).unit("W").range(1.0, 500.0)));
    }

    private Blueprint laptops() {
        return new Blueprint(
                List.of("laptops", "notebooks", "laptop"),
                List.of(
                        spec("brand", "Brand").group("General", 0).required().filterable(),
                        spec("condition", "Condition").group("General", 0)
                                .type(SELECT).options(CONDITIONS).required().filterable(),
                        spec("os", "Operating system").group("General", 0).type(SELECT)
                                .options("Windows", "macOS", "Linux", "ChromeOS", "No OS").filterable(),
                        spec("weight", "Weight").group("General", 0)
                                .type(NUMBER).unit("kg").range(0.5, 6.0),

                        spec("screen_size", "Screen size").group("Display", 1)
                                .type(NUMBER).unit("in").range(10.0, 20.0).filterable(),
                        spec("resolution", "Resolution").group("Display", 1),
                        spec("refresh_rate", "Refresh rate").group("Display", 1)
                                .type(NUMBER).unit("Hz").range(30.0, 360.0),

                        spec("processor", "Processor").group("Performance", 2).filterable(),
                        spec("ram", "RAM").group("Performance", 2).type(SELECT)
                                .options("4 GB", "8 GB", "16 GB", "32 GB", "64 GB")
                                .required().filterable(),
                        spec("storage", "Storage").group("Performance", 2).type(SELECT)
                                .options("128 GB", "256 GB", "512 GB", "1 TB", "2 TB")
                                .required().filterable(),
                        spec("storage_type", "Storage type").group("Performance", 2)
                                .type(SELECT).options("SSD", "HDD", "SSD + HDD").filterable(),
                        spec("graphics", "Graphics").group("Performance", 2),

                        spec("battery_capacity", "Battery").group("Battery", 3)
                                .type(NUMBER).unit("Wh").range(10.0, 150.0)));
    }

    private Blueprint shirts() {
        return new Blueprint(
                List.of("t-shirts", "tshirts", "t-shirt", "shirts", "shirt", "tops"),
                List.of(
                        spec("brand", "Brand").group("General", 0).filterable(),
                        spec("gender", "Worn by").group("General", 0).type(SELECT)
                                .options("Men", "Women", "Unisex", "Kids").required().filterable(),

                        spec("size", "Size").group("Size & Fit", 1).type(SELECT)
                                .options("XS", "S", "M", "L", "XL", "XXL", "3XL")
                                .required().filterable(),
                        spec("fit", "Fit").group("Size & Fit", 1).type(SELECT)
                                .options("Slim", "Regular", "Relaxed", "Oversized").filterable(),

                        spec("material", "Material").group("Material", 2).type(SELECT)
                                .options("Cotton", "Cotton blend", "Polyester", "Linen", "Rayon", "Wool")
                                .filterable(),
                        spec("gsm", "Fabric weight").group("Material", 2)
                                .type(NUMBER).unit("gsm").range(80.0, 400.0),
                        spec("care", "Care instructions").group("Material", 2),

                        spec("colour", "Colour").group("Appearance", 3)
                                .type(SELECT).options(COLOURS).required().filterable(),
                        spec("sleeve", "Sleeve").group("Appearance", 3).type(SELECT)
                                .options("Short sleeve", "Long sleeve", "Sleeveless").filterable(),
                        spec("neckline", "Neckline").group("Appearance", 3).type(SELECT)
                                .options("Crew neck", "V-neck", "Polo", "Henley", "Turtleneck"),
                        spec("pattern", "Pattern").group("Appearance", 3).type(SELECT)
                                .options("Plain", "Striped", "Graphic", "Checked", "Floral").filterable()));
    }

    private Blueprint shoes() {
        return new Blueprint(
                List.of("shoes", "footwear", "sneakers", "shoe"),
                List.of(
                        spec("brand", "Brand").group("General", 0).required().filterable(),
                        spec("gender", "Worn by").group("General", 0).type(SELECT)
                                .options("Men", "Women", "Unisex", "Kids").required().filterable(),
                        spec("condition", "Condition").group("General", 0)
                                .type(SELECT).options(CONDITIONS).filterable(),

                        spec("size", "Size").group("Size & Fit", 1)
                                .type(NUMBER).unit("EU").range(15.0, 50.0).required().filterable(),
                        spec("width", "Width").group("Size & Fit", 1)
                                .type(SELECT).options("Narrow", "Regular", "Wide"),

                        spec("upper_material", "Upper material").group("Material", 2).type(SELECT)
                                .options("Leather", "Suede", "Canvas", "Mesh", "Synthetic").filterable(),
                        spec("sole_material", "Sole material").group("Material", 2)
                                .type(SELECT).options("Rubber", "EVA", "PU", "Leather"),

                        spec("colour", "Colour").group("Appearance", 3)
                                .type(SELECT).options(COLOURS).required().filterable(),
                        spec("closure", "Closure").group("Appearance", 3).type(SELECT)
                                .options("Lace-up", "Slip-on", "Velcro", "Buckle", "Zip")));
    }

    private Blueprint watches() {
        return new Blueprint(
                List.of("watches", "watch", "wristwatches"),
                List.of(
                        spec("brand", "Brand").group("General", 0).required().filterable(),
                        spec("movement", "Movement").group("General", 0).type(SELECT)
                                .options("Automatic", "Mechanical", "Quartz", "Smart").required().filterable(),
                        spec("condition", "Condition").group("General", 0)
                                .type(SELECT).options(CONDITIONS).filterable(),

                        spec("case_diameter", "Case diameter").group("Case", 1)
                                .type(NUMBER).unit("mm").range(20.0, 60.0).filterable(),
                        spec("case_material", "Case material").group("Case", 1).type(SELECT)
                                .options("Stainless steel", "Titanium", "Gold", "Ceramic", "Plastic")
                                .filterable(),
                        spec("water_resistance", "Water resistance").group("Case", 1)
                                .type(NUMBER).unit("m").range(0.0, 1000.0),

                        spec("strap_material", "Strap material").group("Strap", 2).type(SELECT)
                                .options("Leather", "Steel", "Silicone", "Nylon", "Rubber").filterable(),
                        spec("strap_colour", "Strap colour").group("Strap", 2),

                        spec("display_type", "Display").group("Dial", 3).type(SELECT)
                                .options("Analog", "Digital", "Analog-digital").filterable(),
                        spec("dial_colour", "Dial colour").group("Dial", 3)));
    }

    private Blueprint headphones() {
        return new Blueprint(
                List.of("headphones", "earphones", "earbuds", "audio"),
                List.of(
                        spec("brand", "Brand").group("General", 0).required().filterable(),
                        spec("type", "Type").group("General", 0).type(SELECT)
                                .options("Over-ear", "On-ear", "In-ear", "True wireless")
                                .required().filterable(),
                        spec("condition", "Condition").group("General", 0)
                                .type(SELECT).options(CONDITIONS).filterable(),

                        spec("driver_size", "Driver size").group("Audio", 1)
                                .type(NUMBER).unit("mm").range(3.0, 120.0),
                        spec("frequency_response", "Frequency response").group("Audio", 1),
                        spec("noise_cancelling", "Active noise cancelling").group("Audio", 1)
                                .type(BOOLEAN).filterable(),

                        spec("connection", "Connection").group("Connectivity", 2)
                                .type(SELECT).options("Wired", "Bluetooth", "Wired + Bluetooth").filterable(),
                        spec("bluetooth_version", "Bluetooth version").group("Connectivity", 2),
                        spec("microphone", "Microphone").group("Connectivity", 2).type(BOOLEAN),

                        spec("battery_life", "Battery life").group("Battery", 3)
                                .type(NUMBER).unit("hours").range(0.0, 200.0),
                        spec("charging_port", "Charging port").group("Battery", 3).type(SELECT)
                                .options("USB-C", "Micro USB", "Lightning", "None")));
    }

    // ----------------------------------------------------------------------- model

    private record Blueprint(List<String> slugs, List<Spec> specs) {
    }

    private static Spec spec(String code, String label) {
        return new Spec(code, label);
    }

    /**
     * A definition as it reads in a blueprint. Mutable and fluent purely so the lists
     * above stay legible — every attribute would otherwise be an eleven-argument
     * constructor call, most of whose arguments are null.
     */
    private static final class Spec {
        private final String code;
        private final String label;
        private String group;
        private int groupOrder;
        private AttributeDataType dataType = TEXT;
        private String unit;
        private boolean required;
        private boolean filterable;
        private Double min;
        private Double max;
        private List<String> options = List.of();

        private Spec(String code, String label) {
            this.code = code;
            this.label = label;
        }

        private Spec group(String name, int order) {
            this.group = name;
            this.groupOrder = order;
            return this;
        }

        private Spec type(AttributeDataType dataType) {
            this.dataType = dataType;
            return this;
        }

        private Spec unit(String unit) {
            this.unit = unit;
            return this;
        }

        private Spec range(Double min, Double max) {
            this.min = min;
            this.max = max;
            return this;
        }

        private Spec options(String... values) {
            this.options = List.of(values);
            return this;
        }

        private Spec options(List<String> values) {
            this.options = values;
            return this;
        }

        private Spec required() {
            this.required = true;
            return this;
        }

        private Spec filterable() {
            this.filterable = true;
            return this;
        }
    }
}
