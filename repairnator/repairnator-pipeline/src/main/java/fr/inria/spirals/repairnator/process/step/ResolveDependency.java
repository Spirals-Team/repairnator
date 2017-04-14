package fr.inria.spirals.repairnator.process.step;

import fr.inria.spirals.repairnator.ProjectState;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.maven.MavenHelper;

/**
 * Created by urli on 14/04/2017.
 */
public class ResolveDependency extends AbstractStep {

    public ResolveDependency(ProjectInspector inspector) {
        super(inspector);
    }

    public ResolveDependency(ProjectInspector inspector, String stepName) {
        super(inspector, stepName);
    }

    @Override
    protected void businessExecute() {
        this.getLogger().debug("Resolve dependencies with maven (skip tests)...");

        this.getLogger().debug("Installing artifacts without test execution...");
        MavenHelper helper = new MavenHelper(this.getPom(), "dependency:resolve", null, this.getClass().getSimpleName(), this.inspector, true);

        int result = helper.run();

        if (result == MavenHelper.MAVEN_SUCCESS) {
            this.setState(ProjectState.DEPENDENCY_RESOLVED);
        } else {
            this.getLogger().warn("Repository " + this.inspector.getRepoSlug() + " has unresolvable dependencies.");
            this.setState(ProjectState.DEPENDENCY_UNRESOLVABLE);
            this.shouldStop = true;
        }
    }


}
