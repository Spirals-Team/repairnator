package fr.inria.spirals.jtravis.helpers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.inria.spirals.jtravis.entities.Build;
import fr.inria.spirals.jtravis.entities.BuildStatus;
import fr.inria.spirals.jtravis.entities.Config;
import fr.inria.spirals.jtravis.entities.Commit;
import fr.inria.spirals.jtravis.entities.Job;
import fr.inria.spirals.jtravis.entities.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The helper to deal with Build objects
 *
 * @author Simon Urli
 */
public class BuildHelper extends AbstractHelper {

    public static final String BUILD_NAME = "builds";
    public static final String BUILD_ENDPOINT = BUILD_NAME+"/";

    private static BuildHelper instance;

    private BuildHelper() {
        super();
    }

    private static List<String> getEventTypes() {
        List<String> result = new ArrayList<String>();
        result.addAll(Arrays.asList(new String[]{ "cron", "push", "pull_request"}));
        return result;
    }

    protected static BuildHelper getInstance() {
        if (instance == null) {
            instance = new BuildHelper();
        }
        return instance;
    }

    public static Build getBuildFromId(int id, Repository parentRepo) {
        String resourceUrl = getInstance().getEndpoint()+BUILD_ENDPOINT+id;

        try {
            String response = getInstance().get(resourceUrl);
            JsonParser parser = new JsonParser();
            JsonObject allAnswer = parser.parse(response).getAsJsonObject();

            JsonObject buildJSON = allAnswer.getAsJsonObject("build");
            Build build = createGson().fromJson(buildJSON, Build.class);

            JsonObject commitJSON = allAnswer.getAsJsonObject("commit");
            Commit commit = CommitHelper.getCommitFromJsonElement(commitJSON);
            build.setCommit(commit);

            if (parentRepo != null) {
                build.setRepository(parentRepo);
            }

            JsonObject configJSON = buildJSON.getAsJsonObject("config");
            Config config = ConfigHelper.getConfigFromJsonElement(configJSON);
            build.setConfig(config);

            JsonArray arrayJobs = allAnswer.getAsJsonArray("jobs");

            for (JsonElement jobJSONElement : arrayJobs) {
                Job job = JobHelper.createJobFromJsonElement((JsonObject)jobJSONElement);
                build.addJob(job);
            }



            return build;
        } catch (IOException e) {
            getInstance().getLogger().warn("Error when getting build id "+id+" : "+e.getMessage());
            return null;
        }
    }

    private static boolean isAcceptedBuild(Build build, int prNumber, BuildStatus status, String previousBranch) {
        if (prNumber != -1 && build.getPullRequestNumber() != prNumber) {
            return false;
        }
        if (previousBranch != null && !previousBranch.equals("") && !previousBranch.equals(build.getCommit().getBranch())) {
            return false;
        }
        if (status != null && build.getBuildStatus() != status) {
            return false;
        }
        return true;
    }

    /**
     * This is a recursive method allowing to get build from a slug.
     *
     * @param slug The slug where to get the builds
     * @param result The list result to aggregate
     * @param limitDate If given, the date limit to get builds: all builds *before* this date are considered
     * @param after_number Used for pagination: multiple requests may have to be made to reach the final date
     * @param eventTypes Travis support multiple event types for builds like Push, PR or CRON. Those are retrieved individually
     * @param limitNumber Allow to finish early based on the number of builds selected. if 0 passed use only the date to stop searching
     * @param status Allow to only select builds of that status, if null it takes all status
     * @param prNumber Allow to only consider builds of that PR, if -1 given it takes all builds
     * @param onlyAfterNumber Consider only builds after the specified after_number: should be use in conjunction with a specified after_number.
     */
    private static void getBuildsFromSlugRecursively(String slug, List<Build> result, Date limitDate, int after_number, int original_after_number, List<String> eventTypes, int limitNumber, BuildStatus status, int prNumber, boolean onlyAfterNumber, String previousBranch) {
        Map<Integer, Commit> commits = new HashMap<Integer,Commit>();

        String resourceUrl = getInstance().getEndpoint()+RepositoryHelper.REPO_ENDPOINT+slug+"/"+BUILD_NAME;

        if (after_number <= 0 && onlyAfterNumber) {
            return;
        }

        if (eventTypes.isEmpty()) {
            return;
        }
        String evenType = eventTypes.get(0);
        resourceUrl += "?event_type="+evenType;

        if (after_number > 0) {
            resourceUrl += "&after_number="+after_number;
        }

        boolean dateReached = false;

        try {
            String response = getInstance().get(resourceUrl);
            JsonParser parser = new JsonParser();
            JsonObject allAnswer = parser.parse(response).getAsJsonObject();

            JsonArray commitsArray = allAnswer.getAsJsonArray("commits");

            for (JsonElement commitJson : commitsArray) {
                Commit commit = createGson().fromJson(commitJson, Commit.class);
                commits.put(commit.getId(), commit);
            }

            JsonArray buildArray = allAnswer.getAsJsonArray("builds");

            int lastBuildNumber = Integer.MAX_VALUE;

            for (JsonElement buildJson : buildArray) {
                Build build = createGson().fromJson(buildJson, Build.class);

                if ((limitDate == null) || (build.getFinishedAt() == null) || (build.getFinishedAt().after(limitDate))) {
                    int commitId = build.getCommitId();

                    if (commits.containsKey(commitId)) {
                        build.setCommit(commits.get(commitId));
                    }


                    if (build.getNumber() != null) {
                        int buildNumber = Integer.parseInt(build.getNumber());
                        if (lastBuildNumber > buildNumber) {
                            lastBuildNumber = buildNumber;
                        }
                    }

                    if (isAcceptedBuild(build, prNumber, status, previousBranch)) {
                        result.add(build);
                    }

                    // if we reach the limitNumber we can break the loop, and consider the date is reached
                    if (limitNumber != 0 && result.size() >= limitNumber) {
                        dateReached = true;
                        break;
                    }
                } else {
                    dateReached = true;
                    break;
                }
            }

            if (buildArray.size() == 0) {
                dateReached = true;
            }

            if (limitDate != null && !dateReached) {
                getBuildsFromSlugRecursively(slug, result, limitDate, lastBuildNumber, original_after_number, eventTypes, limitNumber, status, prNumber, onlyAfterNumber, previousBranch);
            } else {
                eventTypes.remove(0);
                if (!onlyAfterNumber) {
                    after_number = 0;
                } else {
                    after_number = original_after_number;
                }
                getBuildsFromSlugRecursively(slug, result, limitDate, after_number, original_after_number, eventTypes, limitNumber, status, prNumber, onlyAfterNumber, previousBranch);
            }
        } catch (IOException e) {
            getInstance().getLogger().warn("Error when getting list of builds from slug "+slug+" : "+e.getMessage());
        }
    }

    public static int getTheLastBuildNumberOfADate(String slug, Date date, int after_number, boolean onlyAfterNumber) {
        Map<Integer, Commit> commits = new HashMap<Integer,Commit>();

        String resourceUrl = getInstance().getEndpoint()+RepositoryHelper.REPO_ENDPOINT+slug+"/"+BUILD_NAME;

        if (after_number <= 0 && onlyAfterNumber) {
            return -1;
        }

        if (after_number > 0) {
            resourceUrl += "?after_number="+after_number;
        }

        boolean dateReached = false;

        try {
            String response = getInstance().get(resourceUrl);
            JsonParser parser = new JsonParser();
            JsonObject allAnswer = parser.parse(response).getAsJsonObject();

            JsonArray commitsArray = allAnswer.getAsJsonArray("commits");

            for (JsonElement commitJson : commitsArray) {
                Commit commit = createGson().fromJson(commitJson, Commit.class);
                commits.put(commit.getId(), commit);
            }

            JsonArray buildArray = allAnswer.getAsJsonArray("builds");

            int lastBuildNumber = Integer.MAX_VALUE;

            for (JsonElement buildJson : buildArray) {
                Build build = createGson().fromJson(buildJson, Build.class);

                if (build.getNumber() != null) {
                    int buildNumber = Integer.parseInt(build.getNumber());
                    if (lastBuildNumber > buildNumber) {
                        lastBuildNumber = buildNumber;
                    }
                }

                if (build.getFinishedAt() != null && !build.getFinishedAt().after(date)) {
                    dateReached = true;
                    break;
                }
            }

            if (date != null && !dateReached) {
                return getTheLastBuildNumberOfADate(slug, date, lastBuildNumber, onlyAfterNumber);
            } else {
                return lastBuildNumber;
            }
        } catch (IOException e) {
            getInstance().getLogger().warn("Error when getting list of builds from slug "+slug+" : "+e.getMessage());
        }

        return -1;
    }

    public static List<Build> getBuildsFromSlugWithLimitDate(String slug, Date limitDate) {
        List<Build> result = new ArrayList<Build>();
        getBuildsFromSlugRecursively(slug, result, limitDate, 0, 0, getEventTypes(), 0, null, -1, false, null);
        return result;
    }

    public static List<Build> getBuildsFromSlug(String slug) {
        return getBuildsFromSlugWithLimitDate(slug, null);
    }

    public static List<Build> getBuildsFromRepositoryWithLimitDate(Repository repository, Date limitDate) {
        List<Build> result = new ArrayList<Build>();
        getBuildsFromSlugRecursively(repository.getSlug(), result, limitDate, 0, 0, getEventTypes(), 0, null, -1, false, null);

        for (Build b : result) {
            b.setRepository(repository);
        }
        return result;
    }

    public static List<Build> getBuildsFromRepositoryWithLimitDate(Repository repository) {
        return getBuildsFromRepositoryWithLimitDate(repository, null);
    }

    /**
     * Return the last build before the given build which respect the given status and which is from the same PR if it's a PR build. If given status is null, it will return the last build before.
     * If no build is found, it returns null.
     * @param build
     * @param status
     * @return
     */
    public static Build getLastBuildOfSameBranchOfStatusBeforeBuild(Build build, BuildStatus status) {
        String slug = build.getRepository().getSlug();
        List<Build> results = new ArrayList<Build>();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -1);
        Date limitDate = calendar.getTime();

        int after_number = 0;
        try {
            after_number = Integer.parseInt(build.getNumber());
        } catch (NumberFormatException e) {
            getInstance().getLogger().error("Error while parsing build number for build id: "+build.getId(),e);
            return null;
        }

        int limitNumber = 1;
        List<String> eventTypes = new ArrayList<String>();
        int prNumber;

        if (build.isPullRequest()) {
            eventTypes.add("pull_request");
            prNumber = build.getPullRequestNumber();
        } else {
            eventTypes.add("cron");
            eventTypes.add("push");
            prNumber = -1;
        }

        getBuildsFromSlugRecursively(slug, results, limitDate, after_number, after_number, eventTypes, limitNumber, status, prNumber, true, build.getCommit().getBranch());

        if (results.size() > 0) {
            if (results.size() > 1) {
                Collections.sort(results);
            }
            return results.get(results.size()-1);
        } else {
            return null;
        }
    }

    public static Build getLastBuildFromMaster(Repository repository) {
        String slug = repository.getSlug();
        List<Build> results = new ArrayList<Build>();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -10);
        Date limitDate = calendar.getTime();

        int limitNumber = 1;
        List<String> eventTypes = new ArrayList<String>();
        eventTypes.add("cron");
        eventTypes.add("push");
        int prNumber = -1;

        getBuildsFromSlugRecursively(slug, results, limitDate, 0, 0, eventTypes, limitNumber, null, prNumber, false, null);

        if (results.size() > 0) {
            return results.get(0);
        } else {
            return null;
        }
    }

    public static List<Build> getBuildsFromRepositoryInTimeInterval(Repository repository, Date initialDate, Date finalDate) {
        int lastBuildNumber = getTheLastBuildNumberOfADate(repository.getSlug(), finalDate, 0, false);
        if (lastBuildNumber != -1) {
            List<Build> results = new ArrayList<Build>();

            int after_number = lastBuildNumber + 1;

            getBuildsFromSlugRecursively(repository.getSlug(), results, initialDate, after_number, after_number, getEventTypes(), 0, null, -1, true, null);

            for (Build b : results) {
                b.setRepository(repository);
            }
            if (results.size() > 1) {
                Collections.sort(results);
            }
            return results;
        }
        return null;
    }
}
