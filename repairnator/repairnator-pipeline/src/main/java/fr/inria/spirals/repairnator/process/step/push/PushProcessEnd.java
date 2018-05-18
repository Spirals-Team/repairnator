package fr.inria.spirals.repairnator.process.step.push;

import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.process.git.GitHelper;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.inspectors.StepStatus;
import fr.inria.spirals.repairnator.process.step.AbstractStep;
import fr.inria.spirals.repairnator.states.PushState;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

public class PushProcessEnd extends AbstractStep {

    public PushProcessEnd(ProjectInspector inspector) {
        super(inspector, false);
    }

    @Override
    protected StepStatus businessExecute() {
        if (RepairnatorConfig.getInstance().isPush() && this.getInspector().getJobStatus().getPushState() != PushState.NONE) {
            File sourceDir = new File(this.getInspector().getRepoLocalPath());
            File targetDir = new File(this.getInspector().getRepoToPushLocalPath());

            try {
                Git git = Git.open(targetDir);

                GitHelper gitHelper = this.getInspector().getGitHelper();

                String[] excludedFileNames = { ".git", ".m2" };
                gitHelper.copyDirectory(sourceDir, targetDir, excludedFileNames, this);

                gitHelper.removeNotificationFromTravisYML(targetDir, this);

                git.add().addFilepattern(".").call();

                gitHelper.gitAdd(this.getInspector().getJobStatus().getCreatedFilesToPush(), git);

                PersonIdent personIdent = new PersonIdent("Luc Esape", "luc.esape@gmail.com");
                git.commit().setMessage("End of the repairnator process")
                        .setAuthor(personIdent).setCommitter(personIdent).call();

                if (this.getInspector().getJobStatus().isHasBeenPushed()) {
                    CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(this.getConfig().getGithubToken(), "");
                    git.push().setRemote(PushIncriminatedBuild.REMOTE_NAME).setCredentialsProvider(credentialsProvider).call();
                }
                this.setPushState(PushState.END_PUSHED);
                return StepStatus.buildSuccess(this);
            } catch (GitAPIException | IOException e) {
                this.getLogger().error("Error while trying to commit last information for repairnator", e);
            }
            return StepStatus.buildSkipped(this,"Error while pushing or committed info.");
        } else {
            this.getLogger().info("Repairnator is not configured to push data or the repo have not been initialized. This step will be bypassed.");
            return StepStatus.buildSkipped(this);
        }
    }
}
