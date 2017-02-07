package fr.inria.spirals.repairnator.serializer.gsheet;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import fr.inria.spirals.repairnator.process.ProjectScanner;
import fr.inria.spirals.repairnator.serializer.SerializerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by urli on 02/02/2017.
 */
public class GoogleSpreadSheetScannerSerializer {
    private Logger logger = LoggerFactory.getLogger(GoogleSpreadSheetScannerSerializer.class);
    private static final String RANGE = "Scanner Data!A1:J1";

    private Sheets sheets;
    private ProjectScanner scanner;

    public GoogleSpreadSheetScannerSerializer(ProjectScanner scanner) throws IOException {
        this.sheets = GoogleSpreadSheetFactory.getSheets();
        this.scanner = scanner;
    }

    public void serialize() {
        List<Object> dataCol = new ArrayList<Object>();
        dataCol.add(SerializerUtils.getHostname());
        dataCol.add(SerializerUtils.formatCompleteDate(new Date()));
        dataCol.add(SerializerUtils.formatCompleteDate(this.scanner.getLimitDate()));
        dataCol.add(this.scanner.getTotalRepoNumber());
        dataCol.add(this.scanner.getTotalRepoUsingTravis());
        dataCol.add(this.scanner.getTotalScannedBuilds());
        dataCol.add(this.scanner.getTotalBuildInJava());
        dataCol.add(this.scanner.getTotalPassingBuilds());
        dataCol.add(this.scanner.getTotalBuildInJavaFailing());
        dataCol.add(this.scanner.getTotalBuildInJavaFailingWithFailingTests());

        List<List<Object>> dataRow = new ArrayList<List<Object>>();
        dataRow.add(dataCol);

        ValueRange valueRange = new ValueRange();
        valueRange.setValues(dataRow);

        try {
            AppendValuesResponse response = this.sheets.spreadsheets().values().append(GoogleSpreadSheetFactory.SPREADSHEET_ID, RANGE, valueRange).setInsertDataOption("INSERT_ROWS").setValueInputOption("USER_ENTERED").execute();
            if (response != null && response.getUpdates().getUpdatedCells() > 0) {
                this.logger.debug("Scanner data have been inserted in Google Spreadsheet.");
            }
        } catch (IOException e) {
            this.logger.error("An error occured while inserting scanner data in Google Spreadsheet.",e);
        }
    }
}