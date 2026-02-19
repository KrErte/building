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

        // Radiaatori paigaldus - PURPLE (küte/soojusvesi)
        seed("Radiaatori paigaldus", "PLUMBING", "PPR toru Ø20", "3.0", "jm", null, "PURPLE");
        seed("Radiaatori paigaldus", "PLUMBING", "Põlv 90° Ø20", "2.0", "tk", null, null);
        seed("Radiaatori paigaldus", "PLUMBING", "T-tükk Ø20", "1.0", "tk", null, null);
        seed("Radiaatori paigaldus", "PLUMBING", "Sulgeventiil Ø20", "2.0", "tk", null, null);
        seed("Radiaatori paigaldus", "PLUMBING", "Termoregulaator", "1.0", "tk", null, null);

        // WC poti paigaldus - BROWN (kanalisatsioon)
        seed("WC poti paigaldus", "PLUMBING", "Kanalisatsioonitoru Ø110", "1.5", "jm", null, "BROWN");
        seed("WC poti paigaldus", "PLUMBING", "Ühenduskomplekt", "1.0", "tk", null, null);
        seed("WC poti paigaldus", "PLUMBING", "Põlv 90° Ø110", "1.0", "tk", null, null);
        seed("WC poti paigaldus", "PLUMBING", "Tihendid ja kinnitused", "1.0", "kompl", null, null);

        // Segisti vahetus - BLUE (külm vesi) + PURPLE (soe vesi)
        seed("Segisti vahetus", "PLUMBING", "Veeühendus-voolik", "2.0", "tk", null, null);
        seed("Segisti vahetus", "PLUMBING", "Nurkkraan Ø15", "2.0", "tk", null, null);
        seed("Segisti vahetus", "PLUMBING", "PPR toru Ø20 (külm vesi)", "1.5", "jm", null, "BLUE");
        seed("Segisti vahetus", "PLUMBING", "PPR toru Ø20 (soe vesi)", "1.5", "jm", null, "PURPLE");

        // Boileri paigaldus - PURPLE (soe vesi) + BLUE (külm vesi)
        seed("Boileri paigaldus", "PLUMBING", "PPR toru Ø25 (soe vesi)", "4.0", "jm", null, "PURPLE");
        seed("Boileri paigaldus", "PLUMBING", "PPR toru Ø25 (külm vesi)", "3.0", "jm", null, "BLUE");
        seed("Boileri paigaldus", "PLUMBING", "Sulgeventiil Ø25", "2.0", "tk", null, null);
        seed("Boileri paigaldus", "PLUMBING", "Tagasilöögiklapp", "1.0", "tk", null, null);
        seed("Boileri paigaldus", "PLUMBING", "Kinnituskomplekt", "1.0", "kompl", null, null);

        // Kraanikausi paigaldus - BLUE + PURPLE + BROWN
        seed("Kraanikausi paigaldus", "PLUMBING", "PPR toru Ø20 (külm vesi)", "2.0", "jm", null, "BLUE");
        seed("Kraanikausi paigaldus", "PLUMBING", "PPR toru Ø20 (soe vesi)", "2.0", "jm", null, "PURPLE");
        seed("Kraanikausi paigaldus", "PLUMBING", "Kanalisatsioonitoru Ø50", "1.5", "jm", null, "BROWN");
        seed("Kraanikausi paigaldus", "PLUMBING", "Sifoon", "1.0", "tk", null, null);

        // Dušinurga paigaldus - BLUE + PURPLE + BROWN
        seed("Dušinurga paigaldus", "PLUMBING", "PPR toru Ø20 (külm vesi)", "3.0", "jm", null, "BLUE");
        seed("Dušinurga paigaldus", "PLUMBING", "PPR toru Ø20 (soe vesi)", "3.0", "jm", null, "PURPLE");
        seed("Dušinurga paigaldus", "PLUMBING", "Kanalisatsioonitoru Ø50", "2.0", "jm", null, "BROWN");
        seed("Dušinurga paigaldus", "PLUMBING", "Trappi Ø50", "1.0", "tk", null, null);

        // Vanni paigaldus - BLUE + PURPLE + BROWN
        seed("Vanni paigaldus", "PLUMBING", "PPR toru Ø20 (külm vesi)", "3.0", "jm", null, "BLUE");
        seed("Vanni paigaldus", "PLUMBING", "PPR toru Ø20 (soe vesi)", "3.0", "jm", null, "PURPLE");
        seed("Vanni paigaldus", "PLUMBING", "Kanalisatsioonitoru Ø50", "1.5", "jm", null, "BROWN");
        seed("Vanni paigaldus", "PLUMBING", "Ülevool + sifoon", "1.0", "tk", null, null);

        // Pesumasina ühendus - BLUE + BROWN
        seed("Pesumasina ühendus", "PLUMBING", "PPR toru Ø20 (külm vesi)", "2.0", "jm", null, "BLUE");
        seed("Pesumasina ühendus", "PLUMBING", "Kanalisatsioonitoru Ø50", "1.5", "jm", null, "BROWN");
        seed("Pesumasina ühendus", "PLUMBING", "Nurkkraan Ø15", "1.0", "tk", null, null);

        // Nõudepesumasina ühendus - BLUE + PURPLE + BROWN
        seed("Nõudepesumasina ühendus", "PLUMBING", "PPR toru Ø20 (külm vesi)", "1.5", "jm", null, "BLUE");
        seed("Nõudepesumasina ühendus", "PLUMBING", "PPR toru Ø20 (soe vesi)", "1.5", "jm", null, "PURPLE");
        seed("Nõudepesumasina ühendus", "PLUMBING", "Kanalisatsioonitoru Ø50", "1.0", "jm", null, "BROWN");

        // Kanalisatsiooni magistraaltoru - BROWN
        seed("Kanalisatsiooni magistraal", "PLUMBING", "Kanalisatsioonitoru Ø110", "1.0", "jm", null, "BROWN");
        seed("Kanalisatsiooni magistraal", "PLUMBING", "Põlv 90° Ø110", "0.3", "tk", null, null);
        seed("Kanalisatsiooni magistraal", "PLUMBING", "T-tükk Ø110/50", "0.2", "tk", null, null);

        // Veevarustuse magistraal - BLUE + PURPLE
        seed("Veevarustuse magistraal", "PLUMBING", "PPR toru Ø25 (külm vesi)", "1.0", "jm", null, "BLUE");
        seed("Veevarustuse magistraal", "PLUMBING", "PPR toru Ø25 (soe vesi)", "1.0", "jm", null, "PURPLE");

        // === Küte (HVAC) ===

        // Põrandakütte paigaldus - PURPLE
        seed("Põrandakütte paigaldus", "HVAC", "PEX toru Ø16", "5.0", "jm", null, "PURPLE");
        seed("Põrandakütte paigaldus", "HVAC", "Kinnitusklamber", "4.0", "tk", null, null);
        seed("Põrandakütte paigaldus", "HVAC", "Soojustusplaat", "1.0", "m2", null, null);

        // Küttesüsteemi magistraal - PURPLE
        seed("Küttesüsteemi paigaldus", "HVAC", "PPR toru Ø32 (küte)", "2.0", "jm", null, "PURPLE");
        seed("Küttesüsteemi paigaldus", "HVAC", "Sulgeventiil Ø32", "0.5", "tk", null, null);
        seed("Küttesüsteemi paigaldus", "HVAC", "Tsirkulatsioonipump", "0.1", "tk", null, null);

        // === Elekter (Electrical) ===
        seed("Lüliti/pistikupesa paigaldus", "ELECTRICAL", "Elektrikaabel NYM 3x2.5", "5.0", "jm", null, null);
        seed("Lüliti/pistikupesa paigaldus", "ELECTRICAL", "Paigalduskarp", "1.0", "tk", null, null);
        seed("Lüliti/pistikupesa paigaldus", "ELECTRICAL", "Kaablikanal", "2.0", "jm", null, null);

        seed("Kilbi koostamine", "ELECTRICAL", "Automaatkaitselüliti", "8.0", "tk", null, null);
        seed("Kilbi koostamine", "ELECTRICAL", "Rikkevoolukaitselüliti", "2.0", "tk", null, null);
        seed("Kilbi koostamine", "ELECTRICAL", "Jaotuskilp 24mod", "1.0", "tk", null, null);

        // === Siseehitus (Interior / General Construction) ===
        seed("Kipsplaadi paigaldus", "GENERAL_CONSTRUCTION", "CD profiil 60/27", "3.0", "jm", null, null);
        seed("Kipsplaadi paigaldus", "GENERAL_CONSTRUCTION", "UD profiil 28/27", "1.5", "jm", null, null);
        seed("Kipsplaadi paigaldus", "GENERAL_CONSTRUCTION", "Kruvid", "15.0", "tk", null, null);

        seed("Siseukse paigaldus", "WINDOWS_DOORS", "Montaaživaht", "0.5", "tk", null, null);
        seed("Siseukse paigaldus", "WINDOWS_DOORS", "Kruvid ja tüüblid", "1.0", "kompl", null, null);
        seed("Siseukse paigaldus", "WINDOWS_DOORS", "Liist", "5.0", "jm", null, null);

        // === Plaatimine (Tiling) ===
        seed("Plaatimistööd", "TILING", "Plaadiliim C2 (25kg)", "0.12", "tk", null, null);
        seed("Plaatimistööd", "TILING", "Vuugisegu (5kg)", "0.04", "tk", null, null);
        seed("Plaatimistööd", "TILING", "Risti 2mm", "0.5", "pakk", null, null);

        seed("Hüdroisolatsioonitööd", "TILING", "Hüdroisolatsioonimastiks", "0.5", "kg", null, null);
        seed("Hüdroisolatsioonitööd", "TILING", "Hüdroisolatsioonilint", "1.0", "jm", null, null);

        // === Viimistlus (Finishing) ===
        seed("Seinte pahteldamine", "FINISHING", "Pahtel (25kg)", "0.04", "tk", null, null);
        seed("Seinte pahteldamine", "FINISHING", "Lihvpaber P120", "0.1", "tk", null, null);

        seed("Värvimine", "FINISHING", "Krunt (10L)", "0.01", "tk", null, null);
        seed("Värvimine", "FINISHING", "Sisevärv (10L)", "0.02", "tk", null, null);
        seed("Värvimine", "FINISHING", "Rull + käepide", "0.05", "tk", null, null);

        log.info("Seeded {} component recipes", recipeRepository.count());
    }

    private void seed(String componentName, String category, String materialName,
                      String qtyPerUnit, String unit, String notes, String pipeColor) {
        ComponentRecipe recipe = new ComponentRecipe();
        recipe.setComponentName(componentName);
        recipe.setComponentCategory(category);
        recipe.setMaterialName(materialName);
        recipe.setQuantityPerUnit(new BigDecimal(qtyPerUnit));
        recipe.setMaterialUnit(unit);
        recipe.setNotes(notes);
        recipe.setPipeColor(pipeColor);
        recipeRepository.save(recipe);
    }
}
