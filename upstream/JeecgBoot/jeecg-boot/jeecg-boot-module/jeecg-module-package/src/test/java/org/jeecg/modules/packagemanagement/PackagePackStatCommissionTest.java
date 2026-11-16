package org.jeecg.modules.packagemanagement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.application.packstat.PackStatCommission;
import org.jeecg.modules.packagemanagement.application.packstat.PackStatCommissionWorkbook;
import org.jeecg.modules.packagemanagement.application.packstat.PackStatService;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageActor;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.PackStatMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.PackStatRegionMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PackagePackStatCommissionTest {
    private static PackStatCommission.Source row(String platform, String packer, LocalDate use, LocalDate drop) {
        return new PackStatCommission.Source(platform, packer, "https://example.com/" + packer, "客户", false, null, use, drop);
    }

    @Test void survivalDayUsesCustomerUseDateAndPutsFifteenOrMoreIntoDayFifteen() {
        LocalDate use = LocalDate.of(2026, 11, 1);
        assertEquals(7, PackStatCommission.survivalDays(use, LocalDate.of(2026, 11, 8), LocalDate.of(2026, 12, 1)));
        assertEquals(8, PackStatCommission.survivalDays(use, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 1)));
        assertEquals(15, PackStatCommission.bucket(15));
        assertEquals(15, PackStatCommission.bucket(30));
        assertNull(PackStatCommission.bucket(6));
        PackStatCommission.Report report = PackStatCommission.build("公司", "", YearMonth.of(2026, 11), LocalDate.of(2026, 12, 1), List.of(
                row("android", "黄汉锦", use, LocalDate.of(2026, 11, 9)),
                row("android", "黄汉锦", use, LocalDate.of(2026, 11, 6)),
                row("android", "吴泽浩", use, LocalDate.of(2026, 11, 20)),
                row("ios", "黄光普", use, null)));
        assertEquals("公司2026年11月绩效提成表", report.title());
        assertEquals("包统计绩效-公司-2026-11.xlsx", report.fileName());
        PackStatCommission.Person huang = report.android().stream().filter(person -> "黄汉锦".equals(person.packer())).findFirst().orElseThrow();
        PackStatCommission.Person wu = report.android().stream().filter(person -> "吴泽浩".equals(person.packer())).findFirst().orElseThrow();
        assertEquals(1, huang.counts()[1]);
        assertEquals(400, huang.amount());
        assertEquals(1, wu.counts()[8]);
        assertEquals(1200, wu.amount());
        assertEquals(2000, report.ios().get(0).amount());
        assertTrue(report.details().stream().anyMatch(item -> Integer.valueOf(5).equals(item.days())));
    }

    @Test void workbookShowsPackerCountsRatesAndUnpaidDetail() throws Exception {
        LocalDate use = LocalDate.of(2026, 11, 1);
        PackStatCommission.Report report = PackStatCommission.build("CT", "黄汉锦", YearMonth.of(2026, 11), LocalDate.of(2026, 11, 20), List.of(
                row("android", "黄汉锦", use, LocalDate.of(2026, 11, 8)),
                row("android", "黄汉锦", use, LocalDate.of(2026, 11, 6))));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PackStatCommissionWorkbook.write(report, out);
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            var sheet = book.getSheetAt(0);
            assertEquals("CT2026年11月绩效提成表-黄汉锦", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("android绩效", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals(300, sheet.getRow(3).getCell(1).getNumericCellValue());
            assertEquals("出包人", sheet.getRow(8).getCell(0).getStringCellValue());
            assertEquals("黄汉锦", sheet.getRow(9).getCell(0).getStringCellValue());
            assertEquals(1, sheet.getRow(9).getCell(1).getNumericCellValue());
            assertEquals(300, sheet.getRow(9).getCell(10).getNumericCellValue());
            boolean unpaid = false;
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                var cell = sheet.getRow(i) == null ? null : sheet.getRow(i).getCell(7);
                if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC && cell.getNumericCellValue() == 5) unpaid = true;
            }
            assertTrue(unpaid);
        }
    }

    @Test void exportQueryRequiresCategoryAndMonthAndKeepsTheChosenPacker() {
        PackStatMapper mapper = mock(PackStatMapper.class);
        PackStatService service = new PackStatService(mapper, mock(PackStatRegionMapper.class));
        PackageActor actor = new PackageActor(8, "alice", 11, false);
        PackStat stored = new PackStat();
        stored.setPlatform("android");
        stored.setPacker("黄汉锦");
        stored.setCustomerUseDate(java.sql.Date.valueOf(LocalDate.of(2026, 11, 1)));
        stored.setDropDate(java.sql.Date.valueOf(LocalDate.of(2026, 11, 8)));
        when(mapper.selectPackStatList(any())).thenReturn(List.of(stored));
        PackStatCommission.Report report = service.commission("2026-11", "公司", " 黄汉锦 ", actor);
        assertEquals(300, report.android().get(0).amount());
        var captor = org.mockito.ArgumentCaptor.forClass(PackStat.class);
        verify(mapper).selectPackStatList(captor.capture());
        assertEquals("2026-11", captor.getValue().getUseMonth());
        assertEquals("公司", captor.getValue().getCustomerCategory());
        assertEquals("黄汉锦", captor.getValue().getPacker());
        assertEquals(8, captor.getValue().getTenantId());
        assertThrows(PackageFailure.class, () -> service.commission("2026-11", "其他", null, actor));
        assertThrows(PackageFailure.class, () -> service.commission("2026/11", "公司", null, actor));
    }
}
