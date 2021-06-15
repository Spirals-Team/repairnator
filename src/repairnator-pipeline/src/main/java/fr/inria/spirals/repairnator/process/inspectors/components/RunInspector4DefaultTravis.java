package fr.inria.spirals.repairnator.process.inspectors.components;

import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.notifier.ErrorNotifier;
import fr.inria.spirals.repairnator.pipeline.RepairToolsManager;
import fr.inria.spirals.repairnator.process.inspectors.properties.Properties;
import fr.inria.spirals.repairnator.process.inspectors.properties.machineInfo.MachineInfo;
import fr.inria.spirals.repairnator.process.step.AbstractStep;
import fr.inria.spirals.repairnator.process.step.AddExperimentalPluginRepo;
import fr.inria.spirals.repairnator.process.step.BuildProject;
import fr.inria.spirals.repairnator.process.step.CloneRepository;
import fr.inria.spirals.repairnator.process.step.TestProject;
import fr.inria.spirals.repairnator.process.step.WritePropertyFile;
import fr.inria.spirals.repairnator.process.step.checkoutrepository.CheckoutBuggyBuild;
import fr.inria.spirals.repairnator.process.step.checkoutrepository.CheckoutPatchedBuild;
import fr.inria.spirals.repairnator.process.step.gatherinfo.BuildShouldFail;
import fr.inria.spirals.repairnator.process.step.gatherinfo.BuildShouldPass;
import fr.inria.spirals.repairnator.process.step.gatherinfo.GatherCheckstyleInformation;
import fr.inria.spirals.repairnator.process.step.gatherinfo.GatherTestInformation;
import fr.inria.spirals.repairnator.process.step.paths.*;
import fr.inria.spirals.repairnator.process.step.push.CommitPatch;
import fr.inria.spirals.repairnator.process.step.push.CommitProcessEnd;
import fr.inria.spirals.repairnator.process.step.push.CommitType;
import fr.inria.spirals.repairnator.process.step.push.InitRepoToPush;
import fr.inria.spirals.repairnator.process.step.push.PushProcessEnd;
import fr.inria.spirals.repairnator.process.step.repair.AbstractRepairStep;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.states.ScannedBuildStatus;
import fr.inria.spirals.repairnator.utils.Utils;

public class RunInspector4DefaultTravis extends IRunInspector{

    public RunInspector4DefaultTravis() {}


	@Override
	public void run(ProjectInspector inspector) {
		if (inspector.getBuildToBeInspected().getStatus() != ScannedBuildStatus.PASSING_AND_PASSING_WITH_TEST_CHANGES) {
            AbstractStep cloneRepo = new CloneRepository(inspector);
            cloneRepo.addNextStep(new CheckoutBuggyBuild(inspector, true));

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
           
            cloneRepo.addNextStep(new InitRepoToPush(inspector));

            for (String repairToolName : RepairnatorConfig.getInstance().getRepairTools()) {
                AbstractRepairStep repairStep = RepairToolsManager.getStepFromName(repairToolName);
                if (repairStep != null) {
                    repairStep.setProjectInspector(inspector);
                    cloneRepo.addNextStep(repairStep);
                } else {
                    inspector.getLogger().error("Error while getting repair step class for following name: " + repairToolName);
                }
            }

            cloneRepo.addNextStep(new CommitPatch(inspector, CommitType.COMMIT_REPAIR_INFO))
                    .addNextStep(new CheckoutPatchedBuild(inspector, true))
                    .addNextStep(new BuildProject(inspector))
                    .addNextStep(new TestProject(inspector))
                    .addNextStep(new GatherTestInformation(inspector, true, new BuildShouldPass(), true))
                    .addNextStep(new CommitPatch(inspector, CommitType.COMMIT_HUMAN_PATCH));

            AbstractStep finalStep = new ComputeSourceDir(inspector, false, true); // inspector step is used to compute code metrics on the project


            finalStep.
                    addNextStep(new ComputeModules(inspector, false)).
                    addNextStep(new WritePropertyFile(inspector)).
                    addNextStep(new CommitProcessEnd(inspector)).
                    addNextStep(new PushProcessEnd(inspector));

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