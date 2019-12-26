package fr.inria.spirals.repairnator.process.step;

import fr.inria.jtravis.entities.Build;
import fr.inria.spirals.repairnator.utils.Utils;
import fr.inria.spirals.repairnator.process.inspectors.JenkinsProjectInspector;
import fr.inria.spirals.repairnator.states.PipelineState;
import org.eclipse.jgit.api.Git;

import java.io.File;

/**
 * Created by urli on 03/01/2017.
 */
public class JenkinsCloneRepository extends AbstractStep {

    protected Build build;
    protected String gitUrl;
    protected String gitSlug;
    protected String gitBranch;

    public JenkinsCloneRepository(JenkinsProjectInspector inspector) {
        super(inspector, true);
        this.gitUrl = inspector.getGitUrl();
        this.gitSlug = inspector.getRepoSlug();
        this.gitBranch = inspector.getCheckoutBranchName();
    }

    @Override
    protected StepStatus businessExecute() {
        String repoSlug = this.gitSlug;
        String repoRemotePath = this.gitUrl;
        String repoLocalPath = ((JenkinsProjectInspector)this.getInspector()).getRepoLocalPath();
        String repoBranch = this.gitBranch;

        try {
            this.getLogger().debug("Cloning repository in the following directory: " + repoLocalPath);

            Git.cloneRepository().setCloneSubmodules(true).setURI(repoRemotePath).setBranch(repoBranch).setDirectory(new File(repoLocalPath)).call();

            return StepStatus.buildSuccess(this);
        } catch (Exception e) {
            this.addStepError("Repository " + repoSlug + " cannot be cloned.", e);
            return StepStatus.buildError(this, PipelineState.NOTCLONABLE);
        }
    }

}
