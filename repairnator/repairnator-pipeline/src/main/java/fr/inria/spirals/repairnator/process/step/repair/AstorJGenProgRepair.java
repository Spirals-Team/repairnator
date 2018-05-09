package fr.inria.spirals.repairnator.process.step.repair;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import fr.inria.astor.core.entities.ProgramVariant;
import fr.inria.astor.core.setup.ConfigurationProperties;
import fr.inria.main.AstorOutputStatus;
import fr.inria.main.evolution.AstorMain;
import fr.inria.spirals.repairnator.process.inspectors.JobStatus;
import fr.inria.spirals.repairnator.process.inspectors.StepStatus;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import spoon.SpoonException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Created by urli on 17/08/2017.
 */
public class AstorJGenProgRepair extends AbstractRepairStep {
    protected static final String TOOL_NAME = "AstorJGenProg";
    private static final int MAX_TIME_EXECUTION = 100; // in minutes

    public AstorJGenProgRepair() {}

    @Override
    public String getRepairToolName() {
        return TOOL_NAME;
    }

    @Override
    protected StepStatus businessExecute() {
        this.getLogger().info("Start to repair using Astor");

        JobStatus jobStatus = this.getInspector().getJobStatus();
        List<String> astorPatches = new ArrayList<>();

        List<URL> classPath = this.getInspector().getJobStatus().getRepairClassPath();
        File[] sources = this.getInspector().getJobStatus().getRepairSourceDir();

        if (classPath != null && sources != null) {
            List<String> dependencies = new ArrayList<>();
            for (URL url : jobStatus.getRepairClassPath()) {
                if (url.getFile().endsWith(".jar")) {
                    dependencies.add(url.getPath());
                }
            }

            final List<String> astorArgs = new ArrayList<>();
            astorArgs.add("-dependencies");
            astorArgs.add(StringUtils.join(dependencies,":"));

            astorArgs.add("-mode");
            astorArgs.add("jgenprog");

            astorArgs.add("-location");
            astorArgs.add(jobStatus.getFailingModulePath());

            String relativeSourcePath = new File(jobStatus.getFailingModulePath()).toURI().relativize(jobStatus.getRepairSourceDir()[0].toURI()).getPath();
            astorArgs.add("-srcjavafolder");
            astorArgs.add(relativeSourcePath);

            astorArgs.add("-stopfirst");
            astorArgs.add("true");

            astorArgs.add("-population");
            astorArgs.add("1");

            //astorArgs.add("-loglevel");
            //astorArgs.add("DEBUG");

            astorArgs.add("-parameters");
            astorArgs.add("timezone:Europe/Paris:maxnumbersolutions:3:limitbysuspicious:false:maxmodificationpoints:1000:javacompliancelevel:8:logfilepath:"+this.getInspector().getRepoLocalPath()+"/repairnator.astor.log");

            astorArgs.add("-maxtime");
            astorArgs.add(MAX_TIME_EXECUTION+"");

            astorArgs.add("-seed");
            astorArgs.add("1");

            final AstorMain astorMain = new AstorMain();


            final ExecutorService executor = Executors.newSingleThreadExecutor();
            final Future<AstorOutputStatus> astorExecution = executor.submit(new Callable<AstorOutputStatus>() {
                @Override
                public AstorOutputStatus call() throws Exception {
                    AstorOutputStatus status = null;
                    try {
                        astorMain.execute(astorArgs.toArray(new String[0]));
                        if (astorMain.getEngine() != null) {
                            status = astorMain.getEngine().getOutputStatus();
                        } else {
                            status = AstorOutputStatus.ERROR;
                        }
                    } catch (SpoonException e) {
                        status = AstorOutputStatus.ERROR;
                        addStepError("Got SpoonException while running Astor", e);
                    } catch (RuntimeException e) {
                        addStepError("Got runtime exception while running Astor", e);
                        status = AstorOutputStatus.ERROR;
                    }
                    return status;
                }
            });

            AstorOutputStatus status = null;
            try {
                executor.shutdown();
                status = astorExecution.get(MAX_TIME_EXECUTION, TimeUnit.MINUTES);

                if (astorMain.getEngine() != null) {
                    List<ProgramVariant> solutions = astorMain.getEngine().getSolutions();

                    if (solutions != null) {
                        for (ProgramVariant pv : solutions) {
                            if (pv.isSolution()) {
                                astorPatches.add(pv.getPatchDiff().getFormattedDiff());
                            }
                        }
                    }
                }

            } catch (Exception e) {
                status = AstorOutputStatus.ERROR;
                this.addStepError("Error while executing astor with args: "+ StringUtils.join(astorArgs,","), e);
            }

            jobStatus.addFileToPush("repairnator.astor.log");

            jobStatus.setAstorPatches(astorPatches);
            jobStatus.setAstorStatus(status);

            String jsonpath;
            try {
                jsonpath = astorMain.getEngine().getProjectFacade().getProperties().getWorkingDirRoot() + File.separator + ConfigurationProperties.getProperty("jsonoutputname") + ".json";
            } catch (NullPointerException e) {
                jsonpath = null;
            }

            if (jsonpath != null) {
                File jsonResultFile = new File(jsonpath);
                if (jsonResultFile.exists()) {

                    try {
                        FileUtils.copyFile(jsonResultFile, new File(this.getInspector().getRepoLocalPath()+"/repairnator.astor.results.json"));
                    } catch (IOException e) {
                        this.addStepError("Error while moving astor JSON results", e);
                    }

                    JsonParser jsonParser = new JsonParser();
                    try {
                        JsonElement root = jsonParser.parse(new FileReader(jsonResultFile));
                        this.getInspector().getJobStatus().setAstorResults(root);
                    } catch (FileNotFoundException e) {
                        this.addStepError("Error while reading astor JSON results", e);
                    }

                    jobStatus.addFileToPush("repairnator.astor.results.json");
                }
            }


            if (astorPatches.isEmpty()) {
                return StepStatus.buildSkipped(this,"No patch found.");
            } else {
                this.getInspector().getJobStatus().setHasBeenPatched(true);
                return StepStatus.buildSuccess(this);
            }
        }
        return StepStatus.buildSkipped(this,"Classpath or sources not computed.");
    }
}
