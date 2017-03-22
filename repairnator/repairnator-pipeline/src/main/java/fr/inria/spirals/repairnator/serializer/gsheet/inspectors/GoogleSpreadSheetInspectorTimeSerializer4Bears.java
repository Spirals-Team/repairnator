package fr.inria.spirals.repairnator.serializer.gsheet.inspectors;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import fr.inria.spirals.jtravis.entities.Build;
import fr.inria.spirals.repairnator.Utils;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.step.*;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.serializer.GoogleSpreadSheetFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by fernanda on 21/03/17.
 */
public class GoogleSpreadSheetInspectorTimeSerializer4Bears extends AbstractDataSerializer {
    private Logger logger = LoggerFactory.getLogger(GoogleSpreadSheetInspectorTimeSerializer4Bears.class);
    private static final String RANGE = "Duration Data!A1:S1";

    private Sheets sheets;

    public GoogleSpreadSheetInspectorTimeSerializer4Bears(String googleSecretPath) throws IOException {
        super();
        this.sheets = GoogleSpreadSheetFactory.getSheets(googleSecretPath);
    }

    @Override
    public void serializeData(ProjectInspector inspector) {
        if (this.sheets != null) {
            Map<String, Integer> durations = inspector.getStepsDurationsInSeconds();

            int cloneRepository = durations.getOrDefault(CloneRepository.class.getSimpleName(), 0);
            int checkoutBuild = durations.getOrDefault(CheckoutBuild.class.getSimpleName(), 0);
            int buildProjectBuild = durations.getOrDefault(BuildProject.class.getSimpleName()+"Build", 0);
            int testProjectBuild = durations.getOrDefault(TestProject.class.getSimpleName()+"Build", 0);
            int gatherTestInformationBuild = durations.getOrDefault(GatherTestInformation.class.getSimpleName()+"Build", 0);
            int checkoutPreviousBuild = durations.getOrDefault(CheckoutPreviousBuild.class.getSimpleName(), 0);
            int buildProjectPreviousBuild = durations.getOrDefault(BuildProject.class.getSimpleName()+"PreviousBuild", 0);
            int testProjectPreviousBuild = durations.getOrDefault(TestProject.class.getSimpleName()+"PreviousBuild", 0);
            int gatherTestInformationPreviousBuild = durations.getOrDefault(GatherTestInformation.class.getSimpleName()+"PreviousBuild", 0);
            int checkoutPreviousBuildSourceCode = durations.getOrDefault(CheckoutPreviousBuildSourceCode.class.getSimpleName(), 0);
            int buildProjectPreviousBuildSourceCode = durations.getOrDefault(BuildProject.class.getSimpleName()+"PreviousBuildSourceCode", 0);
            int testProjectPreviousBuildSourceCode = durations.getOrDefault(TestProject.class.getSimpleName()+"PreviousBuildSourceCode", 0);
            int gatherTestInformationPreviousBuildSourceCode = durations.getOrDefault(GatherTestInformation.class.getSimpleName()+"PreviousBuildSourceCode", 0);
            int pushBuild = durations.getOrDefault(PushIncriminatedBuild.class.getSimpleName(), 0);

            int totalDuration = 0;
            totalDuration = cloneRepository + checkoutBuild + buildProjectBuild + testProjectBuild + gatherTestInformationBuild +
                    checkoutPreviousBuild + buildProjectPreviousBuild + testProjectPreviousBuild + gatherTestInformationPreviousBuild +
                    checkoutPreviousBuildSourceCode + buildProjectPreviousBuildSourceCode + testProjectPreviousBuildSourceCode + gatherTestInformationPreviousBuildSourceCode + pushBuild;

            Build build = inspector.getBuild();

            Build previousBuild = inspector.getPreviousBuild();
            int previousBuildId = (previousBuild != null) ? previousBuild.getId() : -1;

            List<Object> dataCol = new ArrayList<Object>();
            dataCol.add(build.getId() + "");
            dataCol.add(previousBuildId + "");
            dataCol.add(build.getRepository().getSlug());
            dataCol.add(Utils.getHostname());
            dataCol.add(totalDuration);
            dataCol.add(cloneRepository);
            dataCol.add(checkoutBuild);
            dataCol.add(buildProjectBuild);
            dataCol.add(testProjectBuild);
            dataCol.add(gatherTestInformationBuild);
            dataCol.add(checkoutPreviousBuild);
            dataCol.add(buildProjectPreviousBuild);
            dataCol.add(testProjectPreviousBuild);
            dataCol.add(gatherTestInformationPreviousBuild);
            dataCol.add(checkoutPreviousBuildSourceCode);
            dataCol.add(buildProjectPreviousBuildSourceCode);
            dataCol.add(testProjectPreviousBuildSourceCode);
            dataCol.add(gatherTestInformationPreviousBuildSourceCode);
            dataCol.add(pushBuild);

            List<List<Object>> dataRow = new ArrayList<List<Object>>();
            dataRow.add(dataCol);

            ValueRange valueRange = new ValueRange();
            valueRange.setValues(dataRow);

            try {
                AppendValuesResponse response = this.sheets.spreadsheets().values()
                        .append(GoogleSpreadSheetFactory.getSpreadsheetID(), RANGE, valueRange)
                        .setInsertDataOption("INSERT_ROWS").setValueInputOption("USER_ENTERED").execute();
                if (response != null && response.getUpdates().getUpdatedCells() > 0) {
                    this.logger.debug("Time data have been inserted in Google Spreadsheet.");
                }
            } catch (IOException e) {
                this.logger.error("An error occured while inserting time data in Google Spreadsheet.", e);
            }
        } else {
            this.logger.warn("Cannot serialize data: the sheets is not initialized (certainly a credential error)");
        }
    }
}
