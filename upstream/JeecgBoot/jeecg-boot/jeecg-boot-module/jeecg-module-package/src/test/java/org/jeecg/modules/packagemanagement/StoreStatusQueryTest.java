package org.jeecg.modules.packagemanagement;

import org.jeecg.modules.packagemanagement.application.store.StoreStatusQuery;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoreStatusQueryTest {
    @Test
    void iosSelectionStaysSeparateUntilAGenericStatusAlreadyCoversIt() {
        StoreStatus row = new StoreStatus();
        row.setStatus(1);
        row.setStatusQuery("1,ios:2,ios:1");
        StoreStatusQuery.apply(row);
        assertNull(row.getStatus());
        assertEquals(List.of(1), row.getStatusIn());
        assertEquals(List.of(2), row.getIosStatusIn());
    }

    @Test
    void namedIosProductStatusDoesNotCollapseToTheThreeListingCodes() {
        StoreStatus row = new StoreStatus();
        row.setStatusQuery("ios:已下架,ios:预购");
        StoreStatusQuery.apply(row);
        assertEquals(List.of("已下架", "预购"), row.getIosProductIn());
        assertEquals(List.of(2), row.getIosLegacyIn());
    }

    @Test
    void unknownStatusIsRejected() {
        StoreStatus row = new StoreStatus();
        row.setStatusQuery("ios:准备提交");
        PackageFailure failure = assertThrows(PackageFailure.class, () -> StoreStatusQuery.apply(row));
        assertEquals(400, failure.getCode());
    }
}
