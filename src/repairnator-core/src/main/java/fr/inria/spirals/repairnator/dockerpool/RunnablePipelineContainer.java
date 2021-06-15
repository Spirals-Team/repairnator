package fr.inria.spirals.repairnator.dockerpool;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.*;
import fr.inria.spirals.repairnator.InputBuild;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.config.SequencerConfig;
import fr.inria.spirals.repairnator.config.StylerConfig;
import fr.inria.spirals.repairnator.dockerpool.serializer.TreatedBuildTracking;
import fr.inria.spirals.repairnator.states.LauncherMode;
import fr.inria.spirals.repairnator.utils.DateUtils;
import fr.inria.spirals.repairnator.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * This class allows the creation of a docker container and run it.
 */
public class RunnablePipelineContainer implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunnablePipelineContainer.class);
    private static final int DELAY_BEFORE_KILLING_DOCKER_IMAGE = 60 * 24; // in minutes
    private Date limitDateBeforeKilling;
    private String imageId;
    private InputBuild inputBuildId;
    private String logDirectory;
    private RepairnatorConfig repairnatorConfig;
    private TreatedBuildTracking treatedBuildTracking;
    private DockerPoolManager poolManager;
    private String containerId;
    private String containerName;
    private List<String> envValues;
    private Set<String> volumes;

    public ContainerExit getExitStatus() {
        return exitStatus;
    }

    private ContainerExit exitStatus;

    /**
     * The constructor will init all the environment values for the container.
     *
     * @param poolManager the manager of the pool of running containers
     * @param imageId the name of the docker image to use for creating the container
     * @param inputBuildId the input build to use for specifying the arguments
     * @param logDirectory the path of the produced logs
     * @param treatedBuildTracking a serializer for recording the docker containers statuses
     */
    public RunnablePipelineContainer(DockerPoolManager poolManager, String imageId, InputBuild inputBuildId, String logDirectory, TreatedBuildTracking treatedBuildTracking) {
        this.poolManager = poolManager;
        this.imageId = imageId;
        this.inputBuildId = inputBuildId;
        this.logDirectory = logDirectory;
        this.repairnatorConfig = RepairnatorConfig.getInstance();
        this.treatedBuildTracking = treatedBuildTracking;

        this.containerName = "docker_pipeline" +
                DateUtils.formatFilenameDate(new Date()) + "_" +
                inputBuildId.toString() + "_" +
                StringUtils.join(this.repairnatorConfig.getRepairTools(),",");

        this.envValues = new ArrayList<>();
        this.envValues.addAll(inputBuildId.getEnvVariables());

        String output = (this.repairnatorConfig.isCreateOutputDir()) ? "/var/log/" + this.repairnatorConfig.getRunId() : "/var/log";

        if (this.repairnatorConfig.isDebug()) {
            this.envValues.add("LOG_LEVEL=DEBUG");
        } else {
            this.envValues.add("LOG_LEVEL=INFO");
        }

        this.envValues.add("LOG_FILENAME="+this.containerName);
        this.envValues.add("GITHUB_OAUTH="+RepairnatorConfig.getInstance().getGithubToken());
        this.envValues.add("RUN_ID="+this.repairnatorConfig.getRunId());
        this.envValues.add("REPAIR_MODE="+this.repairnatorConfig.getLauncherMode().name());
        this.envValues.add("PUSH_URL="+this.repairnatorConfig.getPushRemoteRepo());
        this.envValues.add("MONGODB_HOST="+this.repairnatorConfig.getMongodbHost());
        this.envValues.add("MONGODB_NAME="+this.repairnatorConfig.getMongodbName());
        this.envValues.add("SMTP_SERVER="+this.repairnatorConfig.getSmtpServer());
        this.envValues.add("SMTP_PORT="+Integer.toString(this.repairnatorConfig.getSmtpPort()));
        this.envValues.add("SMTP_USERNAME="+this.repairnatorConfig.getSmtpUsername());
        this.envValues.add("SMTP_PASSWORD="+this.repairnatorConfig.getSmtpPassword());
        this.envValues.add("GITHUB_USERNAME="+this.repairnatorConfig.getGithubUserName());
        this.envValues.add("GITHUB_USEREMAIL="+this.repairnatorConfig.getGithubUserEmail());
        this.envValues.add("NOTIFY_TO="+ StringUtils.join(this.repairnatorConfig.getNotifyTo(),','));
        this.envValues.add("OUTPUT="+output);
        this.envValues.add("TRAVIS_ENDPOINT="+this.repairnatorConfig.getJTravisEndpoint());
        this.envValues.add("TRAVIS_TOKEN="+this.repairnatorConfig.getTravisToken());
        if (this.repairnatorConfig.isCreatePR()) {
            this.envValues.add("CREATE_PR=1");
        }
        if(this.repairnatorConfig.isSmtpTLS()) {
            this.envValues.add("SMTP_TLS=1");
        } else {
            this.envValues.add("SMTP_TLS=0");
        }

        if (
            this.repairnatorConfig.getLauncherMode() == LauncherMode.REPAIR ||
            this.repairnatorConfig.getLauncherMode() == LauncherMode.CHECKSTYLE ||
            this.repairnatorConfig.getLauncherMode() == LauncherMode.GIT_REPOSITORY ||
            this.repairnatorConfig.getLauncherMode() == LauncherMode.SEQUENCER_REPAIR
        ) {
            this.envValues.add("REPAIR_TOOLS=" + StringUtils.join(this.repairnatorConfig.getRepairTools(), ","));
        }

        if (this.repairnatorConfig.getRepairTools().contains("SequencerRepair")) {
            SequencerConfig sequencerConfig = SequencerConfig.getInstance();
            this.envValues.add("SEQUENCER_DOCKER_TAG=" + sequencerConfig.dockerTag);
            this.envValues.add("SEQUENCER_THREADS=" + sequencerConfig.threads);
            this.envValues.add("SEQUENCER_BEAM_SIZE=" + sequencerConfig.beamSize);
            this.envValues.add("SEQUENCER_TIMEOUT=" + sequencerConfig.timeout);
        }

        if (this.repairnatorConfig.getRepairTools().contains("StylerRepair")) {
            StylerConfig stylerConfig = StylerConfig.getInstance();
            this.envValues.add("STYLER_DOCKER_TAG=" + stylerConfig.getDockerTag());
            this.envValues.add("SNIC_HOST=" + stylerConfig.getSnicHost());
            this.envValues.add("SNIC_USERNAME=" + stylerConfig.getSnicUsername());
            this.envValues.add("SNIC_PASSWORD=" + stylerConfig.getSnicPassword());
            this.envValues.add("SNIC_PATH=" + stylerConfig.getSnicPath());
        }
    }

    public InputBuild getInputBuildId() {
        return this.inputBuildId;
    }

    public Date getLimitDateBeforeKilling() {
        return this.limitDateBeforeKilling;
    }

    @Override
    public void run() {
        this.limitDateBeforeKilling = new Date(new Date().toInstant().plus(DELAY_BEFORE_KILLING_DOCKER_IMAGE, ChronoUnit.MINUTES).toEpochMilli());
        DockerClient docker = this.poolManager.getDockerClient();
        try {
            LOGGER.info("Start to build and run container for build id " + this.inputBuildId.toString());
            LOGGER.info("At most this docker run will be killed at: "+ this.limitDateBeforeKilling);

            // fixme: this does not work anymore to put a name that is displayed in docker ps
            Map<String,String> labels = new HashMap<>();
            labels.put("name",this.containerName);


            //to avoid creating new unnamed volumes
            Volume workspaceVolume = docker.inspectVolume("repairnator_workspace");
            Volume logsVolume = docker.inspectVolume("repairnator_logs");
            Volume ODSVolume = docker.inspectVolume("repairnator_ods_data");

            HostConfig hostConfig = HostConfig.builder()
                    .appendBinds(HostConfig.Bind
                            .builder()
                            .from("/var/run/docker.sock")
                            .to("/var/run/docker.sock")
                            .build())
                    .appendBinds(HostConfig.Bind
                            .builder()
                            .from(workspaceVolume)
                            .to("/root/workspace/")
                            .build())
                    .appendBinds(HostConfig.Bind
                            .builder()
                            .from(logsVolume)
                            .to("/var/log/")
                            .build())
                    .appendBinds(HostConfig.Bind
                            .builder()
                            .from(ODSVolume)
                            .to(RepairnatorConfig.getInstance().getODSPath())
                            .build())
                    .build();

            // we specify the complete configuration of the container
            ContainerConfig containerConfig = ContainerConfig.builder()
                    .image(imageId)
                    .env(envValues)
                    .hostname(Utils.getHostname())
                    .hostConfig(hostConfig)
                    .labels(labels)
                    .build();

            // and we create it
            LOGGER.info("(BUILD ID " + this.inputBuildId.toString() + ") Create the container: " + this.containerName);
            ContainerCreation container = docker.createContainer(containerConfig);

            // fixme: replace it with volumes() ?
            // at the end we want to remove both the container and the volume to save space
            this.volumes = containerConfig.volumeNames();

            this.containerId = container.id();
            treatedBuildTracking.setContainerId(this.containerId);

            // now the container is created: let's start it
            LOGGER.info("(BUILD ID " + this.inputBuildId.toString() + ") Start the container: "+this.containerName);
            docker.startContainer(container.id());

            // and now we wait until it's finished
            exitStatus = docker.waitContainer(this.containerId);

            String stdOut = docker.logs(
                    container.id(),
                    DockerClient.LogsParam.stdout()
            ).readFully();

            String stdErr = docker.logs(
                    container.id(),
                    DockerClient.LogsParam.stderr()
            ).readFully();


            LOGGER.info("stdOut: \n" + stdOut);
            LOGGER.info("stdErr: \n" + stdErr);

            LOGGER.info("(BUILD ID " + this.inputBuildId.toString() + ") The container has finished with status code: "+ exitStatus.statusCode());

            // standard bash: if it's 0 everything's fine.

            if (!this.repairnatorConfig.isSkipDelete() && exitStatus.statusCode() == 0) {
                LOGGER.info("(BUILD ID " + this.inputBuildId.toString() + ") Container will be removed.");
                removeDockerContainer(docker);
            }

            if (exitStatus.statusCode() == 0) {
                serialize("TREATED");
            } else {
                serialize("ERROR:CODE" + exitStatus.statusCode());
            }
        } catch (InterruptedException e) {
            LOGGER.error("Error while running the container for build id "+this.inputBuildId.toString(), e);
            killDockerContainer(docker, false);
        } catch (DockerException e) {
            LOGGER.error("Error while creating or running the container for build id "+this.inputBuildId.toString(), e);
            serialize("ERROR");
        }
        this.poolManager.removeSubmittedRunnablePipelineContainer(this);
    }

    private void removeVolumes(DockerClient docker) throws DockerException, InterruptedException {
        for (String volume : this.volumes) {
            docker.removeVolume(volume);
        }
    }

    /**
     * In case of timeout, we kill the container.
     * @param remove if true, it will remove both the container and the volumes
     */
    public void killDockerContainer(DockerClient docker, boolean remove) {
        if (this.containerId == null) {
            LOGGER.error("Error while trying to kill docker container: the container id is not available. Maybe the container is not started yet.");
            return;
        }

        LOGGER.info("Killing the docker container with id "+containerId+". Forced killing date: "+this.limitDateBeforeKilling);
        try {
            docker.killContainer(containerId);
            if (remove) {
                removeDockerContainer(docker);
            }
            this.poolManager.removeSubmittedRunnablePipelineContainer(this);
            serialize("INTERRUPTED");
        } catch (DockerException | InterruptedException e) {
            LOGGER.error("Error while killing docker container "+containerId, e);
        }
    }

    public void removeDockerContainer(DockerClient docker){
        try {
            docker.removeContainer(containerId);
            this.removeVolumes(docker);
        } catch (DockerException | InterruptedException e){
            LOGGER.error("Error while removing docker container: " + containerId, e);
        }
    }

    public void serialize(String msg) {
        treatedBuildTracking.setStatus(msg);
        treatedBuildTracking.serialize();
    }

}
