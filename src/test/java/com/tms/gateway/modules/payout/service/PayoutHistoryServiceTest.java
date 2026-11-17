package com.tms.gateway.modules.payout.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.modules.payout.dto.PayoutListItemDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayoutHistoryServiceTest {

    private final PayoutHistoryService service = new PayoutHistoryService(new MockPayoutDataProvider());

    @Test
    @SuppressWarnings("unchecked")
    void listDefaultsToFiveScreenRows() {
        Map<String, Object> response = service.list(42L, null, null, null, null, 1, 5);
        List<PayoutListItemDto> data = (List<PayoutListItemDto>) response.get("data");
        Map<String, Object> meta = (Map<String, Object>) response.get("meta");

        assertEquals(5, data.size());
        assertEquals(50L, meta.get("total"));
        assertEquals(10, meta.get("last_page"));
        assertEquals("Access Bank", data.getFirst().receivingBank());
        assertEquals("Completed", data.getFirst().statusDisplay());
        assertEquals("b5e4b374-5cb1-4138-8dff-fdd2d3de8ca9", data.getFirst().paymentReference());
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersByStatusSearchAndSalesDate() {
        Map<String, Object> response = service.list(42L, "309022002909209006", "PENDING", "14/03/2025", "14/03/2025",
                1, 5);
        List<PayoutListItemDto> data = (List<PayoutListItemDto>) response.get("data");
        Map<String, Object> meta = (Map<String, Object>) response.get("meta");

        assertEquals(1, data.size());
        assertEquals("pending", data.getFirst().status());
        assertEquals(1L, meta.get("total"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageBeyondEndReturnsEmptyRowsAndInvalidDateIsRejected() {
        Map<String, Object> response = service.list(42L, null, null, null, null, Integer.MAX_VALUE, 5);
        List<PayoutListItemDto> data = (List<PayoutListItemDto>) response.get("data");
        Map<String, Object> meta = (Map<String, Object>) response.get("meta");

        assertEquals(0, data.size());
        assertEquals(50L, meta.get("total"));
        assertNull(meta.get("from"));
        assertNull(meta.get("to"));
        assertThrows(IllegalArgumentException.class,
                () -> service.list(42L, null, null, "31/02/2025", null, 1, 5));
        assertThrows(IllegalArgumentException.class,
                () -> service.list(42L, null, null, "2025-03-16", "2025-03-14", 1, 5));
        assertThrows(IllegalArgumentException.class,
                () -> service.list(42L, null, null, null, "999999999-12-31", 1, 5));
    }

    @Test
    @SuppressWarnings("unchecked")
    void largePageSizeDoesNotOverflowTheResultWindow() {
        Map<String, Object> response = service.list(42L, null, null, null, null, 1, Integer.MAX_VALUE);
        List<PayoutListItemDto> data = (List<PayoutListItemDto>) response.get("data");
        Map<String, Object> meta = (Map<String, Object>) response.get("meta");

        assertEquals(50, data.size());
        assertEquals(50L, meta.get("to"));
    }

    @Test
    void detailIncludesReceivingBankFieldsAndReferenceLookup() {
        var detail = service.getDetail(42L, "b5e4b374-5cb1-4138-8dff-fdd2d3de8ca9");

        assertEquals("payout-001", detail.payoutId());
        assertEquals("Completed", detail.statusDisplay());
        assertEquals("Access Bank", detail.receivingBank().bankName());
        assertEquals("John Doe", detail.receivingBank().accountName());
        assertEquals("0029299292", detail.receivingBank().accountNumber());
        assertEquals(Instant.parse("2025-03-15T07:20:00Z"), detail.dateTimeSettled());
    }

    @Test
    void exportIncludesAllMatchingRowsAndCsvHeaders() {
        String csv = service.exportCsv(42L, null, "completed", null, null);

        assertTrue(csv.startsWith(
                "\"Payment Reference\",\"Total Value\",\"Payout Value\",\"Receiving Bank\",\"Status\",\"Sales Date\",\"Date, Time Settled\"\r\n"));
        assertEquals(41, csv.lines().count());
        assertTrue(csv.contains("\"Access Bank\",\"Completed\""));
    }

    @Test
    void rejectsUnsupportedFiltersAndReturnsNotFoundForUnknownPayout() {
        assertThrows(IllegalArgumentException.class,
                () -> service.list(42L, null, "processed", null, null, 1, 5));
        assertThrows(IllegalArgumentException.class,
                () -> service.list(42L, null, null, null, null, 0, 5));
        assertThrows(AppException.class, () -> service.getDetail(42L, "unknown"));
    }
}
