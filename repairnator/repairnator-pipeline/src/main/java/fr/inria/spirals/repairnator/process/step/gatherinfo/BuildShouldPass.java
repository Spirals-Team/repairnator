package fr.inria.spirals.repairnator.process.step.gatherinfo;

import fr.inria.spirals.repairnator.ProjectState;

/**
 * Created by fermadeiral.
 */
public class BuildShouldPass implements ContractForGatherTestInformation {

    @Override
    public boolean shouldBeStopped(GatherTestInformation gatherTestInformation) {
        if (gatherTestInformation.getState() == ProjectState.NOTFAILING) {
            return false;
        }
        return true;
    }

}
