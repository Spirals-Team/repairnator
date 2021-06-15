package fr.inria.spirals.repairnator.process.step;

import ch.qos.logback.classic.Level;
import fr.inria.jtravis.entities.Build;
import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.process.files.FileHelper;
import fr.inria.spirals.repairnator.process.inspectors.JobStatus;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.step.checkoutrepository.CheckoutBuggyBuild;
import fr.inria.spirals.repairnator.process.step.checkoutrepository.CheckoutType;
import fr.inria.spirals.repairnator.process.step.gatherinfo.GatherCheckstyleInformation;
import fr.inria.spirals.repairnator.process.step.paths.ComputePlugins;
import fr.inria.spirals.repairnator.process.utils4tests.ProjectInspectorMocker;
import fr.inria.spirals.repairnator.states.ScannedBuildStatus;
import fr.inria.spirals.repairnator.utils.Utils;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import se.bjurr.violations.lib.model.Violation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TestGatherCheckstyleInformation {

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

	/**
	 * This test runs on a test project created specifically for Repairnator's CI.
	 * It analyzes a branch with 2 checkstyler violations, and the model returns
	 * 1 possible patch.
	 *
	 * The Styler models for it are shipped with the container so that no access to
	 * the model store is required during CI.
	 *
	 * @throws IOException
	 */
	@Test
	public void testGatherCheckstyleInformationWhenFailing() throws IOException {
		long buildId = 228999402; // andre15silva/stylerTestProject -> master

		Build build = this.checkBuildAndReturn(buildId, false);

		tmpDir = Files.createTempDirectory("test_gathercheckstyle").toFile();

		File repoDir = new File(tmpDir, "repo");
		BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");

		JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

		ProjectInspector inspector = ProjectInspectorMocker.mockProjectInspector(jobStatus, tmpDir, toBeInspected, CheckoutType.CHECKOUT_BUGGY_BUILD);

		CloneRepository cloneStep = new CloneRepository(inspector);
		GatherCheckstyleInformation gatherCheckstyleInformation = new GatherCheckstyleInformation(inspector, true);

		cloneStep.addNextStep(new CheckoutBuggyBuild(inspector, true))
				.addNextStep(new ComputePlugins(inspector, true))
				.addNextStep(gatherCheckstyleInformation);
		cloneStep.execute();

		List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
		assertThat(stepStatusList.size(), is(4));
		StepStatus gatherTestInfoStatus = stepStatusList.get(3);
		assertThat(gatherTestInfoStatus.getStep(), is(gatherCheckstyleInformation));

		for (StepStatus stepStatus : stepStatusList) {
			assertThat(stepStatus.isSuccess(), is(true));
		}

		Set<Violation> violations = jobStatus.getCheckstyleViolations();
		assertThat(violations.size(), is(2));
	}

	/**
	 * This test runs on a test project created specifically for Repairnator's CI.
	 * It analyzes a branch with no checkstyler violations.
	 *
	 * The Styler models for it are shipped with the container so that no access to
	 * the model store is required during CI.
	 *
	 * @throws IOException
	 */
	@Test
	public void testGatherCheckstyleInformationWhenPassing() throws IOException {
		long buildId = 229002078; // andre15silva/stylerTestProject -> passing

		Build build = this.checkBuildAndReturn(buildId, false);

		tmpDir = Files.createTempDirectory("test_gathercheckstyle").toFile();

		File repoDir = new File(tmpDir, "repo");
		BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");

		JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

		ProjectInspector inspector = ProjectInspectorMocker.mockProjectInspector(jobStatus, tmpDir, toBeInspected, CheckoutType.CHECKOUT_BUGGY_BUILD);

		CloneRepository cloneStep = new CloneRepository(inspector);
		GatherCheckstyleInformation gatherCheckstyleInformation = new GatherCheckstyleInformation(inspector, true);

		cloneStep.addNextStep(new CheckoutBuggyBuild(inspector, true))
				.addNextStep(new ComputePlugins(inspector, true))
				.addNextStep(gatherCheckstyleInformation);
		cloneStep.execute();

		List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
		assertThat(stepStatusList.size(), is(4));
		StepStatus gatherTestInfoStatus = stepStatusList.get(3);
		assertThat(gatherTestInfoStatus.getStep(), is(gatherCheckstyleInformation));

		for (StepStatus stepStatus : stepStatusList) {
			assertThat(stepStatus.isSuccess(), is(true));
		}

		Set<Violation> violations = jobStatus.getCheckstyleViolations();
		assertThat(violations.size(), is(0));
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
