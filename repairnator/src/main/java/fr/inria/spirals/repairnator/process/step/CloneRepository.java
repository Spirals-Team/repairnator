package fr.inria.spirals.repairnator.process.step;

import fr.inria.spirals.jtravis.entities.Build;
import fr.inria.spirals.jtravis.entities.PRInformation;
import fr.inria.spirals.repairnator.helpers.GitHelper;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.ProjectState;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;

import java.io.File;

/**
 * Created by urli on 03/01/2017.
 */
public class CloneRepository extends AbstractStep {
    public static final String GITHUB_ROOT_REPO = "https://github.com/";
    private static final String GITHUB_PATCH_ACCEPT = "application/vnd.github.v3.patch";

    protected Build build;

    public CloneRepository(ProjectInspector inspector) {
        super(inspector);
        this.build = inspector.getBuild();
    }

    protected void businessExecute() {
        String repository = this.inspector.getRepoSlug();
        String repoRemotePath = GITHUB_ROOT_REPO + repository + ".git";
        String repoLocalPath = this.inspector.getRepoLocalPath();

        // start cloning
        try {
            this.getLogger()
                    .debug("Cloning repository " + repository + " has in the following directory: " + repoLocalPath);
            Git git = Git.cloneRepository().setURI(repoRemotePath).setDirectory(new File(repoLocalPath)).call();

            this.writeProperty("workspace", this.inspector.getWorkspace());
            this.writeProperty("buildid", this.build.getId() + "");
            this.writeProperty("repo", this.build.getRepository().getSlug());

            if (this.build.isPullRequest()) {
                this.writeProperty("is-pr", "true");

                PRInformation prInformation = this.build.getPRInformation();

                this.writeProperty("pr-remote-repo", this.build.getPRInformation().getOtherRepo().getSlug());
                this.writeProperty("pr-head-commit-id", this.build.getPRInformation().getHead().getSha());
                this.writeProperty("pr-base-commit-id", this.build.getPRInformation().getBase().getSha());
                this.writeProperty("pr-id", this.build.getPullRequestNumber() + "");

                this.getLogger()
                        .debug("Reproduce the PR for " + repository + " by fetching remote branch and merging.");
                String remoteBranchPath = GITHUB_ROOT_REPO + prInformation.getOtherRepo().getSlug() + ".git";

                RemoteAddCommand remoteBranchCommand = git.remoteAdd();
                remoteBranchCommand.setName("PR");
                remoteBranchCommand.setUri(new URIish(remoteBranchPath));
                remoteBranchCommand.call();

                git.fetch().setRemote("PR").call();

                String commitHeadSha = GitHelper.testCommitExistence(git, prInformation.getHead().getSha(), this, build);
                String commitBaseSha = GitHelper.testCommitExistence(git, prInformation.getBase().getSha(), this, build);

                if (commitHeadSha == null) {
                    this.addStepError("Commit head ref cannot be retrieved in the repository: "
                            + prInformation.getHead().getSha() + ". Operation aborted.");
                    this.getLogger().debug(prInformation.getHead().toString());
                    this.shouldStop = true;
                    return;
                }

                if (commitBaseSha == null) {
                    this.addStepError("Commit base ref cannot be retrieved in the repository: "
                            + prInformation.getBase().getSha() + ". Operation aborted.");
                    this.getLogger().debug(prInformation.getBase().toString());
                    this.shouldStop = true;
                    return;
                }

                this.getLogger().debug("Get the commit " + commitHeadSha + " for repo " + repository);
                git.checkout().setName(commitHeadSha).call();

                RevWalk revwalk = new RevWalk(git.getRepository());
                RevCommit revCommitBase = revwalk.lookupCommit(git.getRepository().resolve(commitBaseSha));

                this.getLogger().debug("Do the merge with the PR commit for repo " + repository);
                git.merge().include(revCommitBase).setFastForward(MergeCommand.FastForwardMode.NO_FF).call();
            } else {
                String commitCheckout = this.build.getCommit().getSha();

                commitCheckout = GitHelper.testCommitExistence(git, commitCheckout, this, build);

                if (commitCheckout != null) {
                    this.getLogger().debug("Get the commit " + commitCheckout + " for repo " + repository);
                    git.checkout().setName(commitCheckout).call();
                } else {
                    this.addStepError("Error while getting the commit to checkout from the repo.");
                    this.shouldStop = true;
                    return;
                }

            }

        } catch (Exception e) {
            this.getLogger().warn("Repository " + repository + " cannot be cloned.");
            this.getLogger().debug(e.toString());
            this.addStepError(e.getMessage());
            this.shouldStop = true;
            return;
        }

        this.state = ProjectState.CLONABLE;
    }

    protected void cleanMavenArtifacts() {
    }
}
