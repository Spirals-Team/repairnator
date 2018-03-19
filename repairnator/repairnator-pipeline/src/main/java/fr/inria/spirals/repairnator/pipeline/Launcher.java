package fr.inria.spirals.repairnator.pipeline;

import ch.qos.logback.classic.Level;
import com.martiansoftware.jsap.*;
import com.martiansoftware.jsap.stringparsers.FileStringParser;
import fr.inria.jtravis.entities.Build;
import fr.inria.jtravis.entities.BuildStatus;
import fr.inria.jtravis.helpers.BuildHelper;
import fr.inria.jtravis.helpers.GithubTokenHelper;
import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.LauncherType;
import fr.inria.spirals.repairnator.LauncherUtils;
import fr.inria.spirals.repairnator.notifier.ErrorNotifier;
import fr.inria.spirals.repairnator.serializer.AstorSerializer;
import fr.inria.spirals.repairnator.serializer.MetricsSerializer;
import fr.inria.spirals.repairnator.serializer.NPEFixSerializer;
import fr.inria.spirals.repairnator.states.LauncherMode;
import fr.inria.spirals.repairnator.states.ScannedBuildStatus;
import fr.inria.spirals.repairnator.Utils;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.notifier.AbstractNotifier;
import fr.inria.spirals.repairnator.notifier.FixerBuildNotifier;
import fr.inria.spirals.repairnator.notifier.PatchNotifier;
import fr.inria.spirals.repairnator.notifier.engines.NotifierEngine;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector4Bears;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.serializer.HardwareInfoSerializer;
import fr.inria.spirals.repairnator.serializer.InspectorSerializer;
import fr.inria.spirals.repairnator.serializer.InspectorSerializer4Bears;
import fr.inria.spirals.repairnator.serializer.InspectorTimeSerializer;
import fr.inria.spirals.repairnator.serializer.InspectorTimeSerializer4Bears;
import fr.inria.spirals.repairnator.serializer.NopolSerializer;
import fr.inria.spirals.repairnator.serializer.engines.SerializerEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by urli on 09/03/2017.
 */
public class Launcher {
    private static final String TEST_PROJECT = "surli/failingproject"; // be careful when testing: this project deactivate serialization
    private static Logger LOGGER = LoggerFactory.getLogger(Launcher.class);
    private JSAP jsap;
    private RepairnatorConfig config;
    private BuildToBeInspected buildToBeInspected;
    private List<SerializerEngine> engines;
    private List<AbstractNotifier> notifiers;

    public Launcher(String[] args) throws JSAPException {
        InputStream propertyStream = getClass().getResourceAsStream("/version.properties");
        Properties properties = new Properties();
        if (propertyStream != null) {
            try {
                properties.load(propertyStream);
            } catch (IOException e) {
                LOGGER.error("Error while loading property file", e);
            }
            LOGGER.info("PIPELINE VERSION: "+properties.getProperty("PIPELINE_VERSION"));
        } else {
            LOGGER.info("No information about PIPELINE VERSION has been found.");
        }

        this.defineArgs();
        JSAPResult arguments = jsap.parse(args);
        LauncherUtils.checkArguments(this.jsap, arguments, LauncherType.PIPELINE);
        this.initConfig(arguments);

        if (this.config.getLauncherMode() == LauncherMode.REPAIR) {
            this.checkToolsLoaded();
            this.checkNopolSolverPath();
        }

        if (LauncherUtils.getArgDebug(arguments)) {
            Utils.setLoggersLevel(Level.DEBUG);
        } else {
            Utils.setLoggersLevel(Level.INFO);
        }

        LOGGER.info("The pipeline will try to repair the following buildid: "+this.config.getBuildId());

        this.initSerializerEngines();
        this.initNotifiers();
    }

    private void defineArgs() throws JSAPException {
        // Verbose output
        this.jsap = new JSAP();

        // -h or --help
        this.jsap.registerParameter(LauncherUtils.defineArgHelp());
        // -d or --debug
        this.jsap.registerParameter(LauncherUtils.defineArgDebug());
        // --runId
        this.jsap.registerParameter(LauncherUtils.defineArgRunId());
        // -m or --launcherMode
        this.jsap.registerParameter(LauncherUtils.defineArgLauncherMode("Specify if RepairNator will be launch for repairing (REPAIR) or for collecting fixer builds (BEARS)."));
        // -o or --output
        this.jsap.registerParameter(LauncherUtils.defineArgOutput(LauncherType.PIPELINE, "Specify path to output serialized files"));
        // --dbhost
        this.jsap.registerParameter(LauncherUtils.defineArgMongoDBHost());
        // --dbname
        this.jsap.registerParameter(LauncherUtils.defineArgMongoDBName());
        // --spreadsheet
        this.jsap.registerParameter(LauncherUtils.defineArgSpreadsheetId());
        // --googleAccessToken
        this.jsap.registerParameter(LauncherUtils.defineArgGoogleAccessToken());
        // --smtpServer
        this.jsap.registerParameter(LauncherUtils.defineArgSmtpServer());
        // --notifyto
        this.jsap.registerParameter(LauncherUtils.defineArgNotifyto());
        // --pushurl
        this.jsap.registerParameter(LauncherUtils.defineArgPushUrl());

        FlaggedOption opt2 = new FlaggedOption("build");
        opt2.setShortFlag('b');
        opt2.setLongFlag("build");
        opt2.setStringParser(JSAP.INTEGER_PARSER);
        opt2.setRequired(true);
        opt2.setHelp("Specify the build id to use.");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("z3");
        opt2.setLongFlag("z3");
        opt2.setDefault("./z3_for_linux");
        opt2.setStringParser(FileStringParser.getParser().setMustBeFile(true).setMustExist(true));
        opt2.setHelp("Specify path to Z3");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("workspace");
        opt2.setLongFlag("workspace");
        opt2.setShortFlag('w');
        opt2.setDefault("./workspace");
        opt2.setStringParser(JSAP.STRING_PARSER);
        opt2.setHelp("Specify path to output serialized files");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("ghLogin");
        opt2.setLongFlag("ghLogin");
        opt2.setRequired(true);
        opt2.setStringParser(JSAP.STRING_PARSER);
        opt2.setHelp("Specify login for Github use");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("ghOauth");
        opt2.setLongFlag("ghOauth");
        opt2.setRequired(true);
        opt2.setStringParser(JSAP.STRING_PARSER);
        opt2.setHelp("Specify oauth for Github use");
        this.jsap.registerParameter(opt2);
    }

    private void initConfig(JSAPResult arguments) {
        this.config = RepairnatorConfig.getInstance();

        this.config.setClean(true);
        this.config.setRunId(LauncherUtils.getArgRunId(arguments));
        this.config.setLauncherMode(LauncherUtils.getArgLauncherMode(arguments));
        if (LauncherUtils.getArgOutput(arguments) != null) {
            this.config.setSerializeJson(true);
            this.config.setOutputPath(LauncherUtils.getArgOutput(arguments).getPath());
        }
        this.config.setMongodbHost(LauncherUtils.getArgMongoDBHost(arguments));
        this.config.setMongodbName(LauncherUtils.getArgMongoDBName(arguments));
        this.config.setSpreadsheetId(LauncherUtils.getArgSpreadsheetId(arguments));
        this.config.setGoogleAccessToken(LauncherUtils.getArgGoogleAccessToken(arguments));
        this.config.setSmtpServer(LauncherUtils.getArgSmtpServer(arguments));
        this.config.setNotifyTo(LauncherUtils.getArgNotifyto(arguments));
        if (LauncherUtils.getArgPushUrl(arguments) != null) {
            this.config.setPush(true);
            this.config.setPushRemoteRepo(LauncherUtils.getArgPushUrl(arguments));
        }
        this.config.setBuildId(arguments.getInt("build"));
        this.config.setZ3solverPath(arguments.getFile("z3").getPath());
        this.config.setWorkspacePath(arguments.getString("workspace"));
        this.config.setGithubLogin(arguments.getString("ghLogin"));
        this.config.setGithubToken(arguments.getString("ghOauth"));

        GithubTokenHelper.getInstance().setGithubOauth(this.config.getGithubToken());
        GithubTokenHelper.getInstance().setGithubLogin(this.config.getGithubLogin());
    }

    private void checkToolsLoaded() {
        URLClassLoader loader;

        try {
            loader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            loader.loadClass("com.sun.jdi.AbsentInformationException");
        } catch (ClassNotFoundException e) {
            System.err.println("Tools.jar must be loaded, here the classpath given for your app: "+System.getProperty("java.class.path"));
            LauncherUtils.printUsage(this.jsap, LauncherType.PIPELINE);
        }
    }

    private void checkNopolSolverPath() {
        String solverPath = this.config.getZ3solverPath();

        if (solverPath != null) {
            File file = new File(solverPath);

            if (!file.exists()) {
                System.err.println("The Nopol solver path should be an existing file: " + file.getPath() + " does not exist.");
                LauncherUtils.printUsage(this.jsap, LauncherType.PIPELINE);
            }
        } else {
            System.err.println("The Nopol solver path should be provided.");
            LauncherUtils.printUsage(this.jsap, LauncherType.PIPELINE);
        }
    }

    private void initSerializerEngines() {
        this.engines = new ArrayList<>();

        SerializerEngine spreadsheetSerializerEngine = LauncherUtils.initSpreadsheetSerializerEngineWithAccessToken(LOGGER);
        if (spreadsheetSerializerEngine != null) {
            this.engines.add(spreadsheetSerializerEngine);
        }

        List<SerializerEngine> fileSerializerEngines = LauncherUtils.initFileSerializerEngines(LOGGER);
        this.engines.addAll(fileSerializerEngines);

        SerializerEngine mongoDBSerializerEngine = LauncherUtils.initMongoDBSerializerEngine(LOGGER);
        if (mongoDBSerializerEngine != null) {
            this.engines.add(mongoDBSerializerEngine);
        }
    }

    private void initNotifiers() {
        List<NotifierEngine> notifierEngines = LauncherUtils.initNotifierEngines(LOGGER);
        ErrorNotifier.getInstance(notifierEngines);

        this.notifiers = new ArrayList<>();
        this.notifiers.add(new PatchNotifier(notifierEngines));
        this.notifiers.add(new FixerBuildNotifier(notifierEngines));
    }

    private void getBuildToBeInspected() {
        Build buggyBuild = BuildHelper.getBuildFromId(this.config.getBuildId(), null);

        if (buggyBuild.getFinishedAt() == null) {
            LOGGER.error("Apparently the buggy build is not yet finished (maybe it has been restarted?). The process will exit now.");
            System.exit(-1);
        }
        String runId = this.config.getRunId();

        if (this.config.getLauncherMode() == LauncherMode.BEARS) {
            Build patchedBuild = BuildHelper.getNextBuildOfSameBranchOfStatusAfterBuild(buggyBuild, null);
            if (patchedBuild == null) {
                LOGGER.error("Error while getting patched build: obtained null value. The process will exit now.");
                System.exit(-1);
            }

            if (buggyBuild.getBuildStatus() == BuildStatus.FAILED) {
                this.buildToBeInspected = new BuildToBeInspected(buggyBuild, patchedBuild, ScannedBuildStatus.FAILING_AND_PASSING, runId);
            } else {
                this.buildToBeInspected = new BuildToBeInspected(buggyBuild, patchedBuild, ScannedBuildStatus.PASSING_AND_PASSING_WITH_TEST_CHANGES, runId);
            }
        } else {
            Build nextPassing = BuildHelper.getNextBuildOfSameBranchOfStatusAfterBuild(buggyBuild, BuildStatus.PASSED);

            if (nextPassing != null) {
                this.buildToBeInspected = new BuildToBeInspected(buggyBuild, nextPassing, ScannedBuildStatus.FAILING_AND_PASSING, runId);
            } else {
                this.buildToBeInspected = new BuildToBeInspected(buggyBuild, null, ScannedBuildStatus.ONLY_FAIL, runId);
            }

            // switch off push mechanism in case of test project
            // and switch off serialization
            if (buggyBuild.getRepository().getSlug().toLowerCase().equals(TEST_PROJECT)) {
                this.config.setPush(false);
                this.engines.clear();
            }
        }
    }

    private void mainProcess() throws IOException {
        LOGGER.info("Start by getting the build (buildId: "+this.config.getBuildId()+") with the following config: "+this.config);
        this.getBuildToBeInspected();

        HardwareInfoSerializer hardwareInfoSerializer = new HardwareInfoSerializer(this.engines, this.config.getRunId(), this.config.getBuildId()+"");
        hardwareInfoSerializer.serialize();

        List<AbstractDataSerializer> serializers = new ArrayList<>();

        if (this.config.getLauncherMode() == LauncherMode.REPAIR) {
            serializers.add(new InspectorSerializer(this.engines));
            serializers.add(new InspectorTimeSerializer(this.engines));
        } else {
            serializers.add(new InspectorSerializer4Bears(this.engines));
            serializers.add(new InspectorTimeSerializer4Bears(this.engines));
        }
        serializers.add(new NopolSerializer(this.engines));
        serializers.add(new NPEFixSerializer(this.engines));
        serializers.add(new AstorSerializer(this.engines));
        serializers.add(new MetricsSerializer(this.engines));

        ProjectInspector inspector;

        if (config.getLauncherMode() == LauncherMode.BEARS) {
            inspector = new ProjectInspector4Bears(buildToBeInspected, this.config.getWorkspacePath(), serializers, this.notifiers);
        } else {
            inspector = new ProjectInspector(buildToBeInspected, this.config.getWorkspacePath(), serializers, this.notifiers);
        }
        inspector.run();

        LOGGER.info("Inspector is finished. The process will now exit.");
        System.exit(0);
    }

    public static void main(String[] args) throws IOException, JSAPException {
        Launcher launcher = new Launcher(args);
        launcher.mainProcess();
    }

}
