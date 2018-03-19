package fr.inria.spirals.repairnator.realtime;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.stringparsers.FileStringParser;
import fr.inria.spirals.repairnator.LauncherType;
import fr.inria.spirals.repairnator.LauncherUtils;
import fr.inria.spirals.repairnator.PeriodStringParser;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.notifier.EndProcessNotifier;
import fr.inria.spirals.repairnator.notifier.engines.NotifierEngine;
import fr.inria.spirals.repairnator.serializer.HardwareInfoSerializer;
import fr.inria.spirals.repairnator.serializer.engines.SerializerEngine;
import fr.inria.spirals.repairnator.states.LauncherMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RTLauncher {
    private static Logger LOGGER = LoggerFactory.getLogger(RTLauncher.class);
    private final LauncherMode launcherMode;
    private JSAP jsap;
    private JSAPResult arguments;
    private List<SerializerEngine> engines;
    private RepairnatorConfig config;
    private EndProcessNotifier endProcessNotifier;

    private RTLauncher(String[] args) throws JSAPException {
        this.defineArgs();
        this.arguments = jsap.parse(args);
        LauncherUtils.checkArguments(this.jsap, this.arguments, LauncherType.REALTIME);
        LauncherUtils.checkEnvironmentVariables(this.jsap, LauncherType.REALTIME);
        this.launcherMode = LauncherMode.REPAIR;

        this.initConfig();
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
        // -o or --output
        this.jsap.registerParameter(LauncherUtils.defineArgOutput(LauncherType.REALTIME, "Specify where to put serialized files from dockerpool"));
        // --dbhost
        this.jsap.registerParameter(LauncherUtils.defineArgMongoDBHost());
        // --dbname
        this.jsap.registerParameter(LauncherUtils.defineArgMongoDBName());
        // --notifyEndProcess
        this.jsap.registerParameter(LauncherUtils.defineArgNotifyEndProcess());
        // --smtpServer
        this.jsap.registerParameter(LauncherUtils.defineArgSmtpServer());
        // --notifyto
        this.jsap.registerParameter(LauncherUtils.defineArgNotifyto());
        // -n or --name
        this.jsap.registerParameter(LauncherUtils.defineArgDockerImageName());
        // --skipDelete
        this.jsap.registerParameter(LauncherUtils.defineArgSkipDelete());
        // --createOutputDir
        this.jsap.registerParameter(LauncherUtils.defineArgCreateOutputDir());
        // -l or --logDirectory
        this.jsap.registerParameter(LauncherUtils.defineArgLogDirectory());
        // -t or --threads
        this.jsap.registerParameter(LauncherUtils.defineArgNbThreads());
        // --pushurl
        this.jsap.registerParameter(LauncherUtils.defineArgPushUrl());

        FlaggedOption opt2 = new FlaggedOption("whitelist");
        opt2.setShortFlag('w');
        opt2.setLongFlag("whitelist");
        opt2.setStringParser(FileStringParser.getParser().setMustBeDirectory(false).setMustExist(true));
        opt2.setHelp("Specify the path of whitelisted repository");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("blacklist");
        opt2.setShortFlag('b');
        opt2.setLongFlag("blacklist");
        opt2.setStringParser(FileStringParser.getParser().setMustBeDirectory(false).setMustExist(true));
        opt2.setHelp("Specify the path of blacklisted repository");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("jobsleeptime");
        opt2.setLongFlag("jobsleeptime");
        opt2.setStringParser(JSAP.INTEGER_PARSER);
        opt2.setDefault(InspectJobs.JOB_SLEEP_TIME+"");
        opt2.setHelp("Specify the sleep time between two requests to Travis Job endpoint (in seconds)");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("buildsleeptime");
        opt2.setLongFlag("buildsleeptime");
        opt2.setStringParser(JSAP.INTEGER_PARSER);
        opt2.setDefault(InspectBuilds.BUILD_SLEEP_TIME+"");
        opt2.setHelp("Specify the sleep time between two refresh of build statuses (in seconds)");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("maxinspectedbuilds");
        opt2.setLongFlag("maxinspectedbuilds");
        opt2.setStringParser(JSAP.INTEGER_PARSER);
        opt2.setDefault(InspectBuilds.LIMIT_SUBMITTED_BUILDS+"");
        opt2.setHelp("Specify the maximum number of watched builds");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("duration");
        opt2.setLongFlag("duration");
        opt2.setStringParser(PeriodStringParser.getParser());
        opt2.setHelp("Duration of the execution. If not given, the execution never stop. This argument should be given on the ISO-8601 duration format: PWdTXhYmZs where W, X, Y, Z respectively represents number of Days, Hours, Minutes and Seconds. T is mandatory before the number of hours and P is always mandatory.");
        this.jsap.registerParameter(opt2);
    }

    private void initConfig() {
        this.config = RepairnatorConfig.getInstance();

        this.config.setRunId(LauncherUtils.getArgRunId(this.arguments));
        this.config.setLauncherMode(this.launcherMode);
        this.config.setOutputPath(LauncherUtils.getArgOutput(this.arguments).getPath());
        this.config.setMongodbHost(LauncherUtils.getArgMongoDBHost(this.arguments));
        this.config.setMongodbName(LauncherUtils.getArgMongoDBName(this.arguments));
        this.config.setNotifyEndProcess(LauncherUtils.getArgNotifyEndProcess(this.arguments));
        this.config.setSmtpServer(LauncherUtils.getArgSmtpServer(this.arguments));
        this.config.setNotifyTo(LauncherUtils.getArgNotifyto(this.arguments));
        this.config.setDockerImageName(LauncherUtils.getArgDockerImageName(this.arguments));
        this.config.setSkipDelete(LauncherUtils.getArgSkipDelete(this.arguments));
        this.config.setCreateOutputDir(LauncherUtils.getArgCreateOutputDir(this.arguments));
        this.config.setLogDirectory(LauncherUtils.getArgLogDirectory(this.arguments));
        this.config.setNbThreads(LauncherUtils.getArgNbThreads(this.arguments));
        if (LauncherUtils.getArgPushUrl(this.arguments) != null) {
            this.config.setPush(true);
            this.config.setPushRemoteRepo(LauncherUtils.getArgPushUrl(this.arguments));
        }
        if (this.arguments.contains("whitelist")) {
            this.config.setWhiteList(this.arguments.getFile("whitelist"));
        }
        if (this.arguments.contains("blacklist")) {
            this.config.setBlackList(this.arguments.getFile("blacklist"));
        }
        this.config.setJobSleepTime(this.arguments.getInt("jobsleeptime"));
        this.config.setBuildSleepTime(this.arguments.getInt("buildsleeptime"));
        this.config.setMaxInspectedBuilds(this.arguments.getInt("maxinspectedbuilds"));
        if (this.arguments.getObject("duration") != null) {
            this.config.setDuration((Duration) this.arguments.getObject("duration"));
        }
    }

    private void initSerializerEngines() {
        this.engines = new ArrayList<>();

        List<SerializerEngine> fileSerializerEngines = LauncherUtils.initFileSerializerEngines(this.arguments, LOGGER);
        this.engines.addAll(fileSerializerEngines);

        SerializerEngine mongoDBSerializerEngine = LauncherUtils.initMongoDBSerializerEngine(this.arguments, LOGGER);
        if (mongoDBSerializerEngine != null) {
            this.engines.add(mongoDBSerializerEngine);
        }
    }

    private void initNotifiers() {
        if (this.config.isNotifyEndProcess()) {
            List<NotifierEngine> notifierEngines = LauncherUtils.initNotifierEngines(this.arguments, LOGGER);
            this.endProcessNotifier = new EndProcessNotifier(notifierEngines, LauncherType.REALTIME.name().toLowerCase()+" (runid: "+this.config.getRunId()+")");
        }
    }

    private void initAndRunRTScanner() {
        LOGGER.info("Init RTScanner...");
        String runId = this.config.getRunId();
        HardwareInfoSerializer hardwareInfoSerializer = new HardwareInfoSerializer(this.engines, runId, "rtScanner");
        hardwareInfoSerializer.serialize();
        RTScanner rtScanner = new RTScanner(runId, this.engines);
        rtScanner.getInspectBuilds().setMaxSubmittedBuilds(this.config.getMaxInspectedBuilds());
        rtScanner.getInspectBuilds().setSleepTime(this.config.getBuildSleepTime());
        rtScanner.getInspectJobs().setSleepTime(this.config.getJobSleepTime());

        if (this.config.getDuration() != null) {
            rtScanner.setDuration(this.config.getDuration());

            if (this.endProcessNotifier != null) {
                rtScanner.setEndProcessNotifier(this.endProcessNotifier);
            }
        }

        LOGGER.info("Init build runner");
        BuildRunner buildRunner = rtScanner.getBuildRunner();
        buildRunner.setDockerOutputDir(this.config.getLogDirectory());
        buildRunner.setRunId(runId);
        buildRunner.setCreateOutputDir(this.config.isCreateOutputDir());
        buildRunner.setSkipDelete(this.config.isSkipDelete());
        buildRunner.setEngines(this.engines);
        buildRunner.setDockerImageName(this.config.getDockerImageName());
        buildRunner.initExecutorService(this.config.getNbThreads());


        if (this.config.getWhiteList() != null) {
            rtScanner.initWhiteListedRepository(this.config.getWhiteList());
        }

        if (this.config.getBlackList() != null) {
            rtScanner.initBlackListedRepository(this.config.getBlackList());
        }

        LOGGER.info("Start RTScanner...");
        rtScanner.launch();
    }

    public static void main(String[] args) throws JSAPException {
        RTLauncher rtLauncher = new RTLauncher(args);
        rtLauncher.initAndRunRTScanner();
    }

}
