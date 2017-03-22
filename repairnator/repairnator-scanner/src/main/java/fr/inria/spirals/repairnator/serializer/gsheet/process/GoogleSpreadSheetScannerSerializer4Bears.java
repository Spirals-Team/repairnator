package fr.inria.spirals.repairnator.serializer.gsheet.process;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import fr.inria.spirals.repairnator.ProcessSerializer;
import fr.inria.spirals.repairnator.Utils;
import fr.inria.spirals.repairnator.scanner.ProjectScanner;
import fr.inria.spirals.repairnator.serializer.GoogleSpreadSheetFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by fernanda on 06/03/2017.
 */
public class GoogleSpreadSheetScannerSerializer4Bears implements ProcessSerializer {
    private Logger logger = LoggerFactory.getLogger(GoogleSpreadSheetScannerSerializer4Bears.class);
    private static final String RANGE = "Scanner Data!A1:P1";

    private Sheets sheets;
    private ProjectScanner scanner;

    public GoogleSpreadSheetScannerSerializer4Bears(ProjectScanner scanner, String googleSecretPath) throws IOException {
        this.sheets = GoogleSpreadSheetFactory.getSheets(googleSecretPath);
        this.scanner = scanner;
    }

    public void serialize() {
        if (this.sheets != null) {
            List<Object> dataCol = new ArrayList<Object>();
            dataCol.add(Utils.getHostname());
            dataCol.add(Utils.formatCompleteDate(this.scanner.getScannerRunningBeginDate()));
            dataCol.add(Utils.formatCompleteDate(this.scanner.getScannerRunningEndDate()));
            dataCol.add(this.scanner.getScannerDuration());
            dataCol.add(Utils.formatCompleteDate(this.scanner.getLookFromDate()));
            dataCol.add(this.scanner.getTotalRepoNumber());
            dataCol.add(this.scanner.getTotalRepoUsingTravis());
            dataCol.add(this.scanner.getTotalScannedBuilds());
            dataCol.add(this.scanner.getTotalBuildInJava());
            dataCol.add(this.scanner.getTotalJavaPassingBuilds());
            dataCol.add(this.scanner.getTotalBuildInJavaFailing());
            dataCol.add(this.scanner.getTotalBuildInJavaFailingWithFailingTests());
            dataCol.add(this.scanner.getTotalNumberOfFailingAndPassingBuildPairs());
            dataCol.add(this.scanner.getTotalNumberOfPassingAndPassingBuildPairs());
            dataCol.add(this.scanner.getTotalNumberOfFailingAndPassingBuildPairs() + this.scanner.getTotalNumberOfPassingAndPassingBuildPairs());
            dataCol.add(this.scanner.getTotalPRBuilds());
            dataCol.add(Utils.formatOnlyDay(this.scanner.getLookFromDate()));
            dataCol.add(this.scanner.getRunId());

            List<List<Object>> dataRow = new ArrayList<List<Object>>();
            dataRow.add(dataCol);

            ValueRange valueRange = new ValueRange();
            valueRange.setValues(dataRow);

            try {
                AppendValuesResponse response = this.sheets.spreadsheets().values()
                        .append(GoogleSpreadSheetFactory.getSpreadsheetID(), RANGE, valueRange)
                        .setInsertDataOption("INSERT_ROWS").setValueInputOption("USER_ENTERED").execute();
                if (response != null && response.getUpdates().getUpdatedCells() > 0) {
                    this.logger.debug("Scanner data have been inserted in Google Spreadsheet.");
                }
            } catch (IOException e) {
                this.logger.error("An error occured while inserting scanner data in Google Spreadsheet.", e);
            }
        } else {
            this.logger.warn("Cannot serialize data: the sheets is not initialized (certainly a credential error)");
        }
    }
}
