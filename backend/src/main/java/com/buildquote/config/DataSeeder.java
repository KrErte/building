package com.buildquote.config;

import com.buildquote.entity.Supplier;
import com.buildquote.entity.MarketPrice;
import com.buildquote.entity.User;
import com.buildquote.repository.SupplierRepository;
import com.buildquote.repository.MarketPriceRepository;
import com.buildquote.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final SupplierRepository supplierRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final UserService userService;

    public DataSeeder(SupplierRepository supplierRepository, MarketPriceRepository marketPriceRepository, UserService userService) {
        this.supplierRepository = supplierRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        seedSuppliers();
        seedMarketPrices();
        seedUsers();
    }

    private void seedSuppliers() {
        if (supplierRepository.count() > 0) {
            return;
        }

        String[][] companies = {
            {"Plaatija OÜ", "Jaan Tamm", "jaan@plaatija.ee", "+372 5551 1234", "Tallinn", "TILING", "GOOGLE_PLACES"},
            {"Elekter Pro OÜ", "Peeter Mets", "info@elekterpro.ee", "+372 5552 2345", "Tallinn", "ELECTRICAL", "GOOGLE_PLACES"},
            {"Sanitaar Expert AS", "Mari Kask", "mari@sanitaar.ee", "+372 5553 3456", "Tartu", "PLUMBING", "FIE_REGISTER"},
            {"Viimistlus Meistrid OÜ", "Andres Paju", "andres@viimistlus.ee", "+372 5554 4567", "Tallinn", "FINISHING", "VERIFIED"},
            {"Põrandatööd OÜ", "Kristjan Lepp", "kristjan@porand.ee", "+372 5555 5678", "Pärnu", "FLOORING", "GOOGLE_PLACES"},
            {"Ehitus Partner AS", "Toomas Kuusk", "toomas@ehitus.ee", "+372 5556 6789", "Tallinn", "GENERAL_CONSTRUCTION", "PROCUREMENT"},
            {"Katusemeister OÜ", "Mati Vaher", "mati@katus.ee", "+372 5557 7890", "Viljandi", "ROOFING", "FIE_REGISTER"},
            {"Fassaadi Grupp OÜ", "Priit Saar", "priit@fassaad.ee", "+372 5558 8901", "Tallinn", "FACADE", "GOOGLE_PLACES"},
            {"Lammutus Pro OÜ", "Raivo Tamm", "raivo@lammutus.ee", "+372 5559 9012", "Tallinn", "DEMOLITION", "SEED"},
            {"Aknad ja Uksed OÜ", "Kalle Kivi", "kalle@aknad.ee", "+372 5560 0123", "Tallinn", "WINDOWS_DOORS", "VERIFIED"},
            {"Küte Lahendused AS", "Jaak Põld", "jaak@kyte.ee", "+372 5561 1234", "Tartu", "HVAC", "GOOGLE_PLACES"},
            {"Haljastus Expert OÜ", "Siim Mänd", "siim@haljastus.ee", "+372 5562 2345", "Pärnu", "LANDSCAPING", "FIE_REGISTER"},
            {"Plaatimine24 OÜ", "Aivar Rand", "aivar@plaatimine24.ee", "+372 5563 3456", "Tallinn", "TILING", "GOOGLE_PLACES"},
            {"ElektriPunkt OÜ", "Mart Sild", "mart@elektripunkt.ee", "+372 5564 4567", "Narva", "ELECTRICAL", "SEED"},
            {"Torude Meister OÜ", "Karl Järv", "karl@torud.ee", "+372 5565 5678", "Tallinn", "PLUMBING", "VERIFIED"},
            {"Värvimeister AS", "Olev Tamm", "olev@varv.ee", "+372 5566 6789", "Rakvere", "FINISHING", "GOOGLE_PLACES"},
            {"Parkett Pro OÜ", "Rein Kask", "rein@parkett.ee", "+372 5567 7890", "Tallinn", "FLOORING", "GOOGLE_PLACES"},
            {"MasterBuild OÜ", "Ants Põder", "ants@masterbuild.ee", "+372 5568 8901", "Tartu", "GENERAL_CONSTRUCTION", "PROCUREMENT"},
            {"Plekk ja Katus OÜ", "Jaanus Kivi", "jaanus@plekk.ee", "+372 5569 9012", "Kuressaare", "ROOFING", "FIE_REGISTER"},
            {"Nordic Facade OÜ", "Erik Lepp", "erik@nordic.ee", "+372 5570 0123", "Tallinn", "FACADE", "GOOGLE_PLACES"},
            {"Demolition Team OÜ", "Indrek Mets", "indrek@demo.ee", "+372 5571 1234", "Tallinn", "DEMOLITION", "SEED"},
            {"Window World AS", "Margus Saar", "margus@window.ee", "+372 5572 2345", "Pärnu", "WINDOWS_DOORS", "VERIFIED"},
            {"Soojus OÜ", "Taavi Rand", "taavi@soojus.ee", "+372 5573 3456", "Tallinn", "HVAC", "GOOGLE_PLACES"},
            {"Aed ja Haljastus OÜ", "Kalev Paju", "kalev@aed.ee", "+372 5574 4567", "Haapsalu", "LANDSCAPING", "GOOGLE_PLACES"},
            {"Keraamiline Plaatimine OÜ", "Heino Kuusk", "heino@keraam.ee", "+372 5575 5678", "Tallinn", "TILING", "FIE_REGISTER"},
        };

        for (int i = 0; i < companies.length; i++) {
            String[] c = companies[i];
            Supplier supplier = new Supplier();
            supplier.setCompanyName(c[0]);
            supplier.setContactPerson(c[1]);
            supplier.setEmail(c[2]);
            supplier.setPhone(c[3]);
            supplier.setCity(c[4]);
            supplier.setCategories(new String[]{c[5]});
            supplier.setServiceAreas(new String[]{c[4].toUpperCase(), "HARJUMAA"});
            supplier.setSource(c[6]);
            supplier.setGoogleRating(BigDecimal.valueOf(3.5 + Math.random() * 1.5));
            supplier.setGoogleReviewCount((int) (5 + Math.random() * 95));
            supplier.setTrustScore((int) (60 + Math.random() * 40));
            supplier.setIsVerified(c[6].equals("VERIFIED"));
            supplier.setCreatedAt(LocalDateTime.now());
            supplier.setUpdatedAt(LocalDateTime.now());
            supplierRepository.save(supplier);
        }
    }

    private void seedMarketPrices() {
        if (marketPriceRepository.count() > 0) {
            return;
        }

        String[][] prices = {
            {"TILING", "m2", "25", "45", "35"},
            {"ELECTRICAL", "m2", "15", "30", "22"},
            {"PLUMBING", "m2", "20", "40", "30"},
            {"FINISHING", "m2", "8", "15", "12"},
            {"FLOORING", "m2", "12", "25", "18"},
            {"DEMOLITION", "m2", "10", "20", "15"},
            {"ROOFING", "m2", "30", "60", "45"},
            {"HVAC", "m2", "25", "50", "38"},
            {"WINDOWS_DOORS", "tk", "150", "400", "275"},
            {"FACADE", "m2", "40", "80", "60"},
            {"LANDSCAPING", "m2", "15", "35", "25"},
            {"GENERAL_CONSTRUCTION", "m2", "100", "250", "175"},
        };

        for (String[] p : prices) {
            MarketPrice mp = new MarketPrice();
            mp.setCategory(p[0]);
            mp.setUnit(p[1]);
            mp.setMinPrice(new BigDecimal(p[2]));
            mp.setMaxPrice(new BigDecimal(p[3]));
            mp.setMedianPrice(new BigDecimal(p[4]));
            mp.setAvgPrice(new BigDecimal(p[4]));
            mp.setSampleCount(50);
            mp.setRegion("Tallinn");
            mp.setRegionMultiplier(BigDecimal.ONE);
            mp.setSource("SEED");
            mp.setLastUpdated(LocalDateTime.now());
            marketPriceRepository.save(mp);
        }
    }

    private void seedUsers() {
        log.info("Checking if users need to be seeded...");

        if (userService.countByRole(User.UserRole.ADMIN) > 0) {
            log.info("Users already exist, skipping seeding");
            return;
        }

        log.info("Seeding test users...");

        // Admin user
        userService.createUser(
            "admin@buildquote.ee",
            "Admin123!",
            "Admin",
            "Kasutaja",
            "BuildQuote OÜ",
            "+372 5555 1234",
            User.UserRole.ADMIN,
            User.SubscriptionPlan.ENTERPRISE
        );

        // Pro user
        userService.createUser(
            "pro@buildquote.ee",
            "Pro123!",
            "Mart",
            "Tamm",
            "Ehitus Pro OÜ",
            "+372 5555 2345",
            User.UserRole.USER,
            User.SubscriptionPlan.PRO
        );

        // Free user
        userService.createUser(
            "test@buildquote.ee",
            "Test123!",
            "Jaan",
            "Kask",
            "Väike Ehitus OÜ",
            "+372 5555 3456",
            User.UserRole.USER,
            User.SubscriptionPlan.FREE
        );

        // Another free user
        userService.createUser(
            "demo@buildquote.ee",
            "Demo123!",
            "Liis",
            "Mets",
            null,
            "+372 5555 4567",
            User.UserRole.USER,
            User.SubscriptionPlan.FREE
        );

        // Enterprise user
        userService.createUser(
            "enterprise@buildquote.ee",
            "Enterprise123!",
            "Peeter",
            "Kuusk",
            "Suur Ehitus AS",
            "+372 5555 5678",
            User.UserRole.USER,
            User.SubscriptionPlan.ENTERPRISE
        );

        log.info("Seeded 5 test users successfully!");
        log.info("-----------------------------------");
        log.info("Test accounts:");
        log.info("  admin@buildquote.ee / Admin123! (ADMIN, ENTERPRISE)");
        log.info("  pro@buildquote.ee / Pro123! (USER, PRO)");
        log.info("  test@buildquote.ee / Test123! (USER, FREE)");
        log.info("  demo@buildquote.ee / Demo123! (USER, FREE)");
        log.info("  enterprise@buildquote.ee / Enterprise123! (USER, ENTERPRISE)");
        log.info("-----------------------------------");
    }
}
