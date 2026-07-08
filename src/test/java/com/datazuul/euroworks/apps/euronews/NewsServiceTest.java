package com.datazuul.euroworks.apps.euronews;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NewsServiceTest {

    @Test
    void testNewsServiceInitialization() {
        NewsService service = new NewsService();
        assertNotNull(service);
    }

    @Test
    void testCountryEnum() {
        EuropeanCountry germany = EuropeanCountry.GERMANY;
        assertEquals("de", germany.getCode());
        assertEquals("Deutschland", germany.getName());
    }

    @Test
    void testGetNewsForCountryOnlineOrFallback() {
        NewsService service = new NewsService();
        // Since this test might run in offline CI environments, we handle exceptions gracefully
        try {
            List<NewsItem> items = service.getNewsForCountry(EuropeanCountry.GERMANY);
            assertNotNull(items);
            // If online, it should have fetched 3 items
            if (!items.isEmpty()) {
                assertTrue(items.size() <= 5);
                for (NewsItem item : items) {
                    assertNotNull(item.getTitle());
                    assertNotNull(item.getDescription());
                    assertNotNull(item.getDate());
                    assertNotNull(item.getLink());
                }
            }
        } catch (Exception e) {
            // Offline or server is down: verify that the exception message is clean and matches expectation
            assertTrue(e.getMessage().contains("Keine aktuellen Nachrichten") || e.getMessage().contains("HTTP status"));
        }
    }
}
