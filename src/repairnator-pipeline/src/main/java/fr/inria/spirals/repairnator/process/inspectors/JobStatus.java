package fr.inria.spirals.repairnator.process.inspectors;

import com.google.gson.JsonElement;
import fr.inria.spirals.repairnator.process.inspectors.properties.Properties;
import fr.inria.spirals.repairnator.process.inspectors.properties.features.Features;
import fr.inria.spirals.repairnator.process.inspectors.properties.tests.FailureDetail;
import fr.inria.spirals.repairnator.process.step.StepStatus;
import fr.inria.spirals.repairnator.process.testinformation.FailureLocation;
import fr.inria.spirals.repairnator.states.PushState;
import org.apache.maven.model.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.bjurr.violations.lib.model.Violation;

import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * This class contains all information about the status of the pipeline
 */
public class JobStatus {
    private final Logger logger = LoggerFactory.getLogger(JobStatus.class);

    private List<PushState> pushStates;
    private List<URL> repairClassPath;

    private File[] repairSourceDir;
    private File[] testDir;
    private File[] modules;
    private List<Plugin> plugins;

    /**
     * List of patches indexed by the name of the tool to produce them
     */
    private Map<String, List<RepairPatch>> listOfPatches;

    /**
     * Diagnostic about repair tool on the form of a JsonElement
     */
    private Map<String, JsonElement> toolDiagnostic;

    private boolean isReproducedAsFail;
    private String pomDirPath;
    private boolean hasBeenPushed;

    private Set<FailureLocation> failureLocations;
    private String failingModulePath;
    private Map<String, List<String>> stepErrors;
    private String gitBranchUrl;
    private boolean hasBeenPatched;
    private Throwable fatalError;

    private Set<Violation> checkstyleViolations;

    private Properties properties;

    private List<String> createdFilesToPush;
    private boolean hasBeenForked;
    private String forkURL;

    private List<StepStatus> stepStatuses;
    private Map<String, Integer> stepsDurationsInSeconds;
    private Map<String, Long> freeMemoryByStep;

    private List<String> PRCreated;

    public JobStatus(String pomDirPath) {
        this.stepErrors = new HashMap<>();
        this.pomDirPath = pomDirPath;
        this.repairSourceDir = new File[]{new File("src/main/java")};
        this.failingModulePath = pomDirPath;
        this.properties = new Properties();
        this.createdFilesToPush = new ArrayList<>();
        this.stepStatuses = new ArrayList<>();
        this.stepsDurationsInSeconds = new HashMap<>();
        this.freeMemoryByStep = new HashMap<>();
        this.pushStates = new ArrayList<>();
        this.listOfPatches = new HashMap<>();
        this.toolDiagnostic = new HashMap<>();
        this.repairClassPath = new ArrayList<>();
        this.PRCreated = new ArrayList<>();
    }

    public List<URL> getRepairClassPath() {
        return repairClassPath;
    }

    public void setRepairClassPath(List<URL> repairClassPath) {
        this.repairClassPath = repairClassPath;
    }

    public File[] getRepairSourceDir() {
        return repairSourceDir;
    }

    public void setRepairSourceDir(File[] repairSourceDir) {
        this.repairSourceDir = repairSourceDir;
    }

    public boolean isReproducedAsFail() {
        return isReproducedAsFail;
    }

    public void setReproducedAsFail(boolean reproducedAsFail) {
        isReproducedAsFail = reproducedAsFail;
    }

    public String getPomDirPath() {
        return pomDirPath;
    }

    public void setPomDirPath(String pomDirPath) {
        this.pomDirPath = pomDirPath;
    }

    public void addStepError(String step, String error) {
        if (!stepErrors.containsKey(step)) {
            stepErrors.put(step, new ArrayList<String>());
        }

        List<String> errors = stepErrors.get(step);
        errors.add(error);
    }


    public boolean isHasBeenPushed() {
        return hasBeenPushed;
    }

    public void setHasBeenPushed(boolean hasBeenPushed) {
        this.hasBeenPushed = hasBeenPushed;
    }

    public Map<String, List<String>> getStepErrors() {
        return stepErrors;
    }

    public String getFailingModulePath() {
        return failingModulePath;
    }

    public void setFailingModulePath(String failingModulePath) {
        this.failingModulePath = failingModulePath;
        this.properties.getTests().setFailingModule(this.failingModulePath);
    }

    public Set<FailureLocation> getFailureLocations() {
        return failureLocations;
    }

    public void setFailureLocations(Set<FailureLocation> failureLocations) {
        this.failureLocations = failureLocations;
    }


    public Set<Violation> getCheckstyleViolations() {
        return checkstyleViolations;
    }

    public void setCheckstyleViolations(Set<Violation> checkstyleViolations) {
        this.checkstyleViolations = checkstyleViolations;
    }

    public String getGitBranchUrl() {
        return gitBranchUrl;
    }

    public void setGitBranchUrl(String gitBranchUrl) {
        this.gitBranchUrl = gitBranchUrl;
    }

    public boolean isHasBeenPatched() {
        return hasBeenPatched;
    }

    public void setHasBeenPatched(boolean hasBeenPatched) {
        this.hasBeenPatched = hasBeenPatched;
    }

    public List<PushState> getPushStates() {
        return pushStates;
    }

    public PushState getLastPushState() {
        return pushStates.get(pushStates.size() - 1);
    }

    public void addPushState(PushState pushState) {
        this.pushStates.add(pushState);
    }

    public Properties getProperties() {
        return properties;
    }

    public File[] getTestDir() {
        return testDir;
    }

    public void setTestDir(File[] testDir) {
        this.testDir = testDir;
    }

    public File[] getModules() {
        return modules;
    }

    public void setModules(File[] modules) {
        this.modules = modules;
    }

    public List<Plugin> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<Plugin> plugins) {
        this.plugins = plugins;
    }

    public Throwable getFatalError() {
        return fatalError;
    }

    public void setFatalError(Throwable fatalError) {
        this.fatalError = fatalError;
    }

    public void addFileToPush(String filePath) {
        if (!this.createdFilesToPush.contains(filePath)) {
            this.createdFilesToPush.add(filePath);
        }
    }

    public List<String> getCreatedFilesToPush() {
        return createdFilesToPush;
    }

    public boolean isCreatedFileToPush(String filePath) {
        for (String createdFileToPush : this.createdFilesToPush) {
            if (filePath.endsWith(createdFileToPush)) {
                return true;
            }
        }
        return false;
    }

    public void setHasBeenForked(boolean hasBeenForked) {
        this.hasBeenForked = hasBeenForked;
    }

    public boolean isHasBeenForked() {
        return hasBeenForked;
    }

    public String getForkURL() {
        return forkURL;
    }

    public void setForkURL(String forkURL) {
        this.forkURL = forkURL;
    }

    public void addStepStatus(StepStatus stepStatus) {
        this.stepStatuses.add(stepStatus);
    }

    public void addToolDiagnostic(String toolName, JsonElement diagnostic) {
        this.toolDiagnostic.put(toolName, diagnostic);
    }

    public List<StepStatus> getStepStatuses() {
        return stepStatuses;
    }

    public void addPatches(String toolName, List<RepairPatch> patches) {
        this.listOfPatches.put(toolName, patches);
    }

    public List<RepairPatch> getAllPatches() {
        List<RepairPatch> allPatches = new ArrayList<>();
        for (List<RepairPatch> repairPatches : this.listOfPatches.values()) {
            allPatches.addAll(repairPatches);
        }
        return allPatches;
    }

    protected List<RepairPatch> getCorrectnessLabeledPatches(Features features, String buildId) {
        List<RepairPatch> allPatches = getAllPatches();
        return RepairPatch.classifyByODSWithFeatures(allPatches, buildId);
            
    }    
   
    public Map<String, List<RepairPatch>> getListOfPatches() {
        return listOfPatches;
    }

    public Map<String, JsonElement> getToolDiagnostic() {
        return toolDiagnostic;
    }

    public List<String> getPRCreated() {
        return PRCreated;
    }

    public void addPRCreated(String prURL) {
        this.PRCreated.add(prURL);
    }

    public Map<String, Integer> getStepsDurationsInSeconds() {
        return stepsDurationsInSeconds;
    }

    public void addStepDuration(String step, int duration) {
        this.stepsDurationsInSeconds.put(step, duration);
    }

    public Map<String, Long> getFreeMemoryByStep() {
        return freeMemoryByStep;
    }

    public void addFreeMemoryByStep(String step, long value) {
        this.freeMemoryByStep.put(step, value);
    }

    public List<String> getFailureNames() {
        List<String> failureNames = new ArrayList<>();
        for (FailureDetail failureDetail : this.properties.getTests().getFailureDetails()) {
            if (!failureNames.contains(failureDetail.getFailureName())) {
                failureNames.add(failureDetail.getFailureName());
            }
        }
        return failureNames;
    }
}
