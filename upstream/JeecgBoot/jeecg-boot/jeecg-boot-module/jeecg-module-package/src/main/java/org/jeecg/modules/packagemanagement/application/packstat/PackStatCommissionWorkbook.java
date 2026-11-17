package org.jeecg.modules.packagemanagement.application.packstat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Writes the fixed commission sheet: rates, packer, pickup and design counts, then the package detail. */
public final class PackStatCommissionWorkbook {
    private static final int DAYS = PackStatCommission.DAYS.length;
    private static final int AMOUNT = DAYS + 1;
    private static final String CENTERED_MONEY = "_ * #,##0.00_ ;_ * -#,##0.00_ ;_ * \"-\"??_ ;_ @_ ";
    private static final String TOTAL_MONEY = "#,##0.00_ ";

    private PackStatCommissionWorkbook() {}

    public static void write(PackStatCommission.Report report, OutputStream out) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Paint paint = Paint.of(book);
            Sheet sheet = book.createSheet("绩效提成");
            sheet.setColumnWidth(0, 22 * 256);
            for (int i = 1; i <= DAYS; i++) sheet.setColumnWidth(i, 10 * 256);
            sheet.setColumnWidth(AMOUNT, 18 * 256);
            Row title = sheet.createRow(0);
            title.setHeightInPoints(38);
            text(title, 0, report.title(), paint.title);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, DAYS));
            Row days = sheet.createRow(2);
            text(days, 0, "存活时间/日", paint.yellow);
            for (int i = 0; i < DAYS; i++) number(days, i + 1, PackStatCommission.DAYS[i], paint.yellow);
            rate(sheet.createRow(3), "android绩效", PackStatCommission.ANDROID, paint.yellow);
            rate(sheet.createRow(4), "ios绩效", PackStatCommission.IOS, paint.yellow);
            rate(sheet.createRow(5), "提包绩效", PackStatCommission.PICKUP, paint.yellow);
            rate(sheet.createRow(6), "美工/A面", PackStatCommission.DESIGN, paint.yellow);
            int row = section(sheet, 8, "出包人", "Android出包数量", "数量合计", report.android(), PackStatCommission.ANDROID, paint);
            row = section(sheet, row + 1, "出包人", "iOS出包数量", "数量合计", report.ios(), PackStatCommission.IOS, paint);
            row = section(sheet, row + 1, "出包人", "提包数量", "提包数量合计", report.pickup(), PackStatCommission.PICKUP, paint);
            row = section(sheet, row + 1, "姓名", "美工/A面", "美工/A面数量合计", report.design(), PackStatCommission.DESIGN, paint);
            Row total = sheet.createRow(row + 1);
            total.setHeightInPoints(32);
            text(total, 0, "总合计", paint.totalLabel);
            number(total, 1, sectionAmount(report.android()) + sectionAmount(report.ios()) + sectionAmount(report.pickup()) + sectionAmount(report.design()), paint.totalMoney);
            detail(sheet, row + 3, report.details(), paint);
            book.write(out);
        }
    }

    private static void rate(Row row, String label, int[] rates, CellStyle yellow) {
        text(row, 0, label, yellow);
        for (int i = 0; i < rates.length; i++) number(row, i + 1, rates[i], yellow);
    }

    private static int section(Sheet sheet, int start, String nameTitle, String heading, String countTitle, List<PackStatCommission.Person> people, int[] rates, Paint paint) {
        Row head = sheet.createRow(start);
        text(head, 0, nameTitle, paint.yellow);
        for (int i = 1; i <= DAYS; i++) text(head, i, i == 1 ? heading : "", paint.yellow);
        sheet.addMergedRegion(new CellRangeAddress(start, start, 1, DAYS));
        text(head, AMOUNT, "绩效金额", paint.yellow);
        int row = start + 1;
        int[] counts = new int[DAYS];
        int[] money = new int[DAYS];
        for (PackStatCommission.Person person : people) {
            Row line = sheet.createRow(row++);
            text(line, 0, person.packer(), paint.yellow);
            for (int i = 0; i < person.counts().length; i++) {
                if (person.counts()[i] > 0) number(line, i + 1, person.counts()[i], paint.count);
                counts[i] += person.counts()[i];
                money[i] += person.counts()[i] * rates[i];
            }
            number(line, AMOUNT, person.amount(), paint.yellowMoney);
        }
        Row countRow = sheet.createRow(row++);
        text(countRow, 0, countTitle, paint.green);
        for (int i = 0; i < counts.length; i++) number(countRow, i + 1, counts[i], paint.green);
        text(countRow, AMOUNT, "本月合计", paint.orange);
        Row moneyRow = sheet.createRow(row++);
        text(moneyRow, 0, "绩效金额合计", paint.green);
        for (int i = 0; i < money.length; i++) number(moneyRow, i + 1, money[i], paint.greenMoney);
        number(moneyRow, AMOUNT, sectionAmount(people), paint.orangeMoney);
        return row;
    }

    private static int sectionAmount(List<PackStatCommission.Person> people) {
        int sum = 0;
        for (PackStatCommission.Person person : people) sum += person.amount();
        return sum;
    }

    private static void detail(Sheet sheet, int start, List<PackStatCommission.Detail> details, Paint paint) {
        String[] headers = {"商店链接", "客户", "是否补包", "补包链接", "出包人", "客户使用日期", "掉包日期", "存活天数", "分类", "系统"};
        Row head = sheet.createRow(start);
        head.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) text(head, i, headers[i], paint.detailHead);
        int row = start + 1;
        for (PackStatCommission.Detail item : details) {
            Row line = sheet.createRow(row++);
            line.setHeightInPoints(36);
            text(line, 0, item.storeUrl(), paint.link);
            text(line, 1, item.customer(), paint.detail);
            text(line, 2, item.replenish() ? "是" : "否", paint.detail);
            text(line, 3, item.replenishUrl(), item.replenishUrl() == null || item.replenishUrl().isBlank() ? paint.detail : paint.link);
            text(line, 4, item.packer(), paint.detail);
            text(line, 5, item.useDate() == null ? "" : item.useDate().toString(), paint.detail);
            text(line, 6, item.dropDate() == null ? "" : item.dropDate().toString(), paint.detail);
            if (item.days() != null) number(line, 7, item.days(), paint.detail);
            else text(line, 7, "", paint.detail);
            text(line, 8, item.category(), paint.detail);
            text(line, 9, "ios".equals(item.platform()) ? "iOS" : "android".equals(item.platform()) ? "Android" : item.platform(), paint.detail);
        }
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void number(Row row, int column, double value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static final class Paint {
        final CellStyle title;
        final CellStyle yellow;
        final CellStyle yellowMoney;
        final CellStyle count;
        final CellStyle green;
        final CellStyle greenMoney;
        final CellStyle orange;
        final CellStyle orangeMoney;
        final CellStyle totalLabel;
        final CellStyle totalMoney;
        final CellStyle detailHead;
        final CellStyle detail;
        final CellStyle link;

        private Paint(CellStyle title, CellStyle yellow, CellStyle yellowMoney, CellStyle count, CellStyle green, CellStyle greenMoney,
                CellStyle orange, CellStyle orangeMoney, CellStyle totalLabel, CellStyle totalMoney, CellStyle detailHead, CellStyle detail, CellStyle link) {
            this.title = title;
            this.yellow = yellow;
            this.yellowMoney = yellowMoney;
            this.count = count;
            this.green = green;
            this.greenMoney = greenMoney;
            this.orange = orange;
            this.orangeMoney = orangeMoney;
            this.totalLabel = totalLabel;
            this.totalMoney = totalMoney;
            this.detailHead = detailHead;
            this.detail = detail;
            this.link = link;
        }

        static Paint of(XSSFWorkbook book) {
            XSSFFont titleFont = font(book, "宋体", 24, null);
            XSSFFont body = font(book, "宋体", 10, null);
            XSSFFont head = font(book, "Microsoft YaHei", 10, 0xFFFFFF);
            XSSFFont linkFont = font(book, "Microsoft YaHei", 10, 0x175CEB);
            linkFont.setUnderline(Font.U_SINGLE);
            CellStyle title = box(book, titleFont, HorizontalAlignment.CENTER, null, false);
            CellStyle yellow = box(book, body, HorizontalAlignment.CENTER, 0xFFFF00, true);
            CellStyle yellowMoney = money(book, body, HorizontalAlignment.CENTER, 0xFFFF00, CENTERED_MONEY);
            CellStyle count = box(book, body, HorizontalAlignment.CENTER, null, true);
            CellStyle green = box(book, body, HorizontalAlignment.CENTER, 0x92D050, true);
            CellStyle greenMoney = money(book, body, HorizontalAlignment.CENTER, 0x92D050, CENTERED_MONEY);
            CellStyle orange = box(book, body, HorizontalAlignment.CENTER, 0xFFC000, true);
            CellStyle orangeMoney = money(book, body, HorizontalAlignment.CENTER, 0xFFC000, CENTERED_MONEY);
            CellStyle totalLabel = box(book, body, HorizontalAlignment.CENTER, 0xF8CBAD, true);
            CellStyle totalMoney = money(book, body, HorizontalAlignment.RIGHT, 0xF8CBAD, TOTAL_MONEY);
            CellStyle detailHead = box(book, head, HorizontalAlignment.CENTER, 0x277C4F, false);
            medium(detailHead, 0xCCCCCC);
            detailHead.setWrapText(true);
            CellStyle detail = box(book, body, HorizontalAlignment.CENTER, 0xFFFFFF, false);
            medium(detail, 0xCCCCCC);
            detail.setWrapText(true);
            CellStyle link = box(book, linkFont, HorizontalAlignment.CENTER, 0xFFFFFF, false);
            medium(link, 0xCCCCCC);
            link.setWrapText(true);
            return new Paint(title, yellow, yellowMoney, count, green, greenMoney, orange, orangeMoney, totalLabel, totalMoney, detailHead, detail, link);
        }

        private static XSSFFont font(XSSFWorkbook book, String name, int size, Integer color) {
            XSSFFont font = book.createFont();
            font.setFontName(name);
            font.setFontHeightInPoints((short) size);
            if (color != null) font.setColor(rgb(color));
            return font;
        }

        private static XSSFCellStyle box(XSSFWorkbook book, XSSFFont font, HorizontalAlignment align, Integer fill, boolean thin) {
            XSSFCellStyle style = book.createCellStyle();
            style.setFont(font);
            style.setAlignment(align);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            if (fill != null) {
                style.setFillForegroundColor(rgb(fill));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (thin) {
                style.setBorderTop(BorderStyle.THIN);
                style.setBorderBottom(BorderStyle.THIN);
                style.setBorderLeft(BorderStyle.THIN);
                style.setBorderRight(BorderStyle.THIN);
            }
            return style;
        }

        private static XSSFCellStyle money(XSSFWorkbook book, XSSFFont font, HorizontalAlignment align, int fill, String format) {
            XSSFCellStyle style = box(book, font, align, fill, true);
            style.setDataFormat(book.createDataFormat().getFormat(format));
            return style;
        }

        private static void medium(CellStyle style, int border) {
            XSSFCellStyle cell = (XSSFCellStyle) style;
            XSSFColor color = rgb(border);
            cell.setBorderTop(BorderStyle.MEDIUM);
            cell.setBorderBottom(BorderStyle.MEDIUM);
            cell.setBorderLeft(BorderStyle.MEDIUM);
            cell.setBorderRight(BorderStyle.MEDIUM);
            cell.setTopBorderColor(color);
            cell.setBottomBorderColor(color);
            cell.setLeftBorderColor(color);
            cell.setRightBorderColor(color);
        }

        private static XSSFColor rgb(int color) {
            return new XSSFColor(new byte[] {(byte) (color >> 16), (byte) (color >> 8), (byte) color}, null);
        }
    }
}
