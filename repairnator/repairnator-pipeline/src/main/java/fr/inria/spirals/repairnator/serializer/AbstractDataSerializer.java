package fr.inria.spirals.repairnator.serializer;


import fr.inria.spirals.repairnator.LauncherMode;
import fr.inria.spirals.repairnator.ScannedBuildStatus;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.process.inspectors.JobStatus;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.serializer.engines.SerializerEngine;

import java.util.List;


/**
 * Created by urli on 20/01/2017.
 */
public abstract class AbstractDataSerializer extends Serializer {



    public AbstractDataSerializer(List<SerializerEngine> engines, SerializerType type) {
        super(engines, type);
    }

    protected String getPrettyPrintState(ProjectInspector inspector) {

        JobStatus jobStatus = inspector.getJobStatus();
        switch (jobStatus.getState()) {
            case NONE:
            case INIT:
            case NOTCLONABLE:
                return "not clonable";

            case CLONABLE:
            case BUILDNOTCHECKEDOUT:
            case PATCHEDBUILDNOTCHECKEDOUT:
            case PREVIOUSBUILDCODENOTCHECKEDOUT:
                return "error in check out";

            case DEPENDENCY_UNRESOLVABLE:
            case DEPENDENCY_RESOLVED:
            case BUILDCHECKEDOUT:
            case PATCHEDBUILDCHECKEDOUT:
            case PREVIOUSBUILDCODECHECKEDOUT:
            case NOTBUILDABLE:
                return "not buildable";

            case BUILDABLE:
            case NOTTESTABLE:
                return "not testable";

            case TESTABLE:
                return "testable";

            case NOTFAILING:
                return "not failing";

            case HASTESTFAILURE:
                return "test failure";

            case HASTESTERRORS:
                return "test errors";

            case SOURCEDIRCOMPUTED:
            case SOURCEDIRNOTCOMPUTED:
            case NOTPATCHED:
                if (jobStatus.isReproducedAsFail()) {
                    return "test failure";
                } else {
                    return "test errors";
                }

            case PATCHED:
                return "PATCHED";

            case FIXERBUILDCASE1:
                return "FIXERBUILD_CASE1";

            case FIXERBUILDCASE2:
                return "FIXERBUILD_CASE2";

            case CLASSPATHCOMPUTED:
            case CLASSPATHNOTCOMPUTED:
            case SQUASHED_REPO:
            case NOT_SQUASHED_REPO:
                if (RepairnatorConfig.getInstance().getLauncherMode() == LauncherMode.BEARS) {
                    if (inspector.getBuildToBeInspected().getStatus() == ScannedBuildStatus.FAILING_AND_PASSING) {
                        return "FIXERBUILD_CASE1";
                    } else {
                        return "FIXERBUILD_CASE2";
                    }
                } else {
                    if (jobStatus.isReproducedAsFail()) {
                        return "test failure";
                    } else {
                        return "test errors";
                    }
                }

            default:
                return "unknown";
        }
    }

    public abstract void serializeData(ProjectInspector inspector);
}
