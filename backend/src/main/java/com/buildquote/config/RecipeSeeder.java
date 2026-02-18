package com.buildquote.config;

import com.buildquote.entity.ComponentRecipe;
import com.buildquote.repository.ComponentRecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class RecipeSeeder implements CommandLineRunner {

    private final ComponentRecipeRepository recipeRepository;

    @Override
    public void run(String... args) {
        if (recipeRepository.count() > 0) {
            log.info("Component recipes already seeded, skipping...");
            return;
        }

        log.info("Seeding component recipes...");

        // === Torutööd (Plumbing) ===
        seed("Radiaatori paigaldus", "PLUMBING", "PPR toru Ø20", "3.0", "jm", null);
        seed("Radiaatori paigaldus", "PLUMBING", "Põlv 90° Ø20", "2.0", "tk", null);
        seed("Radiaatori paigaldus", "PLUMBING", "T-tükk Ø20", "1.0", "tk", null);
        seed("Radiaatori paigaldus", "PLUMBING", "Sulgeventiil Ø20", "2.0", "tk", null);
        seed("Radiaatori paigaldus", "PLUMBING", "Termoregulaator", "1.0", "tk", null);

        seed("WC poti paigaldus", "PLUMBING", "Kanalisatsioonitoru Ø110", "1.5", "jm", null);
        seed("WC poti paigaldus", "PLUMBING", "Ühenduskomplekt", "1.0", "tk", null);
        seed("WC poti paigaldus", "PLUMBING", "Põlv 90° Ø110", "1.0", "tk", null);
        seed("WC poti paigaldus", "PLUMBING", "Tihendid ja kinnitused", "1.0", "kompl", null);

        seed("Segisti vahetus", "PLUMBING", "Veeühendus-voolik", "2.0", "tk", null);
        seed("Segisti vahetus", "PLUMBING", "Nurkkraan Ø15", "2.0", "tk", null);

        seed("Boileri paigaldus", "PLUMBING", "PPR toru Ø25", "4.0", "jm", null);
        seed("Boileri paigaldus", "PLUMBING", "Sulgeventiil Ø25", "2.0", "tk", null);
        seed("Boileri paigaldus", "PLUMBING", "Tagasilöögiklapp", "1.0", "tk", null);
        seed("Boileri paigaldus", "PLUMBING", "Kinnituskomplekt", "1.0", "kompl", null);

        seed("Põrandakütte paigaldus", "HVAC", "PEX toru Ø16", "5.0", "jm", null);
        seed("Põrandakütte paigaldus", "HVAC", "Kinnitusklamber", "4.0", "tk", null);
        seed("Põrandakütte paigaldus", "HVAC", "Soojustusplaat", "1.0", "m2", null);

        // === Elekter (Electrical) ===
        seed("Lüliti/pistikupesa paigaldus", "ELECTRICAL", "Elektrikaabel NYM 3x2.5", "5.0", "jm", null);
        seed("Lüliti/pistikupesa paigaldus", "ELECTRICAL", "Paigalduskarp", "1.0", "tk", null);
        seed("Lüliti/pistikupesa paigaldus", "ELECTRICAL", "Kaablikanal", "2.0", "jm", null);

        seed("Kilbi koostamine", "ELECTRICAL", "Automaatkaitselüliti", "8.0", "tk", null);
        seed("Kilbi koostamine", "ELECTRICAL", "Rikkevoolukaitselüliti", "2.0", "tk", null);
        seed("Kilbi koostamine", "ELECTRICAL", "Jaotuskilp 24mod", "1.0", "tk", null);

        // === Siseehitus (Interior / General Construction) ===
        seed("Kipsplaadi paigaldus", "GENERAL_CONSTRUCTION", "CD profiil 60/27", "3.0", "jm", null);
        seed("Kipsplaadi paigaldus", "GENERAL_CONSTRUCTION", "UD profiil 28/27", "1.5", "jm", null);
        seed("Kipsplaadi paigaldus", "GENERAL_CONSTRUCTION", "Kruvid", "15.0", "tk", null);

        seed("Siseukse paigaldus", "WINDOWS_DOORS", "Montaaživaht", "0.5", "tk", null);
        seed("Siseukse paigaldus", "WINDOWS_DOORS", "Kruvid ja tüüblid", "1.0", "kompl", null);
        seed("Siseukse paigaldus", "WINDOWS_DOORS", "Liist", "5.0", "jm", null);

        // === Plaatimine (Tiling) ===
        seed("Plaatimistööd", "TILING", "Plaadiliim C2 (25kg)", "0.12", "tk", null);
        seed("Plaatimistööd", "TILING", "Vuugisegu (5kg)", "0.04", "tk", null);
        seed("Plaatimistööd", "TILING", "Risti 2mm", "0.5", "pakk", null);

        seed("Hüdroisolatsioonitööd", "TILING", "Hüdroisolatsioonimastiks", "0.5", "kg", null);
        seed("Hüdroisolatsioonitööd", "TILING", "Hüdroisolatsioonilint", "1.0", "jm", null);

        // === Viimistlus (Finishing) ===
        seed("Seinte pahteldamine", "FINISHING", "Pahtel (25kg)", "0.04", "tk", null);
        seed("Seinte pahteldamine", "FINISHING", "Lihvpaber P120", "0.1", "tk", null);

        seed("Värvimine", "FINISHING", "Krunt (10L)", "0.01", "tk", null);
        seed("Värvimine", "FINISHING", "Sisevärv (10L)", "0.02", "tk", null);
        seed("Värvimine", "FINISHING", "Rull + käepide", "0.05", "tk", null);

        log.info("Seeded {} component recipes", recipeRepository.count());
    }

    private void seed(String componentName, String category, String materialName,
                      String qtyPerUnit, String unit, String notes) {
        ComponentRecipe recipe = new ComponentRecipe();
        recipe.setComponentName(componentName);
        recipe.setComponentCategory(category);
        recipe.setMaterialName(materialName);
        recipe.setQuantityPerUnit(new BigDecimal(qtyPerUnit));
        recipe.setMaterialUnit(unit);
        recipe.setNotes(notes);
        recipeRepository.save(recipe);
    }
}
