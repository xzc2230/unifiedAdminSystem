package org.jeecg.modules.packagemanagement.application.packstat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Writes the fixed commission sheet: rates, packer, pickup and design counts, then the package detail. */
public final class PackStatCommissionWorkbook {
    private PackStatCommissionWorkbook() {}

    public static void write(PackStatCommission.Report report, OutputStream out) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("绩效提成");
            sheet.setColumnWidth(0, 24 * 256);
            for (int i = 1; i <= 9; i++) sheet.setColumnWidth(i, 10 * 256);
            sheet.setColumnWidth(10, 14 * 256);
            Row title = sheet.createRow(0);
            text(title, 0, report.title());
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));
            Row days = sheet.createRow(2);
            text(days, 0, "存活时间/日");
            for (int i = 0; i < PackStatCommission.DAYS.length; i++) number(days, i + 1, PackStatCommission.DAYS[i]);
            rate(sheet.createRow(3), "android绩效", PackStatCommission.ANDROID);
            rate(sheet.createRow(4), "ios绩效", PackStatCommission.IOS);
            rate(sheet.createRow(5), "提包绩效", PackStatCommission.PICKUP);
            rate(sheet.createRow(6), "美工/A面", PackStatCommission.DESIGN);
            int row = section(sheet, 8, "出包人", "Android出包数量", "数量合计", report.android(), PackStatCommission.ANDROID);
            row = section(sheet, row + 1, "出包人", "iOS出包数量", "数量合计", report.ios(), PackStatCommission.IOS);
            row = section(sheet, row + 1, "姓名", "提包数量", "提包数量合计", report.pickup(), PackStatCommission.PICKUP);
            row = section(sheet, row + 1, "姓名", "美工/A面", "美工/A面数量合计", report.design(), PackStatCommission.DESIGN);
            Row total = sheet.createRow(row + 1);
            text(total, 0, "总合计");
            number(total, 10, sectionAmount(report.android()) + sectionAmount(report.ios()) + sectionAmount(report.pickup()) + sectionAmount(report.design()));
            detail(sheet, row + 3, report.details());
            book.write(out);
        }
    }

    private static void rate(Row row, String label, int[] rates) {
        text(row, 0, label);
        for (int i = 0; i < rates.length; i++) number(row, i + 1, rates[i]);
    }

    private static int section(Sheet sheet, int start, String nameTitle, String heading, String countTitle, List<PackStatCommission.Person> people, int[] rates) {
        Row head = sheet.createRow(start);
        text(head, 0, nameTitle);
        text(head, 1, heading);
        sheet.addMergedRegion(new CellRangeAddress(start, start, 1, 9));
        text(head, 10, "绩效金额");
        int row = start + 1;
        int[] counts = new int[PackStatCommission.DAYS.length];
        int[] money = new int[PackStatCommission.DAYS.length];
        for (PackStatCommission.Person person : people) {
            Row line = sheet.createRow(row++);
            text(line, 0, person.packer());
            for (int i = 0; i < person.counts().length; i++) {
                if (person.counts()[i] > 0) number(line, i + 1, person.counts()[i]);
                counts[i] += person.counts()[i];
                money[i] += person.counts()[i] * rates[i];
            }
            number(line, 10, person.amount());
        }
        Row countRow = sheet.createRow(row++);
        text(countRow, 0, countTitle);
        for (int i = 0; i < counts.length; i++) number(countRow, i + 1, counts[i]);
        text(countRow, 10, "本月合计");
        Row moneyRow = sheet.createRow(row++);
        text(moneyRow, 0, "绩效金额合计");
        for (int i = 0; i < money.length; i++) number(moneyRow, i + 1, money[i]);
        number(moneyRow, 10, sectionAmount(people));
        return row;
    }

    private static int sectionAmount(List<PackStatCommission.Person> people) {
        int sum = 0;
        for (PackStatCommission.Person person : people) sum += person.amount();
        return sum;
    }

    private static void detail(Sheet sheet, int start, List<PackStatCommission.Detail> details) {
        String[] headers = {"商店链接", "客户", "是否补包", "补包链接", "出包人", "客户使用日期", "掉包日期", "存活天数", "分类", "系统"};
        Row head = sheet.createRow(start);
        for (int i = 0; i < headers.length; i++) text(head, i, headers[i]);
        int row = start + 1;
        for (PackStatCommission.Detail item : details) {
            Row line = sheet.createRow(row++);
            text(line, 0, item.storeUrl());
            text(line, 1, item.customer());
            text(line, 2, item.replenish() ? "是" : "否");
            text(line, 3, item.replenishUrl());
            text(line, 4, item.packer());
            text(line, 5, item.useDate() == null ? "" : item.useDate().toString());
            text(line, 6, item.dropDate() == null ? "" : item.dropDate().toString());
            if (item.days() != null) number(line, 7, item.days());
            text(line, 8, item.category());
            text(line, 9, "ios".equals(item.platform()) ? "iOS" : "android".equals(item.platform()) ? "Android" : item.platform());
        }
    }

    private static void text(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private static void number(Row row, int column, int value) {
        row.createCell(column).setCellValue(value);
    }
}
