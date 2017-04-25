package fr.inria.spirals.repairnator.process.step.checkoutrepository;

import fr.inria.spirals.repairnator.ProjectState;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;

/**
 * Created by fernanda on 02/03/17.
 */
public class CheckoutPatchedBuild extends CheckoutRepository {

    public CheckoutPatchedBuild(ProjectInspector inspector) {
        super(inspector);
    }

    protected void businessExecute() {
        this.getLogger().debug("Checking out build...");

        if (this.getInspector().getPatchedBuild() != null) {
            super.setCheckoutType(CheckoutType.CHECKOUT_PATCHED_BUILD);

            super.businessExecute();

            if (this.shouldStop) {
                this.setState(ProjectState.PATCHEDBUILDNOTCHECKEDOUT);
            } else {
                this.setState(ProjectState.PATCHEDBUILDCHECKEDOUT);
                inspector.setCheckoutType(getCheckoutType());
            }
        } else {
            this.addStepError("There is no patched build retrieved. This will stop now.");
            this.shouldStop = true;
            this.setState(ProjectState.PATCHEDBUILDNOTCHECKEDOUT);
        }


    }

}
