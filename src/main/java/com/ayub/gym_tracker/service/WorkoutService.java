package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.ExerciseRequest;
import com.ayub.gym_tracker.dto.WorkoutRequest;
import com.ayub.gym_tracker.dto.WorkoutSaveResult;
import com.ayub.gym_tracker.dto.WorkoutSetRequest;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DeleteRangeRequest;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.InsertRangeRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.ayub.gym_tracker.dto.ExerciseResponse;
import com.ayub.gym_tracker.dto.WorkoutResponse;
import com.ayub.gym_tracker.dto.WorkoutSetResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkoutService {

    private static final String TRACKER_SHEET = "Tracker";

    private static final String WORKOUT_APPEND_RANGE =
            "Tracker!J4:AA";

    private static final String WORKOUT_LOOKUP_RANGE =
            "Tracker!J5:K1000";

    private static final String WORKOUT_READ_RANGE =
            "Tracker!J5:AA1000";

    private static final int FIRST_WORKOUT_ROW = 5;

    // Google Sheets column indexes are zero-based.
    // J = 9 and AA's exclusive ending index = 27.
    private static final int WORKOUT_START_COLUMN_INDEX = 9;

    // AA is index 26, so the exclusive ending index is 27.
    private static final int WORKOUT_END_COLUMN_INDEX = 27;

    private static final int MAX_SETS = 6;

    private final Sheets sheets;
    private final String spreadsheetId;

    public WorkoutService(
            Sheets sheets,
            @Value("${tracker.spreadsheet-id}") String spreadsheetId
    ) {
        this.sheets = sheets;
        this.spreadsheetId = spreadsheetId;
    }

    public WorkoutSaveResult saveWorkout(
            WorkoutRequest request
    ) throws IOException {
        List<List<Object>> newRows = new ArrayList<>();

        for (ExerciseRequest exercise : request.exercises()) {
            if (exercise.sets().size() > MAX_SETS) {
                throw new IllegalArgumentException(
                        exercise.name()
                                + " cannot contain more than "
                                + MAX_SETS
                                + " sets."
                );
            }

            newRows.add(createWorkoutRow(request, exercise));
        }

        List<Integer> existingRows =
                findExistingWorkoutRows(request);

        if (existingRows.isEmpty()) {
            String updatedRange = appendWorkoutRows(newRows);

            return new WorkoutSaveResult(
                    newRows.size(),
                    updatedRange,
                    true
            );
        }

        ensureRowsAreContiguous(existingRows);

        int firstExistingRow = existingRows.get(0);

        resizeWorkoutBlock(
                firstExistingRow,
                existingRows.size(),
                newRows.size()
        );

        String updatedRange =
                updateWorkoutRows(firstExistingRow, newRows);

        return new WorkoutSaveResult(
                newRows.size(),
                updatedRange,
                false
        );
    }

    private List<Object> createWorkoutRow(
            WorkoutRequest workout,
            ExerciseRequest exercise
    ) {
        List<Object> row = new ArrayList<>();

        row.add(workout.date().toString());
        row.add(workout.workout().trim());
        row.add(exercise.name().trim());

        int totalReps = 0;

        for (int index = 0; index < MAX_SETS; index++) {
            if (index < exercise.sets().size()) {
                WorkoutSetRequest set = exercise.sets().get(index);

                row.add(weightOrBodyweight(set.weightKg()));
                row.add(set.reps());

                totalReps += set.reps();
            } else {
                row.add("");
                row.add("");
            }
        }

        row.add(totalReps);
        row.add(valueOrBlank(exercise.notes()));
        row.add(workout.day());

        return row;
    }

    private List<Integer> findExistingWorkoutRows(
            WorkoutRequest request
    ) throws IOException {
        ValueRange response = sheets.spreadsheets()
                .values()
                .get(spreadsheetId, WORKOUT_LOOKUP_RANGE)
                .setValueRenderOption("FORMATTED_VALUE")
                .execute();

        List<List<Object>> rows = response.getValues() == null
                ? List.of()
                : response.getValues();

        List<Integer> matchingRows = new ArrayList<>();

        String expectedDate = request.date().toString();
        String expectedWorkout =
                normalizeWorkoutName(request.workout());

        for (int index = 0; index < rows.size(); index++) {
            List<Object> row = rows.get(index);

            if (row.size() < 2) {
                continue;
            }

            String actualDate =
                    row.get(0).toString().trim();

            String actualWorkout =
                    normalizeWorkoutName(row.get(1).toString());

            if (expectedDate.equals(actualDate)
                    && expectedWorkout.equals(actualWorkout)) {
                matchingRows.add(FIRST_WORKOUT_ROW + index);
            }
        }

        return matchingRows;
    }

    private String appendWorkoutRows(
            List<List<Object>> rows
    ) throws IOException {
        ValueRange body = new ValueRange()
                .setValues(rows);

        AppendValuesResponse response = sheets.spreadsheets()
                .values()
                .append(spreadsheetId, WORKOUT_APPEND_RANGE, body)
                .setValueInputOption("USER_ENTERED")
                .execute();

        return response.getUpdates().getUpdatedRange();
    }

    private void resizeWorkoutBlock(
            int firstRow,
            int oldExerciseCount,
            int newExerciseCount
    ) throws IOException {
        int difference = newExerciseCount - oldExerciseCount;

        if (difference == 0) {
            return;
        }

        int sheetId = findTrackerSheetId();
        Request resizeRequest;

        if (difference > 0) {
            int firstInsertedRow =
                    firstRow + oldExerciseCount;

            GridRange range = createWorkoutGridRange(
                    sheetId,
                    firstInsertedRow,
                    difference
            );

            InsertRangeRequest insertion =
                    new InsertRangeRequest()
                            .setRange(range)
                            .setShiftDimension("ROWS");

            resizeRequest = new Request()
                    .setInsertRange(insertion);
        } else {
            int rowsToDelete = Math.abs(difference);
            int firstDeletedRow =
                    firstRow + newExerciseCount;

            GridRange range = createWorkoutGridRange(
                    sheetId,
                    firstDeletedRow,
                    rowsToDelete
            );

            DeleteRangeRequest deletion =
                    new DeleteRangeRequest()
                            .setRange(range)
                            .setShiftDimension("ROWS");

            resizeRequest = new Request()
                    .setDeleteRange(deletion);
        }

        BatchUpdateSpreadsheetRequest body =
                new BatchUpdateSpreadsheetRequest()
                        .setRequests(List.of(resizeRequest));

        sheets.spreadsheets()
                .batchUpdate(spreadsheetId, body)
                .execute();
    }

    private GridRange createWorkoutGridRange(
            int sheetId,
            int firstSpreadsheetRow,
            int numberOfRows
    ) {
        int startRowIndex = firstSpreadsheetRow - 1;

        return new GridRange()
                .setSheetId(sheetId)
                .setStartRowIndex(startRowIndex)
                .setEndRowIndex(startRowIndex + numberOfRows)
                .setStartColumnIndex(WORKOUT_START_COLUMN_INDEX)
                .setEndColumnIndex(WORKOUT_END_COLUMN_INDEX);
    }

    private String updateWorkoutRows(
            int firstRow,
            List<List<Object>> rows
    ) throws IOException {
        int lastRow = firstRow + rows.size() - 1;

        String range = "Tracker!J"
                + firstRow
                + ":AA"
                + lastRow;

        ValueRange body = new ValueRange()
                .setValues(rows);

        return sheets.spreadsheets()
                .values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute()
                .getUpdatedRange();
    }

    private String normalizeWorkoutName(String workoutName) {
        return workoutName
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private void ensureRowsAreContiguous(
            List<Integer> rows
    ) {
        for (int index = 1; index < rows.size(); index++) {
            int previousRow = rows.get(index - 1);
            int currentRow = rows.get(index);

            if (currentRow != previousRow + 1) {
                throw new IllegalStateException(
                        "Existing workout rows are not contiguous."
                );
            }
        }
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
                        new IllegalStateException(
                                "Tracker sheet not found."
                        )
                )
                .getProperties()
                .getSheetId();
    }

    public List<WorkoutResponse> getWorkoutsByDate(
            LocalDate requestedDate
    ) throws IOException {
        ValueRange response = sheets.spreadsheets()
                .values()
                .get(spreadsheetId, WORKOUT_READ_RANGE)
                .setValueRenderOption("FORMATTED_VALUE")
                .execute();

        List<List<Object>> rows = response.getValues() == null
                ? List.of()
                : response.getValues();

        Map<String, WorkoutGroup> groups =
                new LinkedHashMap<>();

        for (List<Object> row : rows) {
            if (!requestedDate.toString().equals(
                    workoutCellValue(row, 0)
            )) {
                continue;
            }

            String workoutName = workoutCellValue(row, 1);
            String workoutKey =
                    normalizeWorkoutName(workoutName);

            int day = workoutIntegerValue(row, 17);

            WorkoutGroup group = groups.computeIfAbsent(
                    workoutKey,
                    ignored -> new WorkoutGroup(
                            workoutName,
                            day,
                            new ArrayList<>()
                    )
            );

            group.exercises().add(createExerciseResponse(row));
        }

        return groups.values()
                .stream()
                .map(group -> new WorkoutResponse(
                        group.name(),
                        group.day(),
                        List.copyOf(group.exercises())
                ))
                .toList();
    }

    private ExerciseResponse createExerciseResponse(
            List<Object> row
    ) {
        List<WorkoutSetResponse> sets = new ArrayList<>();
        int totalReps = 0;

        for (int setIndex = 0;
             setIndex < MAX_SETS;
             setIndex++) {

            int weightColumn = 3 + (setIndex * 2);
            int repsColumn = 4 + (setIndex * 2);

            Integer reps = workoutIntegerOrNull(
                    row,
                    repsColumn
            );

            if (reps == null) {
                continue;
            }

            BigDecimal weight = workoutWeightOrNull(
                    row,
                    weightColumn
            );

            sets.add(new WorkoutSetResponse(weight, reps));
            totalReps += reps;
        }

        return new ExerciseResponse(
                workoutCellValue(row, 2),
                sets,
                totalReps,
                workoutStringOrNull(row, 16)
        );
    }

    private String workoutCellValue(
            List<Object> row,
            int index
    ) {
        if (index >= row.size() || row.get(index) == null) {
            return "";
        }

        return row.get(index).toString().trim();
    }

    private int workoutIntegerValue(
            List<Object> row,
            int index
    ) {
        return new BigDecimal(
                workoutCellValue(row, index)
                        .replace(",", "")
        ).intValueExact();
    }

    private Integer workoutIntegerOrNull(
            List<Object> row,
            int index
    ) {
        String value = workoutCellValue(row, index);

        if (value.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(
                    value.replace(",", "")
            ).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    private BigDecimal workoutWeightOrNull(
            List<Object> row,
            int index
    ) {
        String value = workoutCellValue(row, index);

        if (value.isBlank()
                || "BW".equalsIgnoreCase(value)) {
            return null;
        }

        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String workoutStringOrNull(
            List<Object> row,
            int index
    ) {
        String value = workoutCellValue(row, index);
        return value.isBlank() ? null : value;
    }

    private record WorkoutGroup(
            String name,
            int day,
            List<ExerciseResponse> exercises
    ) {
    }

    private Object weightOrBodyweight(Object weightKg) {
        return weightKg == null ? "BW" : weightKg;
    }

    private Object valueOrBlank(Object value) {
        return value == null ? "" : value;
    }
}