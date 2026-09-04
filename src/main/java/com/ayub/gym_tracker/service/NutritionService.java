package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.NutritionRequest;
import com.ayub.gym_tracker.dto.NutritionSaveResult;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.InsertDimensionRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ayub.gym_tracker.dto.DailyTargetsResponse;
import com.ayub.gym_tracker.dto.NutritionResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class NutritionService {

    private final Sheets sheets;
    private final String spreadsheetId;
    private static final String WORKOUT_RANGE = "Tracker!J4:U";
    private static final int MAX_SETS = 3;
    private static final String TARGETS_RANGE = "Tracker!A5:D5";
    private static final String NUTRITION_READ_RANGE =
            "Tracker!A9:H1000";

    public NutritionService(
            Sheets sheets,
            @Value("${tracker.spreadsheet-id}") String spreadsheetId
    ) {
        this.sheets = sheets;
        this.spreadsheetId = spreadsheetId;
    }

    public List<List<Object>> readTracker() throws IOException {
        ValueRange response = sheets.spreadsheets()
                .values()
                .get(spreadsheetId, "Tracker!A1:U10")
                .execute();

        return response.getValues() == null
                ? List.of()
                : response.getValues();
    }
    private static final int FIRST_NUTRITION_ROW = 9;
    private static final String TRACKER_SHEET = "Tracker";

    public NutritionSaveResult saveNutrition(
            NutritionRequest request
    ) throws IOException {
        ValueRange response = sheets.spreadsheets()
                .values()
                .get(spreadsheetId, "Tracker!A9:B1000")
                .execute();

        List<List<Object>> rows = response.getValues() == null
                ? List.of()
                : response.getValues();

        Integer existingRow = findNutritionRow(rows, request.date());

        if (existingRow != null) {
            updateNutritionValues(existingRow, request);
            return new NutritionSaveResult(existingRow, false);
        }

        LocalDate dayOneDate = findDayOneDate(rows);

        long difference = ChronoUnit.DAYS.between(
                dayOneDate,
                request.date()
        );

        if (difference < 0) {
            throw new IllegalArgumentException(
                    "Date cannot be before " + dayOneDate
            );
        }

        int dayNumber = Math.toIntExact(difference) + 1;
        int targetRow = FIRST_NUTRITION_ROW + dayNumber - 1;
        Integer protectedSectionRow = findFirstNonNutritionRow(rows);

        if (protectedSectionRow != null && targetRow >= protectedSectionRow) {
            int rowsRequired = targetRow - protectedSectionRow + 1;
            insertRowsBefore(protectedSectionRow, rowsRequired);
        }

        writeNewNutritionRow(targetRow, dayNumber, request);

        return new NutritionSaveResult(targetRow, true);
    }

    private Integer findNutritionRow(
            List<List<Object>> rows,
            LocalDate requestedDate
    ) {
        for (int index = 0; index < rows.size(); index++) {
            List<Object> row = rows.get(index);

            if (row.size() > 1
                    && requestedDate.toString().equals(row.get(1).toString())) {
                return FIRST_NUTRITION_ROW + index;
            }
        }

        return null;
    }

    private LocalDate findDayOneDate(List<List<Object>> rows) {
        for (List<Object> row : rows) {
            if (row.size() > 1
                    && "Day 1".equalsIgnoreCase(row.get(0).toString())) {
                return LocalDate.parse(row.get(1).toString());
            }
        }

        throw new IllegalStateException("Could not find Day 1 in the tracker.");
    }

    private Integer findFirstNonNutritionRow(List<List<Object>> rows) {
        for (int index = 0; index < rows.size(); index++) {
            List<Object> row = rows.get(index);

            if (row.isEmpty()) {
                continue;
            }

            String firstCell = row.get(0).toString().trim();

            if (firstCell.isBlank()) {
                continue;
            }

            boolean isNutritionDay =
                    firstCell.matches("(?i)Day\\s+\\d+");

            if (!isNutritionDay) {
                return FIRST_NUTRITION_ROW + index;
            }
        }

        return null;
    }

    private void updateNutritionValues(
            int row,
            NutritionRequest request
    ) throws IOException {
        updateRange(
                "Tracker!C" + row + ":F" + row,
                List.of(
                        request.calories(),
                        request.proteinG(),
                        request.carbsG(),
                        request.fatG()
                )
        );

        if (request.weightKg() != null) {
            updateRange(
                    "Tracker!G" + row,
                    List.of(request.weightKg())
            );
        }

        if (request.notes() != null && !request.notes().isBlank()) {
            updateRange(
                    "Tracker!H" + row,
                    List.of(request.notes().trim())
            );
        }
    }

    private void writeNewNutritionRow(
            int row,
            int dayNumber,
            NutritionRequest request
    ) throws IOException {
        updateRange(
                "Tracker!A" + row + ":H" + row,
                List.of(
                        "Day " + dayNumber,
                        request.date().toString(),
                        request.calories(),
                        request.proteinG(),
                        request.carbsG(),
                        request.fatG(),
                        valueOrBlank(request.weightKg()),
                        valueOrBlank(request.notes())
                )
        );
    }

    private void insertRowsBefore(
            int spreadsheetRow,
            int numberOfRows
    ) throws IOException {
        int sheetId = findTrackerSheetId();

        DimensionRange range = new DimensionRange()
                .setSheetId(sheetId)
                .setDimension("ROWS")
                .setStartIndex(spreadsheetRow - 1)
                .setEndIndex(spreadsheetRow - 1 + numberOfRows);

        InsertDimensionRequest insertion = new InsertDimensionRequest()
                .setRange(range)
                .setInheritFromBefore(true);

        Request request = new Request()
                .setInsertDimension(insertion);

        BatchUpdateSpreadsheetRequest body =
                new BatchUpdateSpreadsheetRequest()
                        .setRequests(List.of(request));

        sheets.spreadsheets()
                .batchUpdate(spreadsheetId, body)
                .execute();
    }

    private int findTrackerSheetId() throws IOException {
        Spreadsheet spreadsheet = sheets.spreadsheets()
                .get(spreadsheetId)
                .setFields("sheets(properties(sheetId,title))")
                .execute();

        return spreadsheet.getSheets()
                .stream()
                .filter(sheet -> TRACKER_SHEET.equals(
                        sheet.getProperties().getTitle()
                ))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Tracker sheet not found.")
                )
                .getProperties()
                .getSheetId();
    }

    private void updateRange(
            String range,
            List<Object> values
    ) throws IOException {
        ValueRange body = new ValueRange()
                .setValues(List.of(values));

        sheets.spreadsheets()
                .values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    public DailyTargetsResponse getDailyTargets()
            throws IOException {
        ValueRange response = sheets.spreadsheets()
                .values()
                .get(spreadsheetId, TARGETS_RANGE)
                .setValueRenderOption("FORMATTED_VALUE")
                .execute();

        if (response.getValues() == null
                || response.getValues().isEmpty()) {
            throw new IllegalStateException(
                    "Daily targets could not be found."
            );
        }

        List<Object> row = response.getValues().get(0);

        return new DailyTargetsResponse(
                integerValue(row, 0),
                integerValue(row, 1),
                integerValue(row, 2),
                integerValue(row, 3)
        );
    }

    public NutritionResponse getNutritionByDate(
            LocalDate requestedDate
    ) throws IOException {
        ValueRange response = sheets.spreadsheets()
                .values()
                .get(spreadsheetId, NUTRITION_READ_RANGE)
                .setValueRenderOption("FORMATTED_VALUE")
                .execute();

        List<List<Object>> rows = response.getValues() == null
                ? List.of()
                : response.getValues();

        for (List<Object> row : rows) {
            if (!requestedDate.toString().equals(
                    cellValue(row, 1)
            )) {
                continue;
            }

            int day = Integer.parseInt(
                    cellValue(row, 0)
                            .replaceFirst("(?i)^Day\\s+", "")
            );

            return new NutritionResponse(
                    day,
                    integerValue(row, 2),
                    integerValue(row, 3),
                    integerValue(row, 4),
                    integerValue(row, 5),
                    decimalOrNull(row, 6),
                    stringOrNull(row, 7)
            );
        }

        return null;
    }
    private String cellValue(
            List<Object> row,
            int index
    ) {
        if (index >= row.size() || row.get(index) == null) {
            return "";
        }

        return row.get(index).toString().trim();
    }

    private int integerValue(
            List<Object> row,
            int index
    ) {
        return new BigDecimal(
                cellValue(row, index).replace(",", "")
        ).intValueExact();
    }

    private BigDecimal decimalOrNull(
            List<Object> row,
            int index
    ) {
        String value = cellValue(row, index);

        if (value.isBlank()) {
            return null;
        }

        return new BigDecimal(value.replace(",", ""));
    }

    private String stringOrNull(
            List<Object> row,
            int index
    ) {
        String value = cellValue(row, index);
        return value.isBlank() ? null : value;
    }
    private Object valueOrBlank(Object value) {
        return value == null ? "" : value;
    }

}