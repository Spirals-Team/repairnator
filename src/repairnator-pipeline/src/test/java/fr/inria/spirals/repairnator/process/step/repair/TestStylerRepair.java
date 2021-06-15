package fr.inria.spirals.repairnator.process.step.repair;

import ch.qos.logback.classic.Level;
import fr.inria.jtravis.entities.Build;
import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.process.files.FileHelper;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.inspectors.RepairPatch;
import fr.inria.spirals.repairnator.process.step.BuildProject;
import fr.inria.spirals.repairnator.process.step.CloneRepository;
import fr.inria.spirals.repairnator.process.step.StepStatus;
import fr.inria.spirals.repairnator.process.step.TestProject;
import fr.inria.spirals.repairnator.process.step.checkoutrepository.CheckoutBuggyBuild;
import fr.inria.spirals.repairnator.process.step.gatherinfo.BuildShouldFail;
import fr.inria.spirals.repairnator.process.step.gatherinfo.GatherCheckstyleInformation;
import fr.inria.spirals.repairnator.process.step.gatherinfo.GatherTestInformation;
import fr.inria.spirals.repairnator.process.step.paths.ComputeClasspath;
import fr.inria.spirals.repairnator.process.step.paths.ComputePlugins;
import fr.inria.spirals.repairnator.process.step.paths.ComputeSourceDir;
import fr.inria.spirals.repairnator.process.step.paths.ComputeTestDir;
import fr.inria.spirals.repairnator.process.step.repair.styler.StylerRepair;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.states.ScannedBuildStatus;
import fr.inria.spirals.repairnator.utils.Utils;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TestStylerRepair {

	private File tmpDir;

	@Before
	public void setup() {
		Utils.setLoggersLevel(Level.ERROR);
		RepairnatorConfig config = RepairnatorConfig.getInstance();
		config.setJTravisEndpoint("https://api.travis-ci.com");
	}

	@After
	public void tearDown() throws IOException {
		RepairnatorConfig.deleteInstance();
		FileHelper.deleteFile(tmpDir);
	}

	@Test
	public void testStylerRepair() throws IOException {
		long buildId = 228999402; // andre15silva/stylerTestProject -> master

		RepairnatorConfig.getInstance().setOutputPath(Files.createTempDirectory("test_stylerrepair_output").toFile().getAbsolutePath());

		Build build = this.checkBuildAndReturn(buildId, false);

		tmpDir = Files.createTempDirectory("stylerrepair").toFile();

		BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");

		RepairnatorConfig.getInstance().setRepairTools(Collections.singleton(StylerRepair.TOOL_NAME));
		ProjectInspector inspector = new ProjectInspector(toBeInspected, tmpDir.getAbsolutePath(), null, null);

		CloneRepository cloneStep = new CloneRepository(inspector);
		StylerRepair stylerRepair = new StylerRepair();
		stylerRepair.setProjectInspector(inspector);

		cloneStep.addNextStep(new CheckoutBuggyBuild(inspector, true))
				.addNextStep(new BuildProject(inspector))
				.addNextStep(new ComputePlugins(inspector, true))
				.addNextStep(new GatherCheckstyleInformation(inspector, true))
				.addNextStep(stylerRepair);

		cloneStep.execute();

		assertThat(stylerRepair.isShouldStop(), is(false));

		List<StepStatus> stepStatusList = inspector.getJobStatus().getStepStatuses();
		assertThat(stepStatusList.size(), is(6));
		StepStatus npeStatus = stepStatusList.get(5);
		assertThat(npeStatus.getStep(), is(stylerRepair));

		for (StepStatus stepStatus : stepStatusList) {
			assertThat(stepStatus.isSuccess(), is(true));
		}

		String finalStatus = AbstractDataSerializer.getPrettyPrintState(inspector);
		assertThat(finalStatus, is("PATCHED"));

		List<RepairPatch> allPatches = inspector.getJobStatus().getAllPatches();
		assertThat(allPatches.size(), is(1));
		assertThat(inspector.getJobStatus().getToolDiagnostic().get(stylerRepair.getRepairToolName()), notNullValue());

		for (RepairPatch repairPatch : allPatches) {
			assertTrue(new File(repairPatch.getFilePath()).exists());
		}
	}

	private Build checkBuildAndReturn(long buildId, boolean isPR) {
		Optional<Build> optionalBuild = RepairnatorConfig.getInstance().getJTravis().build().fromId(buildId);
		assertTrue(optionalBuild.isPresent());

		Build build = optionalBuild.get();
		assertThat(build, IsNull.notNullValue());
		assertThat(buildId, Is.is(build.getId()));
		assertThat(build.isPullRequest(), Is.is(isPR));

		return build;
	}
}
