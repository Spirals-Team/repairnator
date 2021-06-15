package fr.inria.spirals.repairnator.process.step.repair.styler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import fr.inria.spirals.repairnator.config.StylerConfig;
import fr.inria.spirals.repairnator.docker.DockerHelper;
import fr.inria.spirals.repairnator.process.inspectors.RepairPatch;
import fr.inria.spirals.repairnator.process.step.StepStatus;
import fr.inria.spirals.repairnator.process.step.repair.AbstractRepairStep;
import org.apache.maven.model.Plugin;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * StylerRepair is the repair step that calls the Styler docker image.
 * Styler is an automatic repair system for Checkstyle errors based
 * on machine learning.
 *
 * Check Styler's paper for more info:
 * arxiv.org/pdf/1904.01754
 *
 * Styler's base repo:
 * https://github.com/KTH/styler/
 *
 * @author andre15silva
 */
public class StylerRepair extends AbstractRepairStep {

	public static final String TOOL_NAME = "StylerRepair";
	private final DockerClient docker;

	public StylerRepair() {
		this.docker = DockerHelper.initDockerClient();
	}

	@Override
	public String getRepairToolName() {
		return TOOL_NAME;
	}

	@Override
	protected StepStatus businessExecute() {
		this.getLogger().info("Starting StylerRepair step...");

		Plugin checkstylePlugin = new Plugin();
		checkstylePlugin.setGroupId("org.apache.maven.plugins");
		checkstylePlugin.setArtifactId("maven-checkstyle-plugin");
		if (!this.getInspector().getJobStatus().getPlugins().contains(checkstylePlugin)) {
			this.addStepError("The project does not have checkstyle as a plugin");
			return StepStatus.buildSkipped(this);
		}

		StylerConfig config = StylerConfig.getInstance();

		String stylerCommand = "./styler_repairnator.sh "
				+ this.getInspector().getGitSlug() + " "
				+ this.getInspector().getBuggyBuild().getBranch().getName() + " "
				+ this.getInspector().getBuggyBuild().getCommit().getSha() + " "
				+ config.getSnicHost() + " "
				+ config.getSnicUsername() + " "
				+ config.getSnicPassword() + " "
				+ config.getSnicPath() + " "
				+ "/out/results.json";

		this.getLogger().debug("Docker command: \n " + stylerCommand);

		HostConfig.Builder hostConfigBuilder = HostConfig.builder();
		hostConfigBuilder.appendBinds(HostConfig.Bind
						.from(this.getInspector().getWorkspace())
						.to("/out")
						.build());
		HostConfig hostConfig = hostConfigBuilder.build();

		ContainerConfig containerConfig = ContainerConfig.builder()
				.image(config.getDockerTag())
				.hostConfig(hostConfig)
				.cmd("bash", "-c", stylerCommand)
				.attachStdout(true)
				.attachStderr(true)
				.build();

		try {
			ContainerCreation container = docker.createContainer(containerConfig);

			docker.startContainer(container.id());
			docker.waitContainer(container.id());

			String stdOut = docker.logs(
					container.id(),
					DockerClient.LogsParam.stdout()
			).readFully();

			String stdErr = docker.logs(
					container.id(),
					DockerClient.LogsParam.stderr()
			).readFully();

			this.getLogger().debug("stdOut: \n" + stdOut);
			this.getLogger().debug("stdErr: \n" + stdErr);

			docker.removeContainer(container.id());

			File resultsFile = new File(this.getInspector().getWorkspace() + "/results.json");
			if (resultsFile.exists()) {
				this.getLogger().debug("Results file has been found after execution of Styler: " + resultsFile.getAbsolutePath());

				Gson gson = new Gson();
				JsonElement results = gson.fromJson(new FileReader(resultsFile), JsonElement.class);
				this.getLogger().debug("Results file json: " + results.toString());

				List<RepairPatch> patches = new ArrayList<>();
				Set<String> keys  = results.getAsJsonObject().keySet();
				for (String key: keys) {
					JsonObject patch = results.getAsJsonObject().get(key).getAsJsonObject();
					String diff = patch.get("diff").getAsString();
					String buggyPath = this.getInspector().getRepoLocalPath() + "/" + patch.get("relative_path").getAsString();
					System.out.println(buggyPath);
					patches.add(new RepairPatch(this.getRepairToolName(), buggyPath, diff));
				}

				this.getInspector().getJobStatus().addPatches(this.getRepairToolName(), patches);
				this.getInspector().getJobStatus().addToolDiagnostic(this.getRepairToolName(), results);
				this.getInspector().getJobStatus().setHasBeenPatched(true);
				return StepStatus.buildSuccess(this);
			} else {
				this.getLogger().info("No results file as found after the execution of Styler");
				return StepStatus.buildPatchNotFound(this);
			}
		} catch (DockerException | InterruptedException | IOException e) {
			this.getLogger().error(e.getMessage());
			return StepStatus.buildSkipped(this);
		}

	}

}