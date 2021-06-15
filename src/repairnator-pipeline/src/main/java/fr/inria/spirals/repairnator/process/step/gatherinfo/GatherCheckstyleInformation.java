package fr.inria.spirals.repairnator.process.step.gatherinfo;

import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.maven.MavenHelper;
import fr.inria.spirals.repairnator.process.step.AbstractStep;
import fr.inria.spirals.repairnator.process.step.StepStatus;
import fr.inria.spirals.repairnator.states.PipelineState;
import org.apache.maven.model.Plugin;
import se.bjurr.violations.lib.ViolationsApi;
import se.bjurr.violations.lib.model.Violation;
import se.bjurr.violations.lib.reports.Parser;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Pipeline step for collecting information of a checkstyle run.
 *
 * If the plugin in configured in the project's pom, then the step
 * collects all the violations, if any, detected by checkstyler.
 */
public class GatherCheckstyleInformation extends AbstractStep {

	private int nbViolations;

	public GatherCheckstyleInformation(ProjectInspector inspector, boolean blockingStep) {
		super(inspector, blockingStep, GatherCheckstyleInformation.class.getSimpleName());
	}

	@Override
	protected StepStatus businessExecute() {
		this.getLogger().debug("Gather checkstyle information on the project");

		Plugin checkstylePlugin = new Plugin();
		checkstylePlugin.setGroupId("org.apache.maven.plugins");
		checkstylePlugin.setArtifactId("maven-checkstyle-plugin");
		if (!this.getInspector().getJobStatus().getPlugins().contains(checkstylePlugin)) {
			this.addStepError("The project does not have checkstyle as a plugin");
			return StepStatus.buildSkipped(this);
		}

		Properties properties = new Properties();
		properties.setProperty(MavenHelper.SKIP_TEST_PROPERTY, "true");

		MavenHelper helper = new MavenHelper(this.getPom(), "checkstyle:checkstyle", properties, this.getClass().getSimpleName(), this.getInspector(), false);

		int result;
		try {
			result = helper.run();
		} catch (InterruptedException e) {
			this.addStepError("Error while running checkstyle", e);
			return StepStatus.buildError(this, PipelineState.NOTBUILDABLE);
		}

		if (result == MavenHelper.MAVEN_SUCCESS) {
			this.getInspector().getJobStatus().setCheckstyleViolations(new HashSet<>());
			this.nbViolations = 0;
		} else {
			Set<Violation> violations = ViolationsApi.violationsApi()
					.withPattern(".*checkstyle.*\\.xml$")
					.inFolder(this.getInspector().getJobStatus().getPomDirPath() + "/target")
					.findAll(Parser.CHECKSTYLE)
					.violations();

			this.getInspector().getJobStatus().setCheckstyleViolations(violations);
			this.nbViolations = violations.size();
		}

		this.getLogger().info("---Checkstyle results---");
		this.getLogger().info("   Violations found: " + this.nbViolations);

		return StepStatus.buildSuccess(this);
	}
}
