package com.buildquote.config;

import com.buildquote.entity.MaterialUnitPrice;
import com.buildquote.entity.PriceSource;
import com.buildquote.entity.WorkMaterialBundle;
import com.buildquote.entity.WorkUnitPrice;
import com.buildquote.repository.MaterialUnitPriceRepository;
import com.buildquote.repository.PriceSourceRepository;
import com.buildquote.repository.WorkMaterialBundleRepository;
import com.buildquote.repository.WorkUnitPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class PriceDataSeeder implements CommandLineRunner {

    private final WorkUnitPriceRepository workUnitPriceRepository;
    private final MaterialUnitPriceRepository materialUnitPriceRepository;
    private final PriceSourceRepository priceSourceRepository;
    private final WorkMaterialBundleRepository workMaterialBundleRepository;

    @Override
    public void run(String... args) {
        seedPriceSources();
        seedWorkUnitPrices();
        seedMaterialUnitPrices();
        seedWorkMaterialBundles();
    }

    private void seedPriceSources() {
        if (priceSourceRepository.count() > 0) {
            log.info("Price sources already seeded, skipping...");
            return;
        }

        log.info("Seeding price sources...");
        List<PriceSource> sources = List.of(
            createPriceSource("ehitame24", "Ehitaja Sinu Kodule", "https://ehitame24.ee", "https://ehitame24.ee/hinnakiri/", "Ehitusfirma", "Tööhinnad km-ta ja ilma materjalita"),
            createPriceSource("ehitusabiline", "Ehitusabiline", "http://www.ehitusabiline.ee", "http://www.ehitusabiline.ee/teenused/VIIMISTLUSTÖÖD", "Ehitusfirma", "Tööhinnad km-ga"),
            createPriceSource("raemeistrid", "Raemeistrid", "https://www.raemeistrid.ee", "https://www.raemeistrid.ee/teenused-ja-hinnad", "Ehitusfirma", "Tööhinnad ilma materjalita"),
            createPriceSource("radelmark", "Radelmark OÜ", "https://radelmark.ee", "https://radelmark.ee/hinnad/", "Ehitusfirma", "Katuse- ja fassaaditööd"),
            createPriceSource("toruabi", "ToruAbi", "https://www.toruabi.info", "https://www.toruabi.info/torutoode-hinnakiri/", "Torutööd", "Tunnipõhised hinnad"),
            createPriceSource("toruexpert", "ToruExpert", "https://www.toruexpert.ee", "https://www.toruexpert.ee/hinnakiri/", "Torutööd", "Tunnipõhised hinnad"),
            createPriceSource("ehituskaup24", "Ehituskaup24 (Soosing OÜ)", "https://www.ehituskaup24.ee", "https://hinnad.ehituskaup24.ee/", "E-pood", "Suurim valik ja hinnavõrdlus"),
            createPriceSource("bauhof", "Bauhof", "https://www.bauhof.ee", "", "E-pood", "Ehitusmaterjalid"),
            createPriceSource("puumarket", "Puumarket", "https://www.puumarket.ee", "", "E-pood", "Puit ja puittooted"),
            createPriceSource("ehomer", "Homer", "https://www.ehomer.ee", "", "E-pood", "Projektimüük al 990€ tasuta transport"),
            createPriceSource("uponor", "Uponor", "https://www.uponor.com/et-ee", "", "Tootja", "Põrandaküte ja torustik"),
            createPriceSource("sanventehitus", "SanVentEhitus", "https://torutöödtartu.ee", "", "KVVK ettevõte", "Tartu piirkond küte/vesi/kanal")
        );
        priceSourceRepository.saveAll(sources);
        log.info("Seeded {} price sources", sources.size());
    }

    private PriceSource createPriceSource(String sourceId, String companyName, String website, String priceListUrl, String type, String notes) {
        PriceSource ps = new PriceSource();
        ps.setSourceId(sourceId);
        ps.setCompanyName(companyName);
        ps.setWebsite(website);
        ps.setPriceListUrl(priceListUrl);
        ps.setType(type);
        ps.setNotes(notes);
        return ps;
    }

    private void seedWorkUnitPrices() {
        if (workUnitPriceRepository.count() > 0) {
            log.info("Work unit prices already seeded, skipping...");
            return;
        }

        log.info("Seeding work unit prices...");
        List<WorkUnitPrice> prices = List.of(
            // Vundament
            createWorkPrice("Vundament", "Betoonitööd", "Lintvundamendi ehitus", "jm", 55, 70, 60, false, "ehitame24/raemeistrid", "Ainult tööhind"),
            createWorkPrice("Vundament", "Betoonitööd", "Kandvate postide valamine", "jm", 35, 50, 40, false, "ehitame24", "Ainult tööhind"),
            createWorkPrice("Vundament", "Betoonitööd", "Põranda valamine koos ettevalmistusega", "m2", 14, 20, 16, false, "ehitame24", "Betoon eraldi"),
            createWorkPrice("Vundament", "Betoonitööd", "Vahelagede valamine", "m2", 120, 150, 135, false, "ehitame24", "Rauda ja betooni hind eraldi"),
            createWorkPrice("Vundament", "Betoonitööd", "Vundamendi taldmiku ehitus 200x600mm", "jm", 28, 38, 33, false, "raemeistrid", "Ainult tööhind"),
            // Müüritööd
            createWorkPrice("Müüritööd", "Plokid", "Fibo seinte ladumine", "m2", 20, 35, 27, false, "ehitame24", "Sõltub ploki tüübist"),
            createWorkPrice("Müüritööd", "Plokid", "Aeroc seinte ladumine", "m2", 18, 35, 25, false, "ehitame24", "Sõltub ploki tüübist"),
            createWorkPrice("Müüritööd", "Plokid", "Columbia seinte ladumine", "m2", 19, 30, 24, false, "ehitame24", "Betoonplokk"),
            createWorkPrice("Müüritööd", "Plokid", "Silluste paigaldamine", "tk", 30, 45, 35, false, "ehitame24", "Ainult tööhind"),
            // Fassaad
            createWorkPrice("Fassaad", "Karkass", "Seinakarkassi konstruktsiooni ehitamine 100-250mm", "m2", 18, 28, 22, false, "ehitame24", "Puit- või metallkarkass"),
            createWorkPrice("Fassaad", "Soojustus", "Seina soojustuse paigaldamine", "m2", 5, 8, 6, false, "ehitame24", "Villa hind eraldi"),
            createWorkPrice("Fassaad", "Plaat", "OSB/tuuletõkke/kipsplaadi paigaldamine", "m2", 4, 8, 5, false, "ehitame24/ehitusabiline", "Plaadi hind eraldi"),
            createWorkPrice("Fassaad", "Voodrilaud", "Välisvoodri laudise paigaldamine", "m2", 12, 20, 15, false, "ehitame24", "Laudi hind eraldi"),
            createWorkPrice("Fassaad", "Voodrilaud", "Välisvoodri laudise värvimine (1 kiht)", "m2", 4, 7, 5, false, "ehitame24", "Värvi hind eraldi"),
            createWorkPrice("Fassaad", "Viimistlus", "Fassaadi värvimine", "m2", 6, 10, 8, false, "ehitame24", "Värvi hind eraldi"),
            createWorkPrice("Fassaad", "Plekk", "Veeplekkide paigaldamine", "jm", 8, 15, 10, false, "ehitame24", "Pleki hind eraldi"),
            // Siseehitus
            createWorkPrice("Siseehitus", "Karkass", "Metallkarkassi ehitamine seinale", "m2", 10, 15, 12, false, "ehitame24/ehitusabiline", "CD ja UD profiilid eraldi"),
            createWorkPrice("Siseehitus", "Karkass", "Metallkarkassi ehitamine lakke", "m2", 12, 18, 15, false, "ehitusabiline", "CD ja UD profiilid eraldi"),
            createWorkPrice("Siseehitus", "Soojustus", "Seinte ja lagede soojustamine", "m2", 4, 7, 5, false, "ehitame24", "Villa hind eraldi"),
            createWorkPrice("Siseehitus", "Plaat", "Kipsplaadi paigaldus seinale", "m2", 8, 12, 10, false, "ehitusabiline", "Plaadi hind eraldi"),
            createWorkPrice("Siseehitus", "Plaat", "Kipsplaadi paigaldus lakke", "m2", 10, 14, 12, false, "ehitusabiline", "Plaadi hind eraldi"),
            createWorkPrice("Siseehitus", "Põrand", "Põranda tasandamine isevalguva seguga", "m2", 14, 20, 16, false, "ehitame24", "Segu hind eraldi"),
            createWorkPrice("Siseehitus", "Põrand", "Klick laminaat- ja puitparkettide paigaldus", "m2", 15, 22, 18, false, "ehitame24", "Parketi hind eraldi"),
            createWorkPrice("Siseehitus", "Uksed", "Siseukse paigaldamine", "tk", 70, 100, 80, false, "ehitame24", "Ukse hind eraldi"),
            createWorkPrice("Siseehitus", "Aknad", "Aknalaudade paigaldus", "tk", 25, 35, 30, false, "ehitame24", "Laua hind eraldi"),
            // Viimistlus
            createWorkPrice("Viimistlus", "Laed", "Pahteldamine 2x + lihvimine 2x + kruntimine + 2 kihti värvimine", "m2", 13, 18, 15, false, "ehitame24", "Pahtli ja värvi hind eraldi"),
            createWorkPrice("Viimistlus", "Laed", "Lae värvimine 2 kihti", "m2", 3, 6, 4, false, "ehitusabiline", "Värvi hind eraldi"),
            createWorkPrice("Viimistlus", "Seinad", "Seinte pahteldamine 2x + lihvimine", "m2", 8, 14, 10, false, "ehitame24", "Pahtli hind eraldi"),
            createWorkPrice("Viimistlus", "Seinad", "Värvimine (kruntimine + 2 kihti)", "m2", 5, 10, 7, false, "ehitame24", "Värvi hind eraldi"),
            createWorkPrice("Viimistlus", "Seinad", "Tapeedi paigaldamine", "m2", 8, 14, 10, false, "ehitusabiline", "Tapeedi hind eraldi"),
            // Plaatimine
            createWorkPrice("Plaatimine", "Seinad/põrand", "Plaatimistööd kuni 60x60", "m2", 18, 25, 20, false, "ehitame24/ehitusabiline", "Plaadi ja liimi hind eraldi"),
            createWorkPrice("Plaatimine", "Hüdroisolatsioon", "Hüdroisolatsioonitööd", "m2", 5, 8, 6, false, "ehitame24", "Materjali hind eraldi"),
            createWorkPrice("Plaatimine", "Remont", "Komplektne vannitoa remont", "m2", 100, 150, 125, false, "raemeistrid", "Sisaldab kõiki töid"),
            // Terrass
            createWorkPrice("Terrass", "Ehitus", "Puitterrassi ehitamine", "m2", 22, 30, 25, false, "ehitame24/ehitusabiline", "Laudi hind eraldi"),
            createWorkPrice("Terrass", "Piirded", "Terrassi puitpiirete ehitamine", "m2", 15, 22, 18, false, "ehitame24", "Materjali hind eraldi"),
            // Katus
            createWorkPrice("Katus", "Katusekivi", "Katusekivide paigaldamine", "m2", 15, 25, 20, false, "radelmark", "Kivi hind eraldi"),
            createWorkPrice("Katus", "Plekk", "Katuse plekkide paigaldus", "m2", 12, 20, 15, false, "radelmark", "Pleki hind eraldi"),
            createWorkPrice("Katus", "Soojustus", "Katuste soojustamine", "m2", 5, 10, 7, false, "radelmark", "Villa hind eraldi"),
            createWorkPrice("Katus", "Renni", "Vihmaveesüsteemide paigaldamine", "jm", 8, 14, 10, false, "radelmark", "Renni hind eraldi"),
            // Torutööd
            createWorkPrice("Torutööd", "Seade", "Boileri paigaldus", "tk", 80, 150, 120, false, "toruabi", "Ainult tööhind"),
            createWorkPrice("Torutööd", "Seade", "WC poti paigaldus/vahetus", "tk", 60, 100, 80, false, "toruabi", "Ainult tööhind"),
            createWorkPrice("Torutööd", "Seade", "Segisti vahetus", "tk", 30, 60, 45, false, "toruabi", "Ainult tööhind"),
            createWorkPrice("Torutööd", "Põrandaküte", "Põrandakütte torustiku paigaldus", "m2", 15, 25, 20, false, "sanventehitus", "Toru hind eraldi"),
            createWorkPrice("Torutööd", "Radiaator", "Radiaatori paigaldus", "tk", 50, 100, 75, false, "sanventehitus", "Ainult tööhind"),
            // Elekter
            createWorkPrice("Elekter", "Paigaldus", "Elektrijuhtmete paigaldus", "jm", 3, 8, 5, false, "toruabi", "Kaabli hind eraldi"),
            createWorkPrice("Elekter", "Seade", "Lüliti/pistikupesa paigaldus", "tk", 15, 30, 22, false, "üldine turuhind", "Seadme hind eraldi"),
            createWorkPrice("Elekter", "Kilp", "Kilbi koostamine ja paigaldus", "tk", 200, 500, 350, false, "üldine turuhind", "Sõltub suurusest")
        );
        workUnitPriceRepository.saveAll(prices);
        log.info("Seeded {} work unit prices", prices.size());
    }

    private WorkUnitPrice createWorkPrice(String category, String subcategory, String materialName, String unit, double min, double max, double avg, boolean includesMaterial, String source, String notes) {
        WorkUnitPrice p = new WorkUnitPrice();
        p.setCategory(category);
        p.setSubcategory(subcategory);
        p.setMaterialName(materialName);
        p.setUnit(unit);
        p.setMinPriceEur(BigDecimal.valueOf(min));
        p.setMaxPriceEur(BigDecimal.valueOf(max));
        p.setAvgPriceEur(BigDecimal.valueOf(avg));
        p.setIncludesMaterial(includesMaterial);
        p.setSource(source);
        p.setNotes(notes);
        return p;
    }

    private void seedMaterialUnitPrices() {
        if (materialUnitPriceRepository.count() > 0) {
            log.info("Material unit prices already seeded, skipping...");
            return;
        }

        log.info("Seeding material unit prices...");
        List<MaterialUnitPrice> prices = List.of(
            // Soojustus
            createMaterialPrice("Soojustus", "Rockwool plaatvill 100mm", "m2", 5, 8, 6.5, "ehituskaup24/bauhof", "Fassaadile"),
            createMaterialPrice("Soojustus", "Rockwool plaatvill 150mm", "m2", 7, 11, 9, "ehituskaup24/bauhof", "Fassaadile"),
            createMaterialPrice("Soojustus", "Rockwool rullvill 200mm", "m2", 6, 9, 7.5, "ehituskaup24/bauhof", "Laele/pööningusse"),
            createMaterialPrice("Soojustus", "EPS 100 soojustusplaat 100mm", "m2", 4, 7, 5.5, "ehituskaup24/bauhof", "Fassaadile"),
            createMaterialPrice("Soojustus", "XPS soojustusplaat 100mm", "m2", 8, 13, 10, "ehituskaup24/bauhof", "Vundamendile"),
            // Ehitusplaadid
            createMaterialPrice("Ehitusplaadid", "Kipsplaat 12.5mm", "m2", 4, 6, 5, "ehituskaup24/bauhof", "Standardne GKB"),
            createMaterialPrice("Ehitusplaadid", "Kipsplaat 12.5mm niiskuskindel", "m2", 5, 8, 6.5, "ehituskaup24/bauhof", "GKBI roheline"),
            createMaterialPrice("Ehitusplaadid", "OSB-3 plaat 12mm", "m2", 6, 10, 8, "ehituskaup24/bauhof", "Niiskuskindel"),
            createMaterialPrice("Ehitusplaadid", "Tuuletõkkeplaat 12mm", "m2", 4, 7, 5.5, "ehituskaup24/bauhof", "Isoplaat vms"),
            // Plokid
            createMaterialPrice("Plokid", "Aeroc plokk 200mm", "m2", 18, 25, 22, "ehituskaup24/bauhof", "Gaasbetooni hind"),
            createMaterialPrice("Plokid", "Aeroc plokk 300mm", "m2", 25, 35, 30, "ehituskaup24/bauhof", "Gaasbetooni hind"),
            createMaterialPrice("Plokid", "Fibo plokk 200mm", "m2", 15, 22, 18, "ehituskaup24/bauhof", "Keramsiitplokk"),
            // Põrandad
            createMaterialPrice("Põrandad", "Laminaatparkett 8mm", "m2", 8, 15, 12, "ehituskaup24/bauhof", "Klass 32-33"),
            createMaterialPrice("Põrandad", "Puitparkett tamm 14mm", "m2", 25, 50, 35, "ehituskaup24/bauhof", "3-lipiline"),
            createMaterialPrice("Põrandad", "LVT vinüülparkett", "m2", 15, 30, 22, "ehituskaup24/bauhof", "Veekindel"),
            createMaterialPrice("Põrandad", "Põrandaplaat 60x60", "m2", 10, 30, 18, "ehituskaup24/bauhof", "Portselan/keraamika"),
            createMaterialPrice("Põrandad", "Seinaplaat 30x60", "m2", 8, 25, 15, "ehituskaup24/bauhof", "Keraamika"),
            // Segud
            createMaterialPrice("Segud", "Isevalguv tasandussegu (25kg)", "tk", 12, 20, 16, "ehituskaup24/bauhof", "Weber floor 110"),
            createMaterialPrice("Segud", "MP-75 krohvisegu (30kg)", "tk", 6, 10, 8, "ehituskaup24/bauhof", "Masinapahtel"),
            createMaterialPrice("Segud", "Betoon C25/30 valmissegu", "m3", 80, 120, 100, "betoonitehas", "Kohaletoimetusega"),
            // Värvid
            createMaterialPrice("Värvid", "Sisevärv (10L)", "tk", 25, 60, 40, "ehituskaup24/bauhof", "Tikkurila/Vivacolor"),
            createMaterialPrice("Värvid", "Välisvärv (10L)", "tk", 40, 80, 55, "ehituskaup24/bauhof", "Tikkurila"),
            createMaterialPrice("Värvid", "Krunt (10L)", "tk", 15, 30, 22, "ehituskaup24/bauhof", "Universaalne"),
            // Sanitaar
            createMaterialPrice("Sanitaar", "WC pott + paak komplekt", "tk", 80, 250, 150, "ehituskaup24/bauhof", "Standardne"),
            createMaterialPrice("Sanitaar", "Vannikomplekt 1700mm", "tk", 100, 400, 200, "ehituskaup24/bauhof", "Akrüül"),
            createMaterialPrice("Sanitaar", "Duššinurk 90x90", "tk", 150, 500, 300, "ehituskaup24/bauhof", "Klaas"),
            createMaterialPrice("Sanitaar", "Segisti dušš", "tk", 40, 200, 100, "ehituskaup24/bauhof", "Termostaatiline kallim"),
            createMaterialPrice("Sanitaar", "Boiler 80L", "tk", 200, 400, 300, "ehituskaup24/bauhof", "Elektri boiler"),
            // Küte
            createMaterialPrice("Küte", "Paneel radiaator 600x1000mm", "tk", 80, 150, 110, "ehituskaup24/bauhof", "22 tüüp"),
            createMaterialPrice("Küte", "Paneel radiaator 600x1400mm", "tk", 110, 200, 150, "ehituskaup24/bauhof", "22 tüüp"),
            createMaterialPrice("Küte", "Põrandakütte kollektorsüsteem 5 ringiga", "tk", 150, 300, 220, "ehituskaup24", "Koos kapiga"),
            // Uksed aknad
            createMaterialPrice("Uksed aknad", "Siseukse komplekt", "tk", 80, 250, 150, "ehituskaup24/bauhof", "Koos lengiga"),
            createMaterialPrice("Uksed aknad", "Välisuks komplekt", "tk", 300, 800, 500, "ehituskaup24/bauhof", "Soojustatud"),
            createMaterialPrice("Uksed aknad", "PVC aken 1200x1200", "tk", 150, 350, 250, "ehituskaup24", "3-klaas"),
            // Katus
            createMaterialPrice("Katus", "Katusekivi betoon", "m2", 10, 20, 15, "ehituskaup24/bauhof", "Monier vms"),
            createMaterialPrice("Katus", "Plekkkatus (profiilplekk)", "m2", 8, 15, 12, "ehituskaup24", "Värvitud"),
            createMaterialPrice("Katus", "Vihmaveesüsteem (renn+toru kompl)", "jm", 8, 18, 12, "ehituskaup24", "Plast/metall")
        );
        materialUnitPriceRepository.saveAll(prices);
        log.info("Seeded {} material unit prices", prices.size());
    }

    private MaterialUnitPrice createMaterialPrice(String category, String materialName, String unit, double min, double max, double avg, String source, String notes) {
        MaterialUnitPrice p = new MaterialUnitPrice();
        p.setCategory(category);
        p.setMaterialName(materialName);
        p.setUnit(unit);
        p.setMinPriceEur(BigDecimal.valueOf(min));
        p.setMaxPriceEur(BigDecimal.valueOf(max));
        p.setAvgPriceEur(BigDecimal.valueOf(avg));
        p.setSource(source);
        p.setNotes(notes);
        return p;
    }

    private void seedWorkMaterialBundles() {
        if (workMaterialBundleRepository.count() > 0) {
            log.info("Work material bundles already seeded, skipping...");
            return;
        }

        log.info("Seeding work material bundles...");
        List<WorkMaterialBundle> bundles = List.of(
            // Plaatimine - floor/wall tiling
            createBundle("Plaatimine", "Põrandaplaat 60x60", 1.1, "m2", "10% varu lõikejääkidele"),
            createBundle("Plaatimine", "Seinaplaat 30x60", 1.1, "m2", "10% varu lõikejääkidele"),
            createBundle("Plaatimine", "Isevalguv tasandussegu (25kg)", 0.12, "tk_per_m2", "3kg/m2, kotis 25kg"),
            createBundle("Plaatimine", "MP-75 krohvisegu (30kg)", 0.04, "tk_per_m2", "Vuugisegu asemel"),

            // Müüritööd - masonry
            createBundle("Müüritööd", "Aeroc plokk 200mm", 1.05, "m2", "5% varu"),
            createBundle("Müüritööd", "Aeroc plokk 300mm", 1.05, "m2", "5% varu"),
            createBundle("Müüritööd", "Fibo plokk 200mm", 1.05, "m2", "5% varu"),

            // Siseehitus - interior
            createBundle("Siseehitus", "Kipsplaat 12.5mm", 1.1, "m2", "Seinte/lagede jaoks"),
            createBundle("Siseehitus", "Kipsplaat 12.5mm niiskuskindel", 1.1, "m2", "Märjadesse ruumidesse"),
            createBundle("Siseehitus", "Rockwool plaatvill 100mm", 1.0, "m2", "Seina soojustus"),
            createBundle("Siseehitus", "Laminaatparkett 8mm", 1.08, "m2", "8% varu"),
            createBundle("Siseehitus", "Siseukse komplekt", 1.0, "tk", "Uksed"),

            // Viimistlus - finishing
            createBundle("Viimistlus", "Sisevärv (10L)", 0.012, "tk_per_m2", "1L = 8m2, 2 kihti"),
            createBundle("Viimistlus", "Krunt (10L)", 0.01, "tk_per_m2", "1L = 10m2"),
            createBundle("Viimistlus", "MP-75 krohvisegu (30kg)", 0.05, "tk_per_m2", "Pahteldamine"),

            // Vundament - foundation
            createBundle("Vundament", "Betoon C25/30 valmissegu", 0.15, "m3_per_jm", "Lintvundament 200x600"),
            createBundle("Vundament", "XPS soojustusplaat 100mm", 1.0, "m2", "Vundamendi soojustus"),

            // Fassaad - facade
            createBundle("Fassaad", "EPS 100 soojustusplaat 100mm", 1.05, "m2", "Fassaadi soojustus"),
            createBundle("Fassaad", "Rockwool plaatvill 150mm", 1.05, "m2", "Fassaadi villa soojustus"),
            createBundle("Fassaad", "OSB-3 plaat 12mm", 1.1, "m2", "Tuuletõke"),
            createBundle("Fassaad", "Välisvärv (10L)", 0.012, "tk_per_m2", "1L = 8m2, 2 kihti"),

            // Katus - roof
            createBundle("Katus", "Katusekivi betoon", 1.1, "m2", "10% varu"),
            createBundle("Katus", "Plekkkatus (profiilplekk)", 1.08, "m2", "8% varu"),
            createBundle("Katus", "Rockwool rullvill 200mm", 1.0, "m2", "Katuse soojustus"),
            createBundle("Katus", "Vihmaveesüsteem (renn+toru kompl)", 1.0, "jm", "Rennid"),

            // Torutööd - plumbing
            createBundle("Torutööd", "WC pott + paak komplekt", 1.0, "tk", "Sanitaar"),
            createBundle("Torutööd", "Segisti dušš", 1.0, "tk", "Dušisegisti"),
            createBundle("Torutööd", "Boiler 80L", 1.0, "tk", "Kui vajab vahetust"),
            createBundle("Torutööd", "Paneel radiaator 600x1000mm", 1.0, "tk", "Radiaator ruumi kohta"),

            // Elekter - electrical
            createBundle("Elekter", "Sisevärv (10L)", 0.0, "tk", "Elektritööd ei vaja materjale siit"),

            // Terrass - terrace
            createBundle("Terrass", "OSB-3 plaat 12mm", 0.0, "m2", "Terrassi alus, kui vajalik")
        );
        workMaterialBundleRepository.saveAll(bundles);
        log.info("Seeded {} work material bundles", bundles.size());
    }

    private WorkMaterialBundle createBundle(String workCategory, String materialName, double ratio, String unitType, String notes) {
        WorkMaterialBundle b = new WorkMaterialBundle();
        b.setWorkCategory(workCategory);
        b.setMaterialName(materialName);
        b.setRatio(BigDecimal.valueOf(ratio));
        b.setUnitType(unitType);
        b.setNotes(notes);
        return b;
    }
}
