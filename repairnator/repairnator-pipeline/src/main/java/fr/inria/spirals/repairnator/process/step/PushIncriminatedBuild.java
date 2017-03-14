package fr.inria.spirals.repairnator.process.step;

import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.config.RepairnatorConfigException;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;

/**
 * Created by urli on 05/01/2017.
 */
public class PushIncriminatedBuild extends AbstractStep {
    private static final String REMOTE_REPO_EXT = ".git";
    private static final String REMOTE_REPO_TREE_EXT = "/tree/";
    private static final String NB_COMMITS_TO_KEEP = "10";

    private String branchName;
    private String remoteRepoUrl;
    private RepairnatorConfig config;

    public PushIncriminatedBuild(ProjectInspector inspector) {
        super(inspector);
        try {
            this.config = RepairnatorConfig.getInstance();
        } catch (RepairnatorConfigException e) {
            throw new RuntimeException(e);
        }

        this.remoteRepoUrl = config.getPushRemoteRepo();

        SimpleDateFormat dateFormat = new SimpleDateFormat("YYYYMMdd-HHmmss");
        String formattedDate = dateFormat.format(this.inspector.getBuild().getFinishedAt());

        this.branchName = inspector.getRepoSlug().replace('/', '-') + '-' + inspector.getBuild().getId() + '-'
                + formattedDate;
    }

    public void setRemoteRepoUrl(String remoteRepoUrl) {
        this.remoteRepoUrl = remoteRepoUrl;
    }

    public String getRemoteLocation() {
        return this.remoteRepoUrl + REMOTE_REPO_TREE_EXT + this.branchName;
    }

    @Override
    protected void businessExecute() {

        if (this.config.isPush()) {
            if (this.remoteRepoUrl == null || this.remoteRepoUrl.equals("")) {
                this.getLogger().error("Remote repo should be set !");
                return;
            }

            this.writeProperty("step-durations", StringUtils.join(this.inspector.getStepsDurationsInSeconds().entrySet()));

            String remoteRepo = this.remoteRepoUrl + REMOTE_REPO_EXT;
            this.getLogger().debug(
                    "Start to push failing state in the remote repository: " + remoteRepo + " branch: " + branchName);
            if (System.getenv("GITHUB_OAUTH") == null || System.getenv("GITHUB_OAUTH").equals("")) {
                this.getLogger().warn("You must the GITHUB_OAUTH env property to push incriminated build.");
                return;
            }

            try {
                Git git = Git.open(new File(inspector.getRepoLocalPath()));

                git.checkout().setCreateBranch(true).setName("detached").call();
                ObjectId id = git.getRepository().resolve("HEAD~" + NB_COMMITS_TO_KEEP);

                if (id != null) {
                    this.getLogger().debug("Get only the last " + NB_COMMITS_TO_KEEP + " commits before push.");
                    ProcessBuilder processBuilder = new ProcessBuilder("git", "rebase-last-x", NB_COMMITS_TO_KEEP)
                            .directory(new File(this.inspector.getRepoLocalPath())).inheritIO();

                    Process p = processBuilder.start();
                    p.waitFor();
                } else {
                    this.getLogger().debug("The repository contains less than " + NB_COMMITS_TO_KEEP + ": push all the repo.");
                }

                this.getLogger().debug("Commit the logs and properties files");
                AddCommand addCommand = git.add();
                for (File file : new File(this.inspector.getRepoLocalPath()).listFiles()) {
                    if (file.getName().contains("repairnator")) {
                        addCommand.addFilepattern(file.getName());
                    }
                }

                addCommand.call();
                PersonIdent personIdent = new PersonIdent("Luc Esape", "luc.esape@gmail.com");
                git.commit().setMessage("repairnator: add log and properties").setCommitter(personIdent)
                        .setAuthor(personIdent).call();

                this.getLogger().debug("Add the remote repository to push the current state");

                RemoteAddCommand remoteAdd = git.remoteAdd();
                remoteAdd.setName("saveFail");
                remoteAdd.setUri(new URIish(remoteRepo));
                remoteAdd.call();

                CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(System.getenv("GITHUB_OAUTH"), "");

                this.getLogger().debug("Check if a branch already exists in the remote repository");
                ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh","-c","git remote show saveFail | grep "+branchName)
                        .directory(new File(this.inspector.getRepoLocalPath()));

                Process p = processBuilder.start();
                BufferedReader stdin = new BufferedReader(new InputStreamReader(p.getInputStream()));
                p.waitFor();

                this.getLogger().debug("Get result from grep process...");
                String processReturn = "";
                String line;
                while (stdin.ready() && (line = stdin.readLine()) != null) {
                    processReturn += line;
                }

                if (!processReturn .equals("")) {
                    this.getLogger().warn("A branch already exist in the remote repo with the following name: " + branchName);
                    this.getLogger().debug("Here the grep return: "+processReturn);
                    return;
                }

                this.getLogger().debug("Prepare the branch and push");
                Ref branch = git.branchCreate().setName(branchName).call();

                git.push().setRemote("saveFail").add(branch).setCredentialsProvider(credentialsProvider).call();

            } catch (IOException e) {
                this.getLogger().error("Error while reading git directory at the following location: "
                        + inspector.getRepoLocalPath() + " : " + e);
                this.addStepError(e.getMessage());
            } catch (URISyntaxException e) {
                this.getLogger()
                        .error("Error while setting remote repository with following URL: " + remoteRepo + " : " + e);
                this.addStepError(e.getMessage());
            } catch (GitAPIException e) {
                this.getLogger().error("Error while executing a JGit operation: " + e);
                this.addStepError(e.getMessage());
            } catch (InterruptedException e) {
                this.addStepError("Error while executing git command to gest last 10 commits" + e.getMessage());
            }
        } else {
            this.getLogger().info("The push argument is set to false. Nothing will be pushed.");
        }

    }

}
