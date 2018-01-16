package fr.inria.spirals.repairnator.realtime;

import fr.inria.spirals.jtravis.entities.Build;
import fr.inria.spirals.jtravis.entities.BuildTool;
import fr.inria.spirals.jtravis.entities.Job;
import fr.inria.spirals.jtravis.entities.Log;
import fr.inria.spirals.jtravis.entities.Repository;
import fr.inria.spirals.jtravis.helpers.BuildHelper;
import fr.inria.spirals.jtravis.helpers.RepositoryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class RTScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RTScanner.class);
    private final List<Integer> blackListedRepository;
    private final List<Integer> whiteListedRepository;
    private final InspectBuilds inspectBuilds;
    private final InspectJobs inspectJobs;
    private final BuildRunner buildRunner;
    private FileWriter blacklistWriter;
    private FileWriter whitelistWriter;
    private boolean running;

    public RTScanner() {
        this.blackListedRepository = new ArrayList<>();
        this.whiteListedRepository = new ArrayList<>();
        this.buildRunner = new BuildRunner(this);
        this.inspectBuilds = new InspectBuilds(this);
        this.inspectJobs = new InspectJobs(this);
    }

    public BuildRunner getBuildRunner() {
        return buildRunner;
    }

    public InspectBuilds getInspectBuilds() {
        return inspectBuilds;
    }

    public InspectJobs getInspectJobs() {
        return inspectJobs;
    }

    public void initWhiteListedRepository(File whiteListFile) {
        LOGGER.info("Init whitelist repository...");
        try {
            List<String> lines = Files.readAllLines(whiteListFile.toPath());
            for (String repoName : lines) {
                Repository repository = RepositoryHelper.getRepositoryFromSlug(repoName);
                if (repository != null) {
                    this.whiteListedRepository.add(repository.getId());
                }
            }

            this.whitelistWriter = new FileWriter(whiteListFile, true);
        } catch (IOException e) {
            LOGGER.error("Error while initializing whitelist", e);
        }
        LOGGER.info("Whitelist initialized with: "+this.whiteListedRepository.size()+" entries");
    }

    public void initBlackListedRepository(File blackListFile) {
        LOGGER.info("Init blacklist repository...");
        try {
            List<String> lines = Files.readAllLines(blackListFile.toPath());
            for (String repoName : lines) {
                Repository repository = RepositoryHelper.getRepositoryFromSlug(repoName);
                if (repository != null) {
                    this.blackListedRepository.add(repository.getId());
                }
            }

            this.blacklistWriter = new FileWriter(blackListFile, true);
        } catch (IOException e) {
            LOGGER.error("Error while initializing blacklist", e);
        }
        LOGGER.info("Blacklist initialized with: "+this.blackListedRepository.size()+" entries");
    }

    public void launch() {
        if (!this.running) {
            LOGGER.info("Start running RTScanner...");
            new Thread(this.inspectBuilds).start();
            new Thread(this.inspectJobs).start();
            this.running = true;
        }
    }

    private void addInBlacklistRepository(Repository repository) {
        this.blackListedRepository.add(repository.getId());
        try {
            this.blacklistWriter.append("\n");
            this.blacklistWriter.append(repository.getSlug());
            this.blacklistWriter.flush();
        } catch (IOException e) {
            LOGGER.error("Error while writing entry in blacklist");
        }
    }

    private void addInWhitelistRepository(Repository repository) {
        this.whiteListedRepository.add(repository.getId());
        try {
            this.whitelistWriter.append("\n");
            this.whitelistWriter.append(repository.getSlug());
            this.whitelistWriter.flush();
        } catch (IOException e) {
            LOGGER.error("Error while writing entry in whitelist");
        }
    }

    public boolean isRepositoryInteresting(int repositoryId) {
        if (this.blackListedRepository.contains(repositoryId)) {
            LOGGER.debug("Repo already blacklisted (id: "+repositoryId+")");
            return false;
        }

        if (this.whiteListedRepository.contains(repositoryId)) {
            LOGGER.debug("Repo already whitelisted (id: "+repositoryId+")");
            return true;
        }

        Repository repository = RepositoryHelper.getRepositoryFromId(repositoryId);
        Build masterBuild = BuildHelper.getLastSuccessfulBuildFromMaster(repository, false);

        if (masterBuild == null) {
            LOGGER.info("No successful build found in "+repository.getSlug()+" (id: "+repositoryId+"). It will blacklisted for further call.");
            this.addInBlacklistRepository(repository);
            return false;
        } else {
            if (masterBuild.getConfig().getLanguage() == null || !masterBuild.getConfig().getLanguage().equals("java")) {
                LOGGER.info("Repository "+repository.getSlug()+" (id: "+repositoryId+") is not using java ("+masterBuild.getConfig().getLanguage()+"). It will blacklisted for further call.");
                this.addInBlacklistRepository(repository);
                return false;
            }

            if (masterBuild.getBuildTool() == BuildTool.GRADLE) {
                LOGGER.info("Repository "+repository.getSlug()+" (id: "+repositoryId+") is using gradle. It will blacklisted for further call.");
                this.addInBlacklistRepository(repository);
                return false;
            } else if (masterBuild.getBuildTool() == BuildTool.UNKNOWN) {
                LOGGER.info("Repository "+repository.getSlug()+" (id: "+repositoryId+") build tool is not known. It will be blacklisted for further call.");
                this.addInBlacklistRepository(repository);
                return false;
            }

            if (!masterBuild.getJobs().isEmpty()) {
                Job firstJob = masterBuild.getJobs().get(0);
                Log jobLog = firstJob.getLog();
                if (jobLog.getTestsInformation() != null && jobLog.getTestsInformation().getRunning() > 0) {
                    LOGGER.info("Tests has been found in repository "+repository.getSlug()+" (id: "+repositoryId+") build (id: "+masterBuild.getId()+"). The repo is now whitelisted.");
                    this.addInWhitelistRepository(repository);
                    return true;
                } else {
                    LOGGER.info("No test found in repository "+repository.getSlug()+" (id: "+repositoryId+") build (id: "+masterBuild.getId()+"). It is not considered right now.");
                }
            } else {
                LOGGER.info("No job found in repository "+repository.getSlug()+" (id: "+repositoryId+") build (id: "+masterBuild.getId()+"). It is not considered right now.");
            }
        }

        return false;
    }

    public void submitBuildToExecution(Build build) {
        boolean failing = false;
        for (Job job : build.getJobs()) {
            Log jobLog = job.getLog();
            if (jobLog.getTestsInformation() != null && jobLog.getTestsInformation().getErrored() >= 0 || jobLog.getTestsInformation().getFailing() >= 0) {
                failing = true;
                break;
            }
        }
        if (failing) {
            LOGGER.info("Failing or erroring tests has been found in build (id: "+build.getId()+")");
            this.buildRunner.submitBuild(build);
        } else {
            LOGGER.info("No failing or erroring test has been found in build (id: "+build.getId()+")");
        }
    }

    public void submitWaitingBuild(int buildId) {
        Build build = BuildHelper.getBuildFromId(buildId, null);
        this.inspectBuilds.submitNewBuild(build);
    }
}
