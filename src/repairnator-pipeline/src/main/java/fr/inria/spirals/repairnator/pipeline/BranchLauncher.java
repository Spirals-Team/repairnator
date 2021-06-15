package fr.inria.spirals.repairnator.pipeline;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.stringparsers.EnumeratedStringParser;
import fr.inria.spirals.repairnator.LauncherUtils;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.notifier.PatchNotifier;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.step.repair.NPERepair;
import fr.inria.spirals.repairnator.states.LauncherMode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BranchLauncher implements LauncherAPI{
	private static Logger LOGGER = LoggerFactory.getLogger(BranchLauncher.class);
	private String[] args;
	private static MainProcess mainProcess;

	public BranchLauncher(String[] args) throws JSAPException {
		this.args = args;
		JSAP jsap = new JSAP();
		LauncherUtils.registerCommonArgs(jsap);

		JSAPResult result = jsap.parse(args);
		LauncherUtils.initCommonConfig(getConfig(), result);

		mainProcess = getMainProcess(args);
	}


	/* This should be only those args neccessary to construct the correct launcher */
	public static JSAP defineBasicArgs() throws JSAPException {
		JSAP jsap = new JSAP();

		FlaggedOption opt2 = new FlaggedOption("launcherMode");
        opt2.setShortFlag('l');
        opt2.setLongFlag("launcherMode");
        opt2.setStringParser(JSAP.STRING_PARSER);
        opt2.setDefault(LauncherMode.REPAIR.name());
        opt2.setHelp("specify launcherMode." 
        	+ "REPAIR: standard repairnator repair with Travis build ids. BEARS: analyze pairs of bugs and human-produced patches. "
        	+ "CHECKSTYLE: analyze build failing because of checkstyle. "
        	+ "GIT_REPOSITORY: repairnator repair with Git instead of standard Travis. "
			+ "KUBERNETES_LISTENER: run repairnator as a Activemq server listening for Travis build ids."
			+ "SEQUENCER_REPAIR: run the custom SequencerRepair pipeline.");
        jsap.registerParameter(opt2);



        return jsap;
	}

	public static MainProcess getMainProcess(String[] args) throws JSAPException{
		JSAP jsap = defineBasicArgs();
		JSAPResult jsapResult = jsap.parse(args);

		String launcherMode = jsapResult.getString("launcherMode");


		if (launcherMode.equals(LauncherMode.REPAIR.name())) {
			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.REPAIR);
			return MainProcessFactory.getTravisMainProcess(args);
		} else if (launcherMode.equals(LauncherMode.BEARS.name())) {
			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.BEARS);
			return MainProcessFactory.getTravisMainProcess(args);
		} else if (launcherMode.equals(LauncherMode.CHECKSTYLE.name())) {
			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.CHECKSTYLE);
			return MainProcessFactory.getTravisMainProcess(args);
		} else if (launcherMode.equals(LauncherMode.GIT_REPOSITORY.name())) {
			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.GIT_REPOSITORY);
			return MainProcessFactory.getGithubMainProcess(args);
		} else if (launcherMode.equals(LauncherMode.SEQUENCER_REPAIR.name())) {
			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.SEQUENCER_REPAIR);
			return MainProcessFactory.getGithubMainProcess(args);
		} else if (launcherMode.equals(LauncherMode.KUBERNETES_LISTENER.name())) {
			RepairnatorConfig.getInstance().setLauncherMode(LauncherMode.KUBERNETES_LISTENER);
			return MainProcessFactory.getPipelineListenerMainProcess(args);
		} else {
			LOGGER.warn("Unknown launcher mode. Please choose the following: REPAIR, BEARS, CHECKSTYLE, GIT_REPOSITORY, KUBERNETES_LISTENER, JENKINS_PLUGIN");
			return null;
		}
	}


	public static void main(String[] args) throws JSAPException {
		mainProcess.run();
	}

	@Override
	public void launch() {
		try {
			main(this.args);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public RepairnatorConfig getConfig() {
        return RepairnatorConfig.getInstance();
    }

    @Override
    public JSAP defineArgs() throws JSAPException {
		// Verbose output
		JSAP jsap = new JSAP();

		// Register arguments common to all Launchers
		LauncherUtils.registerCommonArgs(jsap);

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

    @Override
    public boolean mainProcess() {
		try {
			main(args);
		} catch (Exception e){
			throw new RuntimeException(e);
		}
        return true;
    }

    @Override
    public ProjectInspector getInspector() {
		return mainProcess.getInspector();
    }

    @Override
    public void setPatchNotifier(PatchNotifier patchNotifier) {
		mainProcess.setPatchNotifier(patchNotifier);
    }
}