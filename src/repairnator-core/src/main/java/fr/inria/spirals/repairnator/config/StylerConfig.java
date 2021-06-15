package fr.inria.spirals.repairnator.config;

import static fr.inria.spirals.repairnator.utils.Utils.getEnvOrDefault;

/**
 * Configuration manager for Styler repair step:
 * Config values are read directly as environment variables,
 * so that we avoid adding more options through the launcher's
 * command line args.
 */
public final class StylerConfig {
	private final String dockerTag;
	private final String snicHost;
	private final String snicUsername;
	private final String snicPassword;
	private final String snicPath;

	private static StylerConfig instance;

	public static StylerConfig getInstance(){
		if (instance == null)
			instance = new StylerConfig();

		return instance;
	}

	private StylerConfig() {
		this.dockerTag = getEnvOrDefault("STYLER_DOCKER_TAG", "andre15silva/styler:0.1");
		this.snicHost = getEnvOrDefault("SNIC_HOST", "kebnekaise.hpc2n.umu.se");
		this.snicUsername = System.getenv("SNIC_USERNAME");
		this.snicPassword = System.getenv("SNIC_PASSWORD");
		this.snicPath = getEnvOrDefault("SNIC_PATH", "~/styler-models/");
	}

	public String getDockerTag() {
		return dockerTag;
	}

	public String getSnicHost() {
		return snicHost;
	}

	public String getSnicUsername() {
		return snicUsername;
	}

	public String getSnicPassword() {
		return snicPassword;
	}

	public String getSnicPath() {
		return snicPath;
	}

	public static void deleteInstance() {
		instance = null;
	}
}
