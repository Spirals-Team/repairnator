package fr.inria.spirals.repairnator.pipeline;

import ch.qos.logback.classic.Level;
import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.stringparsers.EnumeratedStringParser;
import fr.inria.jtravis.JTravis;
import fr.inria.jtravis.entities.Build;
import fr.inria.jtravis.entities.StateType;
import fr.inria.spirals.repairnator.*;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.notifier.*;
import fr.inria.spirals.repairnator.notifier.engines.NotifierEngine;
import fr.inria.spirals.repairnator.process.inspectors.InspectorFactory;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.step.repair.NPERepair;
import fr.inria.spirals.repairnator.serializer.*;
import fr.inria.spirals.repairnator.serializer.engines.SerializerEngine;
import fr.inria.spirals.repairnator.states.LauncherMode;
import fr.inria.spirals.repairnator.states.ScannedBuildStatus;
import fr.inria.spirals.repairnator.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.*;

/**
 * This class is the main entry point for the repairnator pipeline.
 */
public class LegacyLauncher implements LauncherAPI {
    private static Logger LOGGER = LoggerFactory.getLogger(Launcher.class);
    protected static Listener listener = null;
    private static File tempDir;
    protected BuildToBeInspected buildToBeInspected;
    protected List<SerializerEngine> engines;
    protected List<AbstractNotifier> notifiers;
    protected PatchNotifier patchNotifier;
    protected ProjectInspector inspector;

    @Override
    public RepairnatorConfig getConfig() {
        return RepairnatorConfig.getInstance();
    }

    /* just give an empty instance of the launcher for customized execution */
    public LegacyLauncher() {
        /* Reset fields*/
        this.listener = null;
        this.buildToBeInspected = null;
        this.engines = null;
        this.notifiers = null;
        this.patchNotifier = null;
        this.inspector = null;
    }

    public LegacyLauncher(String[] args) throws JSAPException {
        InputStream propertyStream = getClass().getResourceAsStream("/version.properties");
        Properties properties = new Properties();
        if (propertyStream != null) {
            try {
                properties.load(propertyStream);
            } catch (IOException e) {
                LOGGER.error("Error while loading property file.", e);
            }
            LOGGER.info("PIPELINE VERSION: {}", properties.getProperty("PIPELINE_VERSION"));
        } else {
            LOGGER.info("No information about PIPELINE VERSION has been found.");
        }

        JSAP jsap = this.defineArgs();
        JSAPResult arguments = jsap.parse(args);
        LauncherUtils.checkArguments(jsap, arguments, LauncherType.PIPELINE);
        this.initConfig(arguments);

        if (this.getConfig().getLauncherMode() == LauncherMode.REPAIR || this.getConfig().getLauncherMode() == LauncherMode.SEQUENCER_REPAIR) {
            this.checkNopolSolverPath(jsap);
            LOGGER.info("The pipeline will try to repair the following build id: {}", this.getConfig().getBuildId());
        } else {
            this.checkNextBuildId(jsap);
            LOGGER.info("The pipeline will try to reproduce a bug from build {} and its corresponding patch from build {}",
                    this.getConfig().getBuildId(), this.getConfig().getNextBuildId());
        }

        if (this.getConfig().isDebug()) {
            Utils.setLoggersLevel(Level.DEBUG);
        } else {
            Utils.setLoggersLevel(Level.INFO);
        }

        this.initSerializerEngines();
        this.initNotifiers();
    }

    @Override
    public JSAP defineArgs() throws JSAPException {
        // Verbose output
        JSAP jsap = new JSAP();

        // Register arguments common to all Launchers
        LauncherUtils.registerCommonArgs(jsap);
        // Register arguments for TravisCI
        TravisLauncherUtils.registerTravisArgs(jsap);

        FlaggedOption opt2 = new FlaggedOption("repairTools");
        opt2.setLongFlag("repairTools");
        String availablerepairTools = StringUtils.join(RepairToolsManager.getRepairToolsName(), ",");
        opt2.setStringParser(EnumeratedStringParser.getParser(availablerepairTools.replace(',', ';'), true));
        opt2.setList(true);
        opt2.setListSeparator(',');
        opt2.setHelp("Specify one or several repair tools to use among: " + availablerepairTools);
        opt2.setDefault(NPERepair.TOOL_NAME); // default one is not all available ones
        jsap.registerParameter(opt2);

        return jsap;
    }

    protected void initConfig(JSAPResult arguments) {
        LauncherUtils.initCommonConfig(this.getConfig(), arguments);
        TravisLauncherUtils.initTravisConfig(this.getConfig(), arguments);

        if (this.getConfig().getLauncherMode() == LauncherMode.SEQUENCER_REPAIR) {
            this.getConfig().setRepairTools(new HashSet<>(Collections.singletonList("SequencerRepair")));
        } else {
            this.getConfig().setRepairTools(new HashSet<>(Arrays.asList(arguments.getStringArray("repairTools"))));
        }

        if (this.getConfig().getLauncherMode() == LauncherMode.REPAIR) {
            LOGGER.info("The following repair tools will be used: {}", StringUtils.join(getConfig().getRepairTools(), ", "));
        }
    }

    protected void checkNopolSolverPath(JSAP jsap) {
        String solverPath = this.getConfig().getZ3solverPath();
        // by default Nopol run in Dynamoth mode
        // so no solver is mandatory
    }

    private void checkNextBuildId(JSAP jsap) {
        if (this.getConfig().getNextBuildId() == TravisInputBuild.NO_PATCH) {
            System.err.println("A pair of builds needs to be provided in BEARS mode.");
        }
    }

    protected void initSerializerEngines() {
        this.engines = new ArrayList<>();

        List<SerializerEngine> fileSerializerEngines = LauncherUtils.initFileSerializerEngines(LOGGER);
        this.engines.addAll(fileSerializerEngines);

        SerializerEngine mongoDBSerializerEngine = LauncherUtils.initMongoDBSerializerEngine(LOGGER);
        if (mongoDBSerializerEngine != null) {
            this.engines.add(mongoDBSerializerEngine);
        }
    }

    protected void initNotifiers() {
        List<NotifierEngine> notifierEngines = LauncherUtils.initNotifierEngines(LOGGER);
        ErrorNotifier.getInstance(notifierEngines);

        this.notifiers = new ArrayList<>();
        this.notifiers.add(new BugAndFixerBuildsNotifier(notifierEngines));

        this.patchNotifier = new PatchNotifierImpl(notifierEngines);
    }

    private List<String> getListOfProjectsToIgnore() {
        List<String> result = new ArrayList<>();
        if (this.getConfig().getProjectsToIgnoreFilePath() != null) {
            try {
                List<String> lines = Files.readAllLines(new File(this.getConfig().getProjectsToIgnoreFilePath()).toPath());
                for (String line : lines) {
                    result.add(line.trim().toLowerCase());
                }
            } catch (IOException e) {
                LOGGER.error("Error while reading projects to be ignored from file " + this.getConfig().getProjectsToIgnoreFilePath(), e);
            }
        }
        return result;
    }

    protected boolean getBuildToBeInspected() {
        JTravis jTravis = this.getConfig().getJTravis();
        Optional<Build> optionalBuild = jTravis.build().fromId(this.getConfig().getBuildId());
        if (!optionalBuild.isPresent()) {
            LOGGER.error("Error while retrieving the buggy build. The process will exit now.");
            return false;
        }

        Build buggyBuild = optionalBuild.get();
        if (buggyBuild.getFinishedAt() == null) {
            LOGGER.error("Apparently the buggy build is not yet finished (maybe it has been restarted?). The process will exit now.");
            return false;
        }
        String runId = this.getConfig().getRunId();

        if (this.getConfig().getLauncherMode() == LauncherMode.BEARS) {
            Optional<Build> optionalBuildPatch = jTravis.build().fromId(this.getConfig().getNextBuildId());
            if (!optionalBuildPatch.isPresent()) {
                LOGGER.error("Error while getting patched build: null value was obtained. The process will exit now.");
                return false;
            }

            Build patchedBuild = optionalBuildPatch.get();
            LOGGER.info("The patched build (" + patchedBuild.getId() + ") was successfully retrieved from Travis.");

            if (buggyBuild.getState() == StateType.FAILED) {
                this.buildToBeInspected = new BuildToBeInspected(buggyBuild, patchedBuild, ScannedBuildStatus.FAILING_AND_PASSING, runId);
            } else {
                this.buildToBeInspected = new BuildToBeInspected(buggyBuild, patchedBuild, ScannedBuildStatus.PASSING_AND_PASSING_WITH_TEST_CHANGES, runId);
            }
        } else {
            Optional<Build> optionalNextPassing = jTravis.build().getAfter(buggyBuild, true, StateType.PASSED);
            if (optionalNextPassing.isPresent()) {
                this.buildToBeInspected = new BuildToBeInspected(buggyBuild, optionalNextPassing.get(), ScannedBuildStatus.FAILING_AND_PASSING, runId);
            } else {
                this.buildToBeInspected = new BuildToBeInspected(buggyBuild, null, ScannedBuildStatus.ONLY_FAIL, runId);
            }

            // switch off push mechanism in case of test project
            // and switch off serialization
            String project = buggyBuild.getRepository().getSlug().toLowerCase();
            if (this.getListOfProjectsToIgnore().contains(project)) {
                this.getConfig().setPush(false);
                this.getConfig().setFork(false);
                this.getConfig().setCreatePR(false);
                this.engines.clear();
                LOGGER.info("The build " + this.getConfig().getBuildId() + " is from a project to be ignored (" + project + "), thus the pipeline deactivated serialization for that build.");
            }
        }
        return true;
    }

    @Override
    public boolean mainProcess() {
        LOGGER.info("Start by getting the build (buildId: " + this.getConfig().getBuildId() + ") with the following config: " + this.getConfig());
        if (!this.getBuildToBeInspected()) {
            return false;
        }

        HardwareInfoSerializer hardwareInfoSerializer = new HardwareInfoSerializer(this.engines, this.getConfig().getRunId(), this.getConfig().getBuildId() + "");
        hardwareInfoSerializer.serialize();

        List<AbstractDataSerializer> serializers = new ArrayList<>();

        LauncherMode launcherMode = this.getConfig().getLauncherMode();
        String workspacePath = this.getConfig().getWorkspacePath();
        switch (launcherMode) {
            case BEARS:
                inspector = InspectorFactory.getBearsInspector(buildToBeInspected, workspacePath, this.notifiers);
                break;
            case CHECKSTYLE:
                inspector = InspectorFactory.getCheckStyleInspector(buildToBeInspected, workspacePath, this.notifiers);
                break;
            case SEQUENCER_REPAIR:
                inspector = InspectorFactory.getSequencerRepairInspector(buildToBeInspected, workspacePath, this.notifiers);
                break;
            default:
                inspector = InspectorFactory.getTravisInspector(buildToBeInspected, workspacePath, this.notifiers);
                break;
        }

        System.out.println("Finished " + this.inspector.isPipelineEnding());
        if (launcherMode == LauncherMode.BEARS) {
            inspector.getSerializers().add(new InspectorSerializer4Bears(this.engines, inspector));
        } else {
            inspector.getSerializers().add(new InspectorSerializer(this.engines, inspector));
        }

        inspector.getSerializers().add(new PropertiesSerializer(this.engines, inspector));
        inspector.getSerializers().add(new InspectorTimeSerializer(this.engines, inspector));
        inspector.getSerializers().add(new PipelineErrorSerializer(this.engines, inspector));
        inspector.getSerializers().add(new PatchesSerializer(this.engines, inspector));
        inspector.getSerializers().add(new ToolDiagnosticSerializer(this.engines, inspector));
        inspector.getSerializers().add(new PullRequestSerializer(this.engines, inspector));

        inspector.setPatchNotifier(this.patchNotifier);
        inspector.run();

        if (this.getConfig().getTempWorkspace()) {
            new File(this.getConfig().getWorkspacePath()).delete();
        }

        LOGGER.info("Inspector is finished. The process will exit now.");
        return true;
    }

    /**
     * if listener mode is NOOP it will run the usual pipeline, aka mainProcess in runListenerServer
     * if KUBERNETES it will run as ActiveMQListener and run kubernetesProcess.
     *
     * @param launcher , launch depending on listenerMode
     */
    protected void initProcess(LegacyLauncher launcher) {
        try {
            Constructor c = Class.forName(this.getConfig().getListenerMode().getKlass()).getConstructor(LauncherAPI.class);
            listener = (Listener) c.newInstance(launcher);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        listener.runListenerServer();
    }

    @Override
    public void launch() {
        initProcess(this);
    }

    @Override
    public ProjectInspector getInspector() {
        return inspector;
    }

    public void setPatchNotifier(PatchNotifier patchNotifier) {
        this.patchNotifier = patchNotifier;
    }
}