package fr.inria.spirals.repairnator.realtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.inria.jtravis.JTravis;
import fr.inria.jtravis.entities.Build;
import fr.inria.jtravis.entities.StateType;
import fr.inria.jtravis.entities.v2.JobV2;
import fr.inria.jtravis.helpers.JobHelper;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Super fast scanner inspired from @tduriex's travis-listener
 */
public class FastScanner implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FastScanner.class);

    private RTScanner rtScanner;
    public int nMaxBuildsToBeAnalyzedWithPipeline = 1000;

    public FastScanner() {
        this.rtScanner = new RTScanner("foo");
    }

    public static void main(String[] args) {
        new FastScanner().run();
    }

    @Override
    public void run() {
        LOGGER.debug("Start running inspect Jobs...");
        int nInteresting = 0;
        Set<Integer> done = new HashSet<Integer>();
        JobHelperv2 jobHelperv2 = new JobHelperv2(RepairnatorConfig.getInstance().getJTravis());
        while (true) {
            try {
                Optional<List<JobV2>> jobListOpt = jobHelperv2.allFromV2();
                JobV2 jobV2 = null;
                List<JobV2> jobList = jobListOpt.get();


                Map<StateType, Integer> stats = new HashMap<>();
                int nstats=0;
                jobV2 = jobList.get(0);
                int N=20;
                int GO_IN_THE_PAST= 100;
                for (int k=0;k<N;k++) {
                    for (JobV2 job : jobHelperv2.allSubSequentBuildsFrom(jobV2.getId() - 250*N - GO_IN_THE_PAST)) {
                        if (stats.keySet().contains(job.getState())) {
                            stats.put(job.getState(), stats.get(job.getState()) + 1);
                        } else {
                            stats.put(job.getState(), 1);
                        }
                        nstats++;

                        if ("java".equals(job.getConfig().getLanguage()) && StateType.FAILED.equals(job.getState()) && ! done.contains(job.getId())) {
                            System.out.println("=====" + job.getId());
                            done.add(job.getId());

                            Optional<Build> optionalBuild = RepairnatorConfig.getInstance().getJTravis().build().fromId(job.getBuildId());
                            FastScanner.this.rtScanner.submitBuildToExecution(optionalBuild.get());
                        }
                        jobV2 = job;
                    }
                }

                System.out.println(stats);
                System.out.println(stats.get(StateType.FAILED)*1./nstats);

                if (done.size()>nMaxBuildsToBeAnalyzedWithPipeline) {
                    break;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        } // end while loop
    }

}
