package fr.inria.spirals.repairnator.serializer.gsheet.inspectors;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import fr.inria.spirals.jtravis.entities.Build;
import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.ProjectState;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.Utils;
import fr.inria.spirals.repairnator.serializer.GoogleSpreadSheetFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Created by fermadeiral.
 */
public class GoogleSpreadSheetInspectorSerializer4Bears extends AbstractDataSerializer {
    private Logger logger = LoggerFactory.getLogger(GoogleSpreadSheetInspectorSerializer4Bears.class);
    private static final String RANGE = "All data!A1:P1";

    private Sheets sheets;

    public GoogleSpreadSheetInspectorSerializer4Bears() {
        super();
        this.sheets = GoogleSpreadSheetFactory.getSheets();
    }

    @Override
    public void serializeData(ProjectInspector inspector) {
        if (this.sheets != null) {
            BuildToBeInspected buildToBeInspected = inspector.getBuildToBeInspected();
            Build build = inspector.getBuild();

            Build previousBuild = inspector.getPreviousBuild();
            int previousBuildId = (previousBuild != null) ? previousBuild.getId() : -1;

            String state = this.getPrettyPrintState(inspector.getState(), inspector.getTestInformations());

            String realState = (inspector.getState() != null) ? inspector.getState().name() : "null";

            String typeOfFailures = "";
            Set<String> failures = inspector.getTestInformations().getFailureNames();
            for (String failure : failures) {
                typeOfFailures += failure + ",";
            }

            String previousBuildSlug = (previousBuild != null) ? previousBuild.getRepository().getSlug() : "";

            List<Object> dataCol = new ArrayList<Object>();
            dataCol.add(build.getId() + "");
            dataCol.add(previousBuildId + "");
            dataCol.add(inspector.getBuildToBeInspected().getStatus().toString());
            dataCol.add(state);
            dataCol.add(realState);
            dataCol.add(typeOfFailures);
            dataCol.add(build.getRepository().getSlug());
            dataCol.add(build.getPullRequestNumber() + "");
            dataCol.add(Utils.formatCompleteDate(build.getFinishedAt()));
            dataCol.add(Utils.formatOnlyDay(build.getFinishedAt()));
            dataCol.add(Utils.getHostname());
            dataCol.add(Utils.formatCompleteDate(new Date()));
            dataCol.add(this.getTravisUrl(build.getId(), build.getRepository().getSlug()));
            dataCol.add(this.getTravisUrl(previousBuildId, previousBuildSlug));
            if (inspector.getState() == ProjectState.FIXERBUILDCASE1 || inspector.getState() == ProjectState.FIXERBUILDCASE2) {
                String committerEmail = (build.getCommit().getCommitterEmail() != null) ? build.getCommit().getCommitterEmail() : "-";
                dataCol.add(committerEmail);
            }
            dataCol.add(buildToBeInspected.getRunId());

            List<List<Object>> dataRow = new ArrayList<List<Object>>();
            dataRow.add(dataCol);

            ValueRange valueRange = new ValueRange();
            valueRange.setValues(dataRow);

            try {
                AppendValuesResponse response = this.sheets.spreadsheets().values()
                        .append(GoogleSpreadSheetFactory.getSpreadsheetID(), RANGE, valueRange)
                        .setInsertDataOption("INSERT_ROWS").setValueInputOption("USER_ENTERED").execute();
                if (response != null && response.getUpdates().getUpdatedCells() > 0) {
                    this.logger.debug("Data have been inserted in Google Spreadsheet.");
                }
            } catch (IOException e) {
                this.logger.error("An error occured while inserting data in Google Spreadsheet.", e);
            }
        } else {
            this.logger.warn("Cannot serialize data: the sheets is not initialized (certainly a credential error)");
        }
    }
}
