package fr.inria.spirals.repairnator.states;

/**
 * Created by urli on 03/01/2017.
 */
public enum PipelineState {
    TESTABLE,
    PATCHED,
    BUILDNOTCHECKEDOUT,
    TESTERRORS,
    SOURCEDIRNOTCOMPUTED,
    NOTBUILDABLE,
    TESTFAILURES,
    NOTCLONABLE,
    NOTFAILING,
    NOTTESTABLE,
    CLASSPATHERROR,
    TESTDIRNOTCOMPUTED
}
