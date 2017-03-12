package fr.inria.spirals.repairnator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.martiansoftware.jsap.*;
import com.martiansoftware.jsap.stringparsers.EnumeratedStringParser;
import com.martiansoftware.jsap.stringparsers.FileStringParser;
import fr.inria.spirals.repairnator.scanner.ProjectScanner;
import fr.inria.spirals.repairnator.serializer.gsheet.process.GoogleSpreadSheetScannerSerializer;
import fr.inria.spirals.repairnator.serializer.gsheet.process.GoogleSpreadSheetScannerSerializer4Bears;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Created by urli on 23/12/2016.
 */
public class Launcher {
    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(fr.inria.spirals.repairnator.Launcher.class);

    private JSAP jsap;
    private JSAPResult arguments;

    public Launcher(String[] args) throws JSAPException {
        this.defineArgs();
        this.arguments = jsap.parse(args);
        this.checkArguments();

        if (this.arguments.getBoolean("debug")) {
            Utils.setLoggersLevel(Level.DEBUG);
        } else {
            Utils.setLoggersLevel(Level.INFO);
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

    private void printUsage() {
        System.err.println("Usage: java <repairnator-scanner> [option(s)]");
        System.err.println();
        System.err.println("Options : ");
        System.err.println();
        System.err.println(jsap.getHelp());
        System.exit(-1);
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

        // Tab size
        FlaggedOption opt2 = new FlaggedOption("input");
        opt2.setShortFlag('i');
        opt2.setLongFlag("input");
        opt2.setStringParser(FileStringParser.getParser().setMustExist(true).setMustBeFile(true));
        opt2.setRequired(true);
        opt2.setHelp("Specify where to find the list of projects to scan.");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("output");
        opt2.setShortFlag('o');
        opt2.setLongFlag("output");
        opt2.setStringParser(FileStringParser.getParser());
        opt2.setHelp("Specify where to write the list of build ids (default: stdout)");
        this.jsap.registerParameter(opt2);

        String launcherModeValues = "";
        for (LauncherMode mode : LauncherMode.values()) {
            launcherModeValues += mode.name() + ";";
        }
        launcherModeValues = launcherModeValues.substring(0, launcherModeValues.length() - 2);

        // Launcher mode
        opt2 = new FlaggedOption("launcherMode");
        opt2.setShortFlag('m');
        opt2.setLongFlag("launcherMode");
        opt2.setStringParser(EnumeratedStringParser.getParser(launcherModeValues));
        opt2.setRequired(true);
        opt2.setHelp("Specify if the scanner intends to get failing build (REPAIR) or fixer builds (BEARS).");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("lookupHours");
        opt2.setShortFlag('l');
        opt2.setLongFlag("lookupHours");
        opt2.setStringParser(JSAP.INTEGER_PARSER);
        opt2.setDefault("4");
        opt2.setHelp("Specify the hour number to lookup to get builds");
        this.jsap.registerParameter(opt2);

        opt2 = new FlaggedOption("googleSecretPath");
        opt2.setShortFlag('g');
        opt2.setLongFlag("googleSecretPath");
        opt2.setStringParser(FileStringParser.getParser().setMustBeFile(true).setMustExist(true));
        opt2.setDefault("./client_secret.json");
        opt2.setHelp("Specify the path to the JSON google secret for serializing.");
        this.jsap.registerParameter(opt2);
    }

    private List<BuildToBeInspected> runScanner() throws IOException {
        Launcher.LOGGER.info("Start to scan projects in travis...");

        LauncherMode launcherMode = LauncherMode.valueOf(this.arguments.getString("launcherMode").toUpperCase());
        String googleSecretPath = this.arguments.getFile("googleSecretPath").getPath();

        ProjectScanner scanner = new ProjectScanner(this.arguments.getInt("lookupHours"), launcherMode);
        List<BuildToBeInspected> buildsToBeInspected = scanner.getListOfBuildsToBeInspected(this.arguments.getFile("input").getPath());
            ProcessSerializer scannerSerializer;

        if (launcherMode == LauncherMode.REPAIR) {
            scannerSerializer = new GoogleSpreadSheetScannerSerializer(scanner, googleSecretPath);
        } else {
            scannerSerializer = new GoogleSpreadSheetScannerSerializer4Bears(scanner, googleSecretPath);
        }

        scannerSerializer.serialize();

        if (buildsToBeInspected.isEmpty()) {
            Launcher.LOGGER.info("No build has been found ("+scanner.getTotalScannedBuilds()+" scanned builds.)");
        }
        return buildsToBeInspected;
    }

    private void mainProcess() throws IOException {

        List<BuildToBeInspected> buildsToBeInspected = this.runScanner();

        if (buildsToBeInspected != null) {
            for (BuildToBeInspected buildToBeInspected : buildsToBeInspected) {
                Launcher.LOGGER.info("Incriminated project : " + buildToBeInspected.getBuild().getRepository().getSlug() + ":" + buildToBeInspected.getBuild().getId());
            }
            
            this.processOutput(buildsToBeInspected);
        } else {
            Launcher.LOGGER.warn("Builds inspected has null value.");
            System.exit(-1);
        }
    }

    private void processOutput(List<BuildToBeInspected> listOfBuilds) {

        String outputPath = this.arguments.getString("output");
        if (outputPath != null) {
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath));

                for (BuildToBeInspected buildToBeInspected : listOfBuilds) {
                    writer.write(buildToBeInspected.getBuild().getId()+"");
                    writer.newLine();
                    writer.flush();
                }

                writer.close();
                return;
            } catch (IOException e) {
                LOGGER.error("Error while writing file "+outputPath+". The content will be printed in the standard output.",e);
            }
        }

        for (BuildToBeInspected buildToBeInspected : listOfBuilds) {
            System.out.println(buildToBeInspected.getBuild().getId());
        }
    }

    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher(args);
        launcher.mainProcess();
    }

}
