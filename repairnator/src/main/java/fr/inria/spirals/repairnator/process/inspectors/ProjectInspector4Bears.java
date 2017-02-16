package fr.inria.spirals.repairnator.process.inspectors;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.inria.spirals.jtravis.entities.Build;
import fr.inria.spirals.repairnator.RepairMode;
import fr.inria.spirals.repairnator.process.ProjectState;
import fr.inria.spirals.repairnator.process.step.AbstractStep;
import fr.inria.spirals.repairnator.process.step.BuildProject;
import fr.inria.spirals.repairnator.process.step.CheckoutPreviousBuild;
import fr.inria.spirals.repairnator.process.step.CloneRepository;
import fr.inria.spirals.repairnator.process.step.GatherTestInformation4Bears;
import fr.inria.spirals.repairnator.process.step.PushIncriminatedBuild;
import fr.inria.spirals.repairnator.process.step.TestProject;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;

/**
 * Created by fermadeiral.
 */
public class ProjectInspector4Bears extends ProjectInspector {
	private final Logger logger = LoggerFactory.getLogger(ProjectInspector4Bears.class);

	public ProjectInspector4Bears(Build build, String workspace, List<AbstractDataSerializer> serializers,
			String nopolSolverPath, boolean push, RepairMode mode) {
		super(build, workspace, serializers, null, push, mode);
		this.previousBuildFlag = false;
	}

	public void run() {
		AbstractStep firstStep = null;

		AbstractStep cloneRepo = new CloneRepository(this);
		AbstractStep buildRepo = new BuildProject(this);
		AbstractStep testProject = new TestProject(this);
		this.testInformations = new GatherTestInformation4Bears(this);
		AbstractStep checkoutPreviousBuild = new CheckoutPreviousBuild(this);
		AbstractStep buildRepoForPreviousBuild = new BuildProject(this);
		AbstractStep testProjectForPreviousBuild = new TestProject(this);
		AbstractStep gatherTestInformation = new GatherTestInformation4Bears(this);

		cloneRepo.setNextStep(buildRepo).setNextStep(testProject).setNextStep(this.testInformations)
				.setNextStep(checkoutPreviousBuild).setNextStep(buildRepoForPreviousBuild)
				.setNextStep(testProjectForPreviousBuild).setNextStep(gatherTestInformation);

		if (this.getPushMode()) {
			PushIncriminatedBuild pushIncriminatedBuild = new PushIncriminatedBuild(this);
			pushIncriminatedBuild.setRemoteRepoUrl(PushIncriminatedBuild.REMOTE_REPO_BEAR);
			this.setPushBuild(pushIncriminatedBuild);
			gatherTestInformation.setNextStep(pushIncriminatedBuild);
		}

		firstStep = cloneRepo;
		firstStep.setDataSerializer(this.serializers);

		firstStep.setState(ProjectState.INIT);

		try {
			firstStep.execute();
		} catch (Exception e) {
			this.addStepError("Unknown", e.getMessage());
			this.logger.debug("Exception catch while executing steps: ", e);
		}
	}
}