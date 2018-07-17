package fr.inria.spirals.repairnator.process.step.repair.nopol;

import fr.inria.spirals.repairnator.process.inspectors.StepStatus;
import fr.inria.spirals.repairnator.process.testinformation.FailureLocation;

import java.util.Collections;

/**
 * This launch Nopol against all classes of failing test at once
 */
public class NopolAllTestsRepair extends AbstractNopolRepair {
    protected static final String TOOL_NAME = "NopolSingleTest";

    @Override
    protected StepStatus businessExecute() {
        this.getLogger().debug("Start to use nopol single repair to repair...");

        this.initPatchDir();
        this.initWithJobStatus();

        if (this.getClassPath() != null && this.getSources() != null) {
            for (FailureLocation failureLocation : this.getInspector().getJobStatus().getFailureLocations()) {
                this.runNopol(failureLocation, Collections.EMPTY_LIST, true);
            }

            return this.recordResults();
        } else {
            this.addStepError("No classpath or sources directory has been given. Nopol can't be launched.");
            return StepStatus.buildSkipped(this,"No classpath or source directory given.");
        }
    }

    @Override
    public String getRepairToolName() {
        return TOOL_NAME;
    }
}
