package fr.inria.spirals.repairnator.process.inspectors.components;

import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.notifier.AbstractNotifier;
import fr.inria.spirals.repairnator.notifier.ErrorNotifier;
import fr.inria.spirals.repairnator.pipeline.RepairToolsManager;
import fr.inria.spirals.repairnator.process.git.GitHelper;
import fr.inria.spirals.repairnator.process.step.AbstractStep;
import fr.inria.spirals.repairnator.process.step.AddExperimentalPluginRepo;
import fr.inria.spirals.repairnator.process.step.BuildProject;
import fr.inria.spirals.repairnator.process.step.CloneCheckoutBranchRepository;
import fr.inria.spirals.repairnator.process.step.TestProject;
import fr.inria.spirals.repairnator.process.step.WritePropertyFile;
import fr.inria.spirals.repairnator.process.step.checkoutrepository.CheckoutPatchedBuild;
import fr.inria.spirals.repairnator.process.step.checkoutrepository.CheckoutType;
import fr.inria.spirals.repairnator.process.step.gatherinfo.BuildShouldFail;
import fr.inria.spirals.repairnator.process.step.gatherinfo.BuildShouldPass;
import fr.inria.spirals.repairnator.process.step.gatherinfo.GatherCheckstyleInformation;
import fr.inria.spirals.repairnator.process.step.gatherinfo.GatherTestInformation;
import fr.inria.spirals.repairnator.process.step.paths.*;
import fr.inria.spirals.repairnator.process.step.push.CommitType;
import fr.inria.spirals.repairnator.process.step.push.GitRepositoryCommitPatch;
import fr.inria.spirals.repairnator.process.step.push.GitRepositoryCommitProcessEnd;
import fr.inria.spirals.repairnator.process.step.push.GitRepositoryInitRepoToPush;
import fr.inria.spirals.repairnator.process.step.push.GitRepositoryPushProcessEnd;
import fr.inria.spirals.repairnator.process.step.repair.AbstractRepairStep;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.inspectors.GitRepositoryProjectInspector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RunInspector4DefaultGit extends IRunInspector{

    public RunInspector4DefaultGit() {}

	@Override
	public void run(ProjectInspector inspector_in) {
        GitRepositoryProjectInspector inspector = (GitRepositoryProjectInspector) inspector_in;
		if (inspector.getGitRepositoryUrl() != null) {
            AbstractStep cloneRepo = new CloneCheckoutBranchRepository(inspector);
            
            // If we have experimental plugins, we need to add them here.
            String[] repos = RepairnatorConfig.getInstance().getExperimentalPluginRepoList();
            if(repos != null) {
                for(int i = 0; i < repos.length-1; i =+ 2) {
                    cloneRepo.addNextStep(new AddExperimentalPluginRepo(inspector, repos[i], repos[i+1], repos[i+2]));
                }
            }
            // Add the next steps
            if (!this.skipPreSteps) {
                cloneRepo
                    .addNextStep(new BuildProject(inspector))
                    .addNextStep(new TestProject(inspector))
                    .addNextStep(new ComputeClasspath(inspector, false))
                    .addNextStep(new ComputeSourceDir(inspector, false, false))
                    .addNextStep(new ComputeTestDir(inspector, true))
                    .addNextStep(new ComputePlugins(inspector, true))
                    .addNextStep(new GatherTestInformation(inspector, true, new BuildShouldFail(), false))
                    .addNextStep(new GatherCheckstyleInformation(inspector, true));
            }
            
            cloneRepo.addNextStep(new GitRepositoryInitRepoToPush(inspector));
            
            for (String repairToolName : RepairnatorConfig.getInstance().getRepairTools()) {
                AbstractRepairStep repairStep = RepairToolsManager.getStepFromName(repairToolName);
                if (repairStep != null) {
                    repairStep.setProjectInspector(inspector);
                    cloneRepo.addNextStep(repairStep);
                } else {
                    inspector.getLogger().error("Error while getting repair step class for following name: " + repairToolName);
                }
            }

            cloneRepo.addNextStep(new GitRepositoryCommitPatch(inspector, CommitType.COMMIT_REPAIR_INFO))
                    .addNextStep(new CheckoutPatchedBuild(inspector, true))
                    .addNextStep(new BuildProject(inspector))
                    .addNextStep(new TestProject(inspector))
                    .addNextStep(new GatherTestInformation(inspector, true, new BuildShouldPass(), true))
                    .addNextStep(new GitRepositoryCommitPatch(inspector, CommitType.COMMIT_HUMAN_PATCH));

            AbstractStep finalStep = new ComputeSourceDir(inspector, false, true); // this step is used to compute code metrics on the project
            
            finalStep.
                    addNextStep(new ComputeModules(inspector, false)).
                    addNextStep(new WritePropertyFile(inspector)).
                    addNextStep(new GitRepositoryCommitProcessEnd(inspector)).
                    addNextStep(new GitRepositoryPushProcessEnd(inspector));

            inspector.setFinalStep(finalStep);

            cloneRepo.setDataSerializer(inspector.getSerializers());
            cloneRepo.setNotifiers(inspector.getNotifiers());

            inspector.printPipeline();

            try {
                cloneRepo.execute();
            } catch (Exception e) {
                inspector.getJobStatus().addStepError("Unknown", e.getMessage());
                inspector.getLogger().error("Exception catch while executing steps: ", e);
                inspector.getJobStatus().setFatalError(e);

                ErrorNotifier errorNotifier = ErrorNotifier.getInstance();
                if (errorNotifier != null) {
                    errorNotifier.observe(inspector);
                }

                for (AbstractDataSerializer serializer : inspector.getSerializers()) {
                    serializer.serialize();
                }
            }
        } else {
            inspector.getLogger().debug("Build " + inspector.getBuggyBuild().getId() + " is not a failing build.");
        }
	}
}