package edu.vit.hcms.core.enums;

import edu.vit.hcms.core.config.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Priority, Category, and AppConfig Tests")
class PriorityCategoryConfigTest {

    @Test
    @DisplayName("Priority enum mapping and SLA hours")
    void testPriorityMapping() {
        assertEquals(Priority.HIGH, Priority.fromLevel(1));
        assertEquals(Priority.MEDIUM, Priority.fromLevel(2));
        assertEquals(Priority.LOW, Priority.fromLevel(3));
        assertThrows(IllegalArgumentException.class, () -> Priority.fromLevel(4));

        assertEquals(24, Priority.HIGH.getDefaultSlaHours());
        assertEquals(72, Priority.MEDIUM.getDefaultSlaHours());
        assertEquals(168, Priority.LOW.getDefaultSlaHours());
    }

    @Test
    @DisplayName("Category mapping to Staff Specialization")
    void testCategoryMapping() {
        assertEquals(Specialization.ELECTRICAL, Category.ELECTRICAL.getRequiredSpecialization());
        assertEquals(Specialization.PLUMBING, Category.PLUMBING.getRequiredSpecialization());
        assertEquals(Specialization.NETWORK, Category.WIFI.getRequiredSpecialization());
        assertEquals(Specialization.HVAC, Category.AC.getRequiredSpecialization());
        assertEquals(Specialization.CARPENTRY, Category.CARPENTRY.getRequiredSpecialization());
        assertEquals(Specialization.CLEANING, Category.CLEANING.getRequiredSpecialization());
        assertEquals(Specialization.GENERAL, Category.OTHER.getRequiredSpecialization());
    }

    @Test
    @DisplayName("AppConfig Singleton loads default settings")
    void testAppConfig() {
        AppConfig config = AppConfig.getInstance();
        assertNotNull(config);
        assertSame(config, AppConfig.getInstance(), "AppConfig must be a singleton");

        assertEquals(8080, config.getInt("PORT"));
        assertEquals(24, config.getInt("SLA_HIGH_HOURS"));
        assertEquals(72, config.getInt("SLA_MEDIUM_HOURS"));
        assertEquals(168, config.getInt("SLA_LOW_HOURS"));
        assertEquals("dev", config.get("PROFILE"));
    }
}
