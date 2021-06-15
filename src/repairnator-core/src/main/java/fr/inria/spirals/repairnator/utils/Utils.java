package fr.inria.spirals.repairnator.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Created by urli on 02/02/2017.
 */
public class Utils {

    public static final String GITHUB_OAUTH = "GITHUB_OAUTH";
    public static final String M2_HOME = "M2_HOME";

    private static final String TRAVIS_URL = "http://travis-ci.org/";
    public static final String TRAVIS_FILE = ".travis.yml";

    public static final String GITHUB_URL = "https://github.com/";
    public static final String REMOTE_REPO_EXT = ".git";
    public static final String GITHUB_USER_NAME_PATTERN = "[a-zA-Z0-9](?:[a-zA-Z0-9]|[-](?=[a-zA-Z0-9]))*";
    public static final String GITHUB_REPO_NAME_PATTERN = "[a-zA-Z0-9-_.]+(?<!\\.git)";
    private static final String GITHUB_REPO_URL_PATTERN = GITHUB_URL + GITHUB_USER_NAME_PATTERN + "/" + GITHUB_REPO_NAME_PATTERN;

    public static final String POM_FILE = "pom.xml";

    public static final char COMMA = ',';

    public static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();

        } catch (UnknownHostException e) {
            return "unknown host";
        }
    }

    public static String getTravisUrl(long buildId, String slug) {
        return TRAVIS_URL + slug + "/builds/" + String.valueOf(buildId);
    }

    public static String getSimpleGithubRepoUrl(String slug) {
        return GITHUB_URL + slug;
    }

    public static String getCompleteGithubRepoUrl(String slug) {
        return GITHUB_URL + slug + REMOTE_REPO_EXT;
    }

    public static String getCommitUrl(String commitId, String repoSlug) {
        return "http://github.com/"+repoSlug+"/commit/"+commitId;
    }

    public static String getBranchUrl(String branchName, String repoSlug) {
        return "http://github.com/"+repoSlug+"/tree/"+branchName;
    }

    public static boolean matchesGithubRepoUrl(String repoUrl) {
        return repoUrl.matches(GITHUB_REPO_URL_PATTERN);
    }

    public static void setLoggersLevel(Level level) {
        Logger allinria = (Logger) LoggerFactory.getLogger("fr.inria");
        allinria.setLevel(level);

        Logger jgit = (Logger) LoggerFactory.getLogger("org.eclipse.jgit");
        jgit.setLevel(Level.WARN);
    }

    public static Level getLoggersLevel() {
        Logger allinria = (Logger) LoggerFactory.getLogger("fr.inria");
        return allinria.getLevel();
    }

    public static String getValue(List<Object> value, int index) {
        if (index < value.size()) {
            return value.get(index).toString();
        } else {
            return null;
        }
    }

    public static void checkToolsJar() throws ClassNotFoundException {
        // Check if tools.jar is in classpath, as Nopol depends on it.
        // This is not done in compile time as we don't want Repairnator to it to compile on OpenJDK or JDK >= 9
        Class.forName("com.sun.jdi.AbsentInformationException");
    }

    public static String getEnvOrDefault(String name, String dfault){
        String env = System.getenv(name);
        if(env == null || env.equals(""))
            return dfault;
        return env;
    }

}
