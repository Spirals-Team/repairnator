package fr.inria.spirals.repairnator.pipeline;

import ch.qos.logback.classic.Level;
import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.stringparsers.EnumeratedStringParser;
import fr.inria.spirals.jtravis.entities.Build;
import fr.inria.spirals.jtravis.entities.BuildStatus;
import fr.inria.spirals.jtravis.helpers.BuildHelper;
import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.GoogleSpreadSheetFactory;
import fr.inria.spirals.repairnator.LauncherMode;
import fr.inria.spirals.repairnator.ScannedBuildStatus;
import fr.inria.spirals.repairnator.Utils;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.config.RepairnatorConfigException;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector4Bears;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.serializer.gsheet.inspectors.GoogleSpreadSheetInspectorSerializer;
import fr.inria.spirals.repairnator.serializer.gsheet.inspectors.GoogleSpreadSheetInspectorSerializer4Bears;
import fr.inria.spirals.repairnator.serializer.gsheet.inspectors.GoogleSpreadSheetNopolSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by urli on 09/03/2017.
 */
public class Launcher {

    private static Logger LOGGER = LoggerFactory.getLogger(Launcher.class);
    private JSAP jsap;
    private JSAPResult arguments;
    private RepairnatorConfig config;
    private int buildId;
    private int previousBuildId;
    private BuildToBeInspected buildToBeInspected;

    public Launcher(String[] args) throws JSAPException {
        this.defineArgs();
        this.arguments = jsap.parse(args);
        this.checkArguments();
        this.checkEnvironmentVariables();
        try {
            this.config = RepairnatorConfig.getInstance();
        } catch (RepairnatorConfigException e) {
            System.err.println(e.getMessage());
            System.err.println(e.getCause());
            this.printUsage();
        }

        this.config.setLauncherMode(LauncherMode.valueOf(this.arguments.getString("launcherMode").toUpperCase()));

        if (this.config.getLauncherMode() == LauncherMode.REPAIR) {
            this.checkToolsLoaded();
            this.checkNopolSolverPath();
        }

        if (this.arguments.getBoolean("debug")) {
            Utils.setLoggersLevel(Level.DEBUG);
        } else {
            Utils.setLoggersLevel(Level.INFO);
        }

        this.buildId = this.arguments.getInt("build");
        this.previousBuildId = this.arguments.getInt("previousBuild");
    }

    private void checkNopolSolverPath() {
        String solverPath = this.config.getZ3solverPath();

        if (solverPath != null) {
            File file = new File(solverPath);

            if (!file.exists()) {
                System.err.println("The Nopol solver path should be an existing file: " + file.getPath() + " does not exist.");
                this.printUsage();
                System.exit(-1);
            }
        } else {
            System.err.println("The Nopol solver path should be provided.");
            this.printUsage();
            System.exit(-1);
        }
    }

    private void checkArguments() {
        if (!this.arguments.success()) {
            // print out specific error messages describing the problems
            for (java.util.Iterator<?> errs = arguments.getErrorMessageIterator(); errs.hasNext();) {
                System.err.println("Error: " + errs.next());
            }
            this.printUsage();
        }

        if (this.arguments.getBoolean("help")) {
            this.printUsage();
        }
    }

    private void defineArgs() throws JSAPException {
        // Verbose output
        this.jsap = new JSAP();

        // help
        Switch sw1 = new Switch("help");
        sw1.setShortFlag('h');
        sw1.setLongFlag("help");
        sw1.setDefault("false");
        this.jsap.registerParameter(sw1);

        // verbosity
        sw1 = new Switch("debug");
        sw1.setShortFlag('d');
        sw1.setLongFlag("debug");
        sw1.setDefault("false");
        this.jsap.registerParameter(sw1);

        FlaggedOption opt2 = new FlaggedOption("build");
        opt2.setShortFlag('b');
        opt2.setLongFlag("build");
        opt2.setStringParser(JSAP.INTEGER_PARSER);
        opt2.setRequired(true);
        opt2.setHelp("Specify the build id to use.");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("previousBuild");
        opt2.setShortFlag('p');
        opt2.setLongFlag("previousBuild");
        opt2.setStringParser(JSAP.INTEGER_PARSER);
        opt2.setDefault("-1");
        opt2.setHelp("Specify the previous build id to use, if needed.");
        this.jsap.registerParameter(opt2);

        String launcherModeValues = "";
        for (LauncherMode mode : LauncherMode.values()) {
            launcherModeValues += mode.name() + ";";
        }
        launcherModeValues.substring(0, launcherModeValues.length() - 2);

        // Launcher mode
        opt2 = new FlaggedOption("launcherMode");
        opt2.setShortFlag('m');
        opt2.setLongFlag("launcherMode");
        opt2.setStringParser(EnumeratedStringParser.getParser(launcherModeValues));
        opt2.setRequired(true);
        opt2.setHelp("Specify if RepairNator will be launch for repairing (REPAIR) or for collecting fixer builds (BEARS).");
        this.jsap.registerParameter(opt2);
    }

    private void checkEnvironmentVariables() {
        for (String envVar : Utils.ENVIRONMENT_VARIABLES) {
            if (System.getenv(envVar) == null) {
                System.err.println("You must set the following environment variable: "+envVar);
                this.printUsage();
            }
        }
    }

    private void printUsage() {
        System.err.println("Usage: java <repairnator-pipeline name> [option(s)]");
        System.err.println();
        System.err.println("Options : ");
        System.err.println();
        System.err.println(jsap.getHelp());
        System.err.println("Please note that the following environment variables must be set: ");
        for (String env : Utils.ENVIRONMENT_VARIABLES) {
            System.err.println(" - " + env);
        }
        System.err.println("For using Nopol, you must add tools.jar in your classpath from your installed jdk");
        System.exit(-1);
    }

    private void checkToolsLoaded() {
        URLClassLoader loader;

        try {
            loader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            loader.loadClass("com.sun.jdi.AbsentInformationException");
        } catch (ClassNotFoundException e) {
            System.err.println("Tools.jar must be loaded, here the classpath given for your app: "+System.getProperty("java.class.path"));
            this.printUsage();
            System.exit(-1);
        }
    }

    private void getBuildToBeInspected() {
        Build build = BuildHelper.getBuildFromId(this.buildId, null);

        if (this.config.getLauncherMode() == LauncherMode.REPAIR) {
            this.buildToBeInspected = new BuildToBeInspected(build, ScannedBuildStatus.ONLY_FAIL);
        } else {
            Build previousBuild = BuildHelper.getBuildFromId(this.previousBuildId, null);
            if (previousBuild != null) {
                if (previousBuild.getBuildStatus() == BuildStatus.FAILED) {
                    this.buildToBeInspected = new BuildToBeInspected(build, previousBuild, ScannedBuildStatus.FAILING_AND_PASSING);
                } else {
                    this.buildToBeInspected = new BuildToBeInspected(build, previousBuild, ScannedBuildStatus.PASSING_AND_PASSING_WITH_TEST_CHANGES);
                }
            } else {
                throw new RuntimeException("There was an error getting the previous build (id: "+this.previousBuildId+")");
            }
        }
    }

    private void mainProcess() throws IOException {
        LOGGER.info("Start by getting the build (buildId: "+this.buildId+") with the following config: "+this.config);
        this.getBuildToBeInspected();

        List<AbstractDataSerializer> serializers = new ArrayList<>();

        if (this.config.getLauncherMode() == LauncherMode.REPAIR) {
            GoogleSpreadSheetFactory.setSpreadsheetId(GoogleSpreadSheetFactory.REPAIR_SPREADSHEET_ID);

            serializers.add(new GoogleSpreadSheetInspectorSerializer(this.config.getGoogleSecretPath()));
            serializers.add(new GoogleSpreadSheetNopolSerializer(this.config.getGoogleSecretPath()));
        } else {
            GoogleSpreadSheetFactory.setSpreadsheetId(GoogleSpreadSheetFactory.BEAR_SPREADSHEET_ID);

            serializers.add(new GoogleSpreadSheetInspectorSerializer4Bears(this.config.getGoogleSecretPath()));
        }

        ProjectInspector inspector;

        if (config.getLauncherMode() == LauncherMode.BEARS) {
            inspector = new ProjectInspector4Bears(buildToBeInspected, this.config.getWorkspacePath(), serializers);
        } else {
            inspector = new ProjectInspector(buildToBeInspected, this.config.getWorkspacePath(), serializers);
        }
        inspector.run();
    }

    public static void main(String[] args) throws IOException, JSAPException {
        Launcher launcher = new Launcher(args);
        launcher.mainProcess();
    }
}
