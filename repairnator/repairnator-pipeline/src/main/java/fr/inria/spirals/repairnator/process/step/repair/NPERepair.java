package fr.inria.spirals.repairnator.process.step.repair;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.inria.spirals.repairnator.process.inspectors.StepStatus;
import fr.inria.spirals.repairnator.process.maven.MavenHelper;
import fr.inria.spirals.repairnator.process.testinformation.FailureLocation;
import fr.inria.spirals.repairnator.process.testinformation.FailureType;
import org.apache.commons.io.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by urli on 10/07/2017.
 */
public class NPERepair extends AbstractRepairStep {
    protected static final String TOOL_NAME = "NPEFix";
    private static final String NPEFIX_GOAL = "fr.inria.gforge.spirals:repair-maven-plugin:1.4:npefix";

    public NPERepair() {}

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
        this.getLogger().debug("Entrance in NPERepair step...");

        if (isThereNPE()) {
            this.getLogger().info("NPE found, start NPEFix");

            MavenHelper mavenHelper = new MavenHelper(this.getPom(), NPEFIX_GOAL, null, this.getName(), this.getInspector(), true );
            int status = MavenHelper.MAVEN_ERROR;
            try {
                status = mavenHelper.run();
            } catch (InterruptedException e) {
                this.addStepError("Error while executing Maven goal", e);
            }

            if (status == MavenHelper.MAVEN_ERROR) {
                this.addStepError("Error while running NPE fix: maybe the project does not contain a NPE?");
                return StepStatus.buildSkipped(this,"Error while running maven goal for NPEFix.");
            } else {
                Collection<File> files = FileUtils.listFiles(new File(this.getInspector().getJobStatus().getPomDirPath()+"/target/npefix"), new String[] { "json"}, false);
                if (!files.isEmpty()) {

                    File patchesFiles = files.iterator().next();
                    try {
                        FileUtils.copyFile(patchesFiles, new File(this.getInspector().getRepoLocalPath()+"/repairnator.npefix.results"));
                    } catch (IOException e) {
                        this.addStepError("Error while moving NPE fix results", e);
                    }

                    this.getInspector().getJobStatus().addFileToPush("repairnator.npefix.results");

                    boolean effectivelyPatched = false;
                    File patchDir = new File(this.getInspector().getRepoLocalPath()+"/repairnatorPatches");

                    patchDir.mkdir();
                    try {
                        JsonParser jsonParser = new JsonParser();
                        JsonElement root = jsonParser.parse(new FileReader(patchesFiles));
                        this.getInspector().getJobStatus().setNpeFixResults(root);

                        List<String> npePatches = new ArrayList<String>();

                        JsonArray executions = root.getAsJsonObject().getAsJsonArray("executions");
                        if (executions != null) {
                            for (JsonElement execution : executions) {
                                JsonObject result = execution.getAsJsonObject().getAsJsonObject("result");
                                boolean success = result.get("success").getAsBoolean() && execution.getAsJsonObject().has("decisions");

                                if (success) {
                                    effectivelyPatched = true;
                                    String testName = execution.getAsJsonObject().getAsJsonObject("test").get("name").getAsString();
                                    long startDate = execution.getAsJsonObject().get("startDate").getAsLong();

                                    String filename = "npefix_"+testName+"_"+startDate+".patch";
                                    String content = execution.getAsJsonObject().get("diff").getAsString();

                                    File patchFile = new File(patchDir.getPath()+"/"+filename);
                                    BufferedWriter patchWriter = new BufferedWriter(new FileWriter(patchFile));
                                    patchWriter.write(content);
                                    patchWriter.flush();
                                    patchWriter.close();

                                    npePatches.add(content);
                                }
                            }
                        }
                        this.getInspector().getJobStatus().setNpeFixPatches(npePatches);
                    } catch (IOException e) {
                        this.addStepError("Error while parsing JSON patch files");
                    }

                    if (effectivelyPatched) {
                        this.getInspector().getJobStatus().setHasBeenPatched(true);
                        return StepStatus.buildSuccess(this);
                    } else {
                        return StepStatus.buildSkipped(this,"No patch found.");
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
