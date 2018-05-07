package fr.inria.spirals.repairnator.process.step;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.inria.spirals.repairnator.process.inspectors.JobStatus;
import fr.inria.spirals.repairnator.process.inspectors.Metrics;
import fr.inria.spirals.repairnator.process.inspectors.MetricsSerializerAdapter;
import fr.inria.spirals.repairnator.process.inspectors.StepStatus;
import fr.inria.spirals.repairnator.process.step.push.PushIncriminatedBuild;
import fr.inria.spirals.repairnator.states.PipelineState;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.notifier.AbstractNotifier;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.states.PushState;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Created by urli on 03/01/2017.
 */
public abstract class AbstractStep {
    private static final String PROPERTY_FILENAME = "repairnator.json";

    /**
     * The name of the step, by default it's the class name
     * We can use a custom name to distinguish two different instances.
     */
    private String name;

    private ProjectInspector inspector;

    private boolean shouldStop;
    private AbstractStep nextStep;
    private long dateBegin;
    private long dateEnd;
    private boolean pomLocationTested;
    private List<AbstractDataSerializer> serializers;
    private List<AbstractNotifier> notifiers;
    private Properties properties;
    private RepairnatorConfig config;

    private PushState pushState;

    /**
     * If set to true, the failure of the step means a stop of the entire pipeline.
     */
    private boolean blockingStep;

    public AbstractStep(ProjectInspector inspector, boolean blockingStep) {
        this(inspector, "", blockingStep);
        this.name = this.getClass().getSimpleName();
    }

    public AbstractStep(ProjectInspector inspector, String name, boolean blockingStep) {
        this.name = name;
        this.inspector = inspector;
        this.shouldStop = false;
        this.pomLocationTested = false;
        this.serializers = new ArrayList<>();
        this.properties = new Properties();
        this.config = RepairnatorConfig.getInstance();
        this.blockingStep = blockingStep;
        this.initStates();
    }

    public void setBlockingStep(boolean blockingStep) {
        this.blockingStep = blockingStep;
    }

    public boolean isBlockingStep() {
        return blockingStep;
    }

    protected void initStates() {
        if (this.inspector != null) {
            this.setPushState(PushState.NONE);
        }
    }

    public void setNotifiers(List<AbstractNotifier> notifiers) {
        if (notifiers != null) {
            this.notifiers = notifiers;
            if (this.nextStep != null) {
                this.nextStep.setNotifiers(notifiers);
            }
        }
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
        if (this.nextStep != null) {
            this.nextStep.setProperties(properties);
        }
    }

    protected Properties getProperties() {
        return properties;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDataSerializer(List<AbstractDataSerializer> serializers) {
        if (serializers != null) {
            this.serializers = serializers;
            if (this.nextStep != null) {
                this.nextStep.setDataSerializer(serializers);
            }
        }
    }

    public ProjectInspector getInspector() {
        return inspector;
    }

    public AbstractStep setNextStep(AbstractStep nextStep) {
        this.nextStep = nextStep;
        nextStep.setDataSerializer(this.serializers);
        nextStep.setNotifiers(this.notifiers);
        nextStep.setProperties(this.properties);
        return nextStep;
    }

    protected void setPushState(PushState pushState) {
        if (pushState != null) {
            this.pushState = pushState;
            this.inspector.getJobStatus().setPushState(this.pushState);
            if (this.nextStep != null) {
                this.nextStep.setPushState(pushState);
            }
        }
    }

    protected Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }

    public void addStepError(String error) {
        getLogger().error(error);
        this.inspector.getJobStatus().addStepError(this.name, error);
    }

    public void addStepError(String error, Throwable exception) {
        getLogger().error(error, exception);
        this.inspector.getJobStatus().addStepError(this.name, error + " Original msg: " + exception.getMessage());
    }

    protected void executeNextStep() {
        this.observeAndNotify();
        if (this.nextStep != null) {
            this.nextStep.execute();
        } else {
            this.terminatePipeline();
        }
    }

    private void serializeData() {
        if (serializers != null) {
            this.getLogger().info("Serialize all data for build: "+this.getInspector().getBuggyBuild().getId());
            for (AbstractDataSerializer serializer : this.serializers) {
                serializer.serializeData(this.inspector);
            }
        }
    }

    private void observeAndNotify() {
        ProjectInspector inspector = this.getInspector();
        JobStatus jobStatus = inspector.getJobStatus();
        if (jobStatus.isHasBeenPatched() && !jobStatus.isHasBeenForked() && this.config.isPush() && this.config.isFork()) {
            String repositoryName = getInspector().getRepoSlug();
            getLogger().info("Fork the repository: "+repositoryName);
            try {
                String forkedRepoUrl = inspector.getGitHelper().forkRepository(repositoryName, this);
                if (forkedRepoUrl != null) {
                    jobStatus.setForkURL(forkedRepoUrl);
                    jobStatus.setHasBeenForked(true);
                    getLogger().info("Obtain the following fork URL: "+forkedRepoUrl);
                }
            } catch (IOException e) {
                getLogger().error("Error while forking the repository "+repositoryName, e);
            }

        }

        if (this.notifiers != null) {
            for (AbstractNotifier notifier : this.notifiers) {
                notifier.observe(this.inspector);
            }
        }
    }

    private void testPomLocation() {
        this.pomLocationTested = true;
        File defaultPomFile = new File(this.inspector.getRepoLocalPath() + File.separator + "pom.xml");

        if (defaultPomFile.exists()) {
            return;
        } else {
            this.getLogger().info("The pom.xml file is not at the root of the repository. Try to find another one.");

            File rootRepo = new File(this.inspector.getRepoLocalPath());

            File[] dirs = rootRepo.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.isDirectory();
                }
            });

            if (dirs != null) {
                Arrays.sort(dirs);
                for (File dir : dirs) {
                    File pomFile = new File(dir.getPath()+File.separator+"pom.xml");

                    if (pomFile.exists()) {
                        this.getLogger().info("Found a pom.xml in the following directory: "+dir.getPath());
                        this.inspector.getJobStatus().setPomDirPath(dir.getPath());
                        return;
                    }
                }
            }

            this.addStepError("RepairNator was unable to found a pom.xml in the repository. It will stop now.");
            this.shouldStop = true;
        }
    }

    protected String getPom() {
        if (!pomLocationTested) {
            testPomLocation();
        }
        return this.inspector.getJobStatus().getPomDirPath() + File.separator + "pom.xml";
    }

    protected void cleanMavenArtifacts() {
        if (this.inspector.getM2LocalPath() != null) {
            try {
                FileUtils.deleteDirectory(this.inspector.getM2LocalPath());
            } catch (IOException e) {
                getLogger().warn(
                        "Error while deleting the M2 local directory (" + this.inspector.getM2LocalPath() + "): " + e);
            }
        }

        if (this.config.isClean()) {
            try {
                FileUtils.deleteDirectory(this.inspector.getRepoLocalPath());
            } catch (IOException e) {
                getLogger().warn("Error while deleting the workspace directory (" + this.inspector.getRepoLocalPath()
                        + "): " + e);
            }
        }
    }

    public void setProjectInspector(ProjectInspector inspector) {
        this.inspector = inspector;
        this.initStates();
    }

    public boolean isShouldStop() {
        return this.shouldStop;
    }

    public void execute() {
        this.dateBegin = new Date().getTime();
        StepStatus stepStatus = this.businessExecute();
        this.dateEnd = new Date().getTime();

        Metrics metric = this.inspector.getJobStatus().getMetrics();
        metric.addStepDuration(this.name, getDuration());
        metric.addFreeMemoryByStep(this.name, Runtime.getRuntime().freeMemory());

        this.inspector.getJobStatus().addStepStatus(stepStatus);

        this.shouldStop = this.shouldStop || (this.isBlockingStep() && !stepStatus.isSuccess());
        if (!this.shouldStop) {
            this.executeNextStep();
        } else {
            this.terminatePipeline();
        }
    }

    private void terminatePipeline() {
        this.recordMetrics();
        this.writeProperty("metrics", this.inspector.getJobStatus().getMetrics());
        this.lastPush();
        this.serializeData();
        this.cleanMavenArtifacts();
    }

    private void recordMetrics() {
        Metrics metric = this.inspector.getJobStatus().getMetrics();

        metric.setFreeMemory(Runtime.getRuntime().freeMemory());
        metric.setTotalMemory(Runtime.getRuntime().totalMemory());
        metric.setNbCPU(Runtime.getRuntime().availableProcessors());
    }

    private int getDuration() {
        if (dateEnd == 0 || dateBegin == 0) {
            return 0;
        }
        return Math.round((dateEnd - dateBegin) / 1000);
    }

    // FIXME: this method should not be placed here
    private void lastPush() {
        if (RepairnatorConfig.getInstance().isPush() && this.getInspector().getJobStatus().getPushState() != PushState.NONE) {
            File sourceDir = new File(this.getInspector().getRepoLocalPath());
            File targetDir = new File(this.getInspector().getRepoToPushLocalPath());

            try {
                Git git = Git.open(targetDir);

                org.apache.commons.io.FileUtils.copyDirectory(sourceDir, targetDir, new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return !pathname.toString().contains(".git") && !pathname.toString().contains(".m2") && !pathname.toString().contains(".travis.yml");
                    }
                });

                git.add().addFilepattern(".").call();

                for (String fileToPush : this.getInspector().getJobStatus().getCreatedFilesToPush()) {
                    // add force is not supported by JGit...
                    ProcessBuilder processBuilder = new ProcessBuilder("git", "add", "-f",fileToPush)
                            .directory(git.getRepository().getDirectory().getParentFile()).inheritIO();

                    try {
                        Process p = processBuilder.start();
                        p.waitFor();
                    } catch (InterruptedException|IOException e) {
                        this.getLogger().error("Error while executing git command to add files: " + e);
                    }
                }

                PersonIdent personIdent = new PersonIdent("Luc Esape", "luc.esape@gmail.com");
                git.commit().setMessage("End of the repairnator process")
                        .setAuthor(personIdent).setCommitter(personIdent).call();

                if (this.getInspector().getJobStatus().isHasBeenPushed()) {
                    CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(this.config.getGithubToken(), "");
                    git.push().setRemote(PushIncriminatedBuild.REMOTE_NAME).setCredentialsProvider(credentialsProvider).call();
                }
            } catch (GitAPIException | IOException e) {
                this.getLogger().error("Error while trying to commit last information for repairnator", e);
            }
        }


    }

    protected void writeProperty(String propertyName, Object value) {
        if (value != null) {
            this.properties.put(propertyName, value);

            String filePath = this.inspector.getRepoLocalPath() + File.separator + PROPERTY_FILENAME;
            File file = new File(filePath);

            Gson gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Metrics.class, new MetricsSerializerAdapter()).create();
            String jsonString = gson.toJson(this.properties);

            try {
                if (!file.exists()) {
                    file.createNewFile();
                }
                OutputStreamWriter outputStream = new OutputStreamWriter(new FileOutputStream(file));
                outputStream.write(jsonString);
                outputStream.flush();
                outputStream.close();
            } catch (IOException e) {
                this.getLogger().error("Cannot write property to the following file: " + filePath, e);
            }
        } else {
            this.getLogger().warn("Trying to write property null for key: "+propertyName);
        }

    }

    public RepairnatorConfig getConfig() {
        return config;
    }

    protected abstract StepStatus businessExecute();
}
