package fr.inria.spirals.repairnator.process.step.repair.soraldbot;

import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.process.git.GitHelper;
import fr.inria.spirals.repairnator.process.step.StepStatus;
import fr.inria.spirals.repairnator.process.step.repair.AbstractRepairStep;
import fr.inria.spirals.repairnator.process.step.repair.soraldbot.models.SoraldTargetCommit;
import fr.inria.spirals.repairnator.utils.DateUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

/*
    Performs Sorald repair on a given commit for a given sonar_rule.
    Note: getConfig().push and getConfig().isCreate should be set to true to create a PR.
 */
public class SoraldBot extends AbstractRepairStep {
    private static final String REPO_PATH = "tmp_repo";
    private static final String RULE_LINK_TEMPLATE = "https://rules.sonarsource.com/java/RSPEC-";
    private SoraldTargetCommit commit;
    private String originalBranchName;

    private String workingRepoPath;

    /**
     * {@return if the initialization is successful}
     */
    private boolean init() {
        commit = new SoraldTargetCommit(getConfig().getGitCommitHash(), getInspector().getRepoSlug());
        workingRepoPath = getInspector().getWorkspace() + File.separator + REPO_PATH;

        try {
            originalBranchName = getOriginalBranch();
        } catch (IOException e) {
            getLogger().error("IOException while looking for the original branch");
        }

        return commit != null && workingRepoPath != null && originalBranchName != null;
    }

    @Override
    public String getRepairToolName() {
        return SoraldConstants.SORALD_TOOL_NAME;
    }

    @Override
    protected StepStatus businessExecute() {
        boolean successfulInit = init();
        if(!successfulInit){
            return StepStatus.buildSkipped(this, "Error while repairing with Sorald");
        }

        List<String> rules = Arrays.asList(RepairnatorConfig.getInstance().getSonarRules());
        try {
            for (String rule : rules) {
                Set<String> patchedFiles =
                        SoraldAdapter.getInstance(getInspector().getWorkspace(), SoraldConstants.SPOON_SNIPER_MODE)
                                .repairRepoAndReturnViolationIntroducingFiles(commit, rule, REPO_PATH);

                if(patchedFiles != null && !patchedFiles.isEmpty()){
                    this.getInspector().getJobStatus().setHasBeenPatched(true);
//                    getConfig().setPush(true);
                    createPRWithSpecificPatchedFiles(patchedFiles, rule);
                }
            }
        } catch (Exception e) {
            return StepStatus.buildSkipped(this, "Error while repairing with Sorald");
        }

        return StepStatus.buildSuccess(this);
    }

    private void createPRWithSpecificPatchedFiles(Set<String> violationIntroducingFiles, String rule)
            throws GitAPIException, IOException, URISyntaxException {

        String newBranchName = "repairnator-patch-" + DateUtils.formatFilenameDate(new Date());
        String forkedRepo = null;
        Git forkedGit = null;
        if (forkedGit == null) {
            forkedRepo = this.getForkedRepoName();
            if (forkedRepo == null) {
                return;
            }
        }


        applyPatches4Sonar(violationIntroducingFiles, rule);

        forkedGit = this.createGitBranch4Push(newBranchName);

        StringBuilder prTextBuilder = new StringBuilder()
                .append("This PR fixes the violations for the following Sorald rule: \n");
        prTextBuilder.append(RULE_LINK_TEMPLATE).append(rule + "\n");
        prTextBuilder.append("If you do no want to receive automated PRs for Sorald warnings, reply to this PR with 'STOP'");
        setPrText(prTextBuilder.toString());
        setPRTitle("Fix Sorald violations");

        
        pushPatches(forkedGit, forkedRepo, newBranchName);
        createPullRequest(originalBranchName, newBranchName);
    }

    private String getOriginalBranch() throws IOException {
        GitHub github = new GitHubBuilder().withOAuthToken(RepairnatorConfig.getInstance().getGithubToken()).build();
        GHRepository repo = github.getRepository(getInspector().getRepoSlug());
        Map<String, GHBranch> branches = repo.getBranches();
        Optional<String> branch = branches
                .keySet().stream()
                .filter(key -> branches.get(key).getSHA1().equals(commit.getCommitId()))
                .findFirst();

        if(!branch.isPresent()){
            getLogger().error("The branch of the commit was not found");
            return null;
        }
        return branch.get();
    }

    private void applyPatches4Sonar(Set<String> violationIntroducingFiles, String ruleNumber)
            throws IOException, GitAPIException, URISyntaxException {
        FileUtils.copyDirectory(new File(workingRepoPath), new File(getInspector().getRepoLocalPath()));

        Git git = Git.open(new File(this.getInspector().getRepoLocalPath()));
        AddCommand addCommand = git.add();
        violationIntroducingFiles.forEach(f -> addCommand.addFilepattern(f));
        addCommand.setUpdate(true);
        addCommand.call();

        git.commit().setAuthor(GitHelper.getCommitterIdent()).setCommitter(GitHelper.getCommitterIdent())
                .setMessage("Proposal for patching Sorald rule " + ruleNumber).call();
    }
}
