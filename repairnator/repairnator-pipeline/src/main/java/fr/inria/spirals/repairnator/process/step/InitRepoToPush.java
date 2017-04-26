package fr.inria.spirals.repairnator.process.step;

import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.File;
import java.io.IOException;

/**
 * Created by urli on 26/04/2017.
 */
public class InitRepoToPush extends AbstractStep {

    public InitRepoToPush(ProjectInspector inspector) {
        super(inspector);
    }

    public InitRepoToPush(ProjectInspector inspector, String name) {
        super(inspector, name);
    }

    @Override
    protected void businessExecute() {

        if (RepairnatorConfig.getInstance().isPush()) {
            this.getLogger().info("Repairnator configured to push. Start init repo to push.");

            File sourceDir = new File(this.getInspector().getRepoLocalPath());
            File targetDir = new File(this.getInspector().getRepoToPushLocalPath());

            try {
                FileUtils.copyDirectory(sourceDir, targetDir);

                File gitTargetFolder = new File(targetDir, ".git");
                FileUtils.deleteDirectory(gitTargetFolder);

                Git git = Git.init().setGitDir(targetDir).call();
                git.add().addFilepattern(".").call();

                PersonIdent personIdent = new PersonIdent("Luc Esape", "luc.esape@gmail.com");
                git.commit().setMessage("Bug commit. This is the reflect of the following commit: ... . Please note some conf files may have been added")
                        .setAuthor(personIdent).setCommitter(personIdent).call();

            } catch (IOException e) {
                this.addStepError("");
            } catch (GitAPIException e) {
                this.addStepError("");
            }

        } else {
            this.getLogger().info("Repairnator configured to NOT push. Step bypassed.");
        }
    }
}
