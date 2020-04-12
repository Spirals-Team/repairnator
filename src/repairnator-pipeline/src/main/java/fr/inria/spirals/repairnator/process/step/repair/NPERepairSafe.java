package fr.inria.spirals.repairnator.process.step.repair;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.inria.spirals.repairnator.process.inspectors.RepairPatch;
import fr.inria.spirals.repairnator.process.step.StepStatus;
import fr.inria.spirals.repairnator.process.maven.MavenHelper;
import fr.inria.spirals.repairnator.process.testinformation.FailureLocation;
import fr.inria.spirals.repairnator.process.testinformation.FailureType;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by Benjamin Tellström on 14/06/2019
 */

// This class is a straight copt of NPERepair, but with different fields for TOOL_NAME and NPEFIX_GOAL to avoid hiding
public class NPERepairSafe extends AbstractRepairStep {
    public static final String TOOL_NAME = "NPEFixSafe";
    private static final String NPEFIX_GOAL = "fr.inria.gforge.spirals:repair-maven-plugin:1.6-SNAPSHOT:npefix";

    public NPERepairSafe() {}

    @Override
    public String getRepairToolName() {
        return TOOL_NAME;
    }

    private boolean isThereNPE() {
        for (FailureLocation failureLocation : this.getInspector().getJobStatus().getFailureLocations()) {
            for (FailureType failureType : failureLocation.getFailures()) {
                if (failureType.getFailureName().startsWith("java.lang.NullPointerException")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected StepStatus businessExecute() {
        this.getLogger().debug("Entrance in NPERepairSafe step...");

        if (isThereNPE()) {
            this.getLogger().info("NPE found, start NPEFixSafe");

            List<RepairPatch> repairPatches = new ArrayList<>();

            MavenHelper mavenHelper = new MavenHelper(this.getPom(), NPEFIX_GOAL, null, this.getName(), this.getInspector(), true );
            int status = MavenHelper.MAVEN_ERROR;
            try {
                status = mavenHelper.run();
            } catch (InterruptedException e) {
                this.addStepError("Error while executing Maven goal", e);
            }

            if (status == MavenHelper.MAVEN_ERROR) {
                this.addStepError("Error while running NPEfixSafe: maybe the project does not contain a NPE?");
                return StepStatus.buildSkipped(this,"Error while running maven goal for NPEFixSafe.");
            } else {
                Collection<File> files = FileUtils.listFiles(new File(this.getInspector().getJobStatus().getPomDirPath()+"/target/npefix"), new String[] { "json"}, false);
                if (!files.isEmpty()) {

                    File patchesFiles = files.iterator().next();
                    try {
                        FileUtils.copyFile(patchesFiles, new File(this.getInspector().getRepoLocalPath()+"/repairnator.npefix.results"));
                    } catch (IOException e) {
                        this.addStepError("Error while moving NPEfixSafe results", e);
                    }

                    this.getInspector().getJobStatus().addFileToPush("repairnator.npefix-safe.results");

                    boolean effectivelyPatched = false;
                    File patchDir = new File(this.getInspector().getRepoLocalPath()+"/repairnatorPatches");

                    patchDir.mkdir();
                    try {
                        JsonParser jsonParser = new JsonParser();
                        JsonElement root = jsonParser.parse(new FileReader(patchesFiles));
                        this.recordToolDiagnostic(root);

                        JsonArray executions = root.getAsJsonObject().getAsJsonArray("executions");
                        if (executions != null) {
                            for (JsonElement execution : executions) {
                                JsonObject result = execution.getAsJsonObject().getAsJsonObject("result");
                                boolean success = result.get("success").getAsBoolean() && execution.getAsJsonObject().has("decisions");

                                if (success) {
                                    effectivelyPatched = true;

                                    JsonElement diff = execution.getAsJsonObject().get("diff");
                                    if (diff != null) {
                                        String content = diff.getAsString();

                                        RepairPatch repairPatch = new RepairPatch(this.getRepairToolName(), "", content);
                                        repairPatches.add(repairPatch);
                                    } else {
                                        this.addStepError("Error while parsing JSON path file: diff content is null.");
                                    }
                                }
                            }

                            this.recordPatches(repairPatches,MAX_PATCH_PER_TOOL);
                        }
                    } catch (IOException e) {
                        this.addStepError("Error while parsing JSON patch files");
                    }

                    if (effectivelyPatched) {
                        return StepStatus.buildSuccess(this);
                    } else {
                        return StepStatus.buildPatchNotFound(this);
                    }

                } else {
                    this.addStepError("Error while getting the patch json file: no file found.");
                    return StepStatus.buildSkipped(this,"Error while reading patch file.");
                }


            }
        } else {
            this.getLogger().info("No NPE found, this step will be skipped.");
            return StepStatus.buildSkipped(this, "No NPE found.");
        }
    }
}

