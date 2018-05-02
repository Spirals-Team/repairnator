package fr.inria.spirals.repairnator.process.inspectors;

import ch.qos.logback.classic.Level;
import fr.inria.main.AstorOutputStatus;
import fr.inria.jtravis.entities.Build;
import fr.inria.jtravis.helpers.BuildHelper;
import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.pipeline.RepairToolsManager;
import fr.inria.spirals.repairnator.process.nopol.NopolStatus;
import fr.inria.spirals.repairnator.states.LauncherMode;
import fr.inria.spirals.repairnator.states.PipelineState;
import fr.inria.spirals.repairnator.states.PushState;
import fr.inria.spirals.repairnator.states.ScannedBuildStatus;
import fr.inria.spirals.repairnator.Utils;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.notifier.AbstractNotifier;
import fr.inria.spirals.repairnator.notifier.PatchNotifier;
import fr.inria.spirals.repairnator.notifier.engines.NotifierEngine;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.serializer.InspectorSerializer;
import fr.inria.spirals.repairnator.serializer.NopolSerializer;
import fr.inria.spirals.repairnator.serializer.SerializerType;
import fr.inria.spirals.repairnator.serializer.engines.SerializedData;
import fr.inria.spirals.repairnator.serializer.engines.SerializerEngine;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Created by urli on 24/04/2017.
 */
public class TestProjectInspector {

    private static final String SOLVER_PATH_DIR = "src/test/resources/z3/";
    private static final String SOLVER_NAME_LINUX = "z3_for_linux";
    private static final String SOLVER_NAME_MAC = "z3_for_mac";

    @Before
    public void setUp() {
        String solverPath;
        if (isMac()) {
            solverPath = SOLVER_PATH_DIR+SOLVER_NAME_MAC;
        } else {
            solverPath = SOLVER_PATH_DIR+SOLVER_NAME_LINUX;
        }

        RepairnatorConfig config = RepairnatorConfig.getInstance();
        config.setZ3solverPath(solverPath);
        config.setPush(true);
        config.setPushRemoteRepo("");
        config.setRepairTools(RepairToolsManager.getRepairToolsName());
        Utils.setLoggersLevel(Level.ERROR);
    }

    public static boolean isMac() {
        String OS = System.getProperty("os.name").toLowerCase();
        return (OS.contains("mac"));
    }

    @After
    public void tearDown() {
        RepairnatorConfig.deleteInstance();
    }

    @Test
    public void testPatchFailingProject() throws IOException, GitAPIException {
        int buildId = 208897371; // surli/failingProject only-one-failing

        Path tmpDirPath = Files.createTempDirectory("test_complete");
        File tmpDir = tmpDirPath.toFile();
        tmpDir.deleteOnExit();

        Optional<Build> optionalBuild = RepairnatorConfig.getInstance().getJTravis().build().fromId(buildId);
        assertTrue(optionalBuild.isPresent());
        Build failingBuild = optionalBuild.get();

        BuildToBeInspected buildToBeInspected = new BuildToBeInspected(failingBuild, null, ScannedBuildStatus.ONLY_FAIL, "test");

        List<AbstractDataSerializer> serializers = new ArrayList<>();
        List<AbstractNotifier> notifiers = new ArrayList<>();

        List<SerializerEngine> serializerEngines = new ArrayList<>();
        SerializerEngine serializerEngine = mock(SerializerEngine.class);
        serializerEngines.add(serializerEngine);

        List<NotifierEngine> notifierEngines = new ArrayList<>();
        NotifierEngine notifierEngine = mock(NotifierEngine.class);
        notifierEngines.add(notifierEngine);

        serializers.add(new InspectorSerializer(serializerEngines));
        serializers.add(new NopolSerializer(serializerEngines));

        notifiers.add(new PatchNotifier(notifierEngines));

        RepairnatorConfig config = RepairnatorConfig.getInstance();
        config.setLauncherMode(LauncherMode.REPAIR);

        ProjectInspector inspector = new ProjectInspector(buildToBeInspected, tmpDir.getAbsolutePath(), serializers, notifiers);
        inspector.run();

        JobStatus jobStatus = inspector.getJobStatus();
        assertThat(jobStatus.getAstorStatus(), is(AstorOutputStatus.MAX_GENERATION));
        assertThat(jobStatus.getPipelineState(), is(PipelineState.NOPOL_PATCHED));
        assertThat(jobStatus.getPushState(), is(PushState.REPAIR_INFO_COMMITTED));
        assertThat(jobStatus.getFailureLocations().size(), is(1));
        assertThat(jobStatus.getMetrics().getFailureNames().size(), is(1));

        String remoteBranchName = "surli-failingProject-208897371-20170308-040702";
        assertEquals(remoteBranchName, inspector.getRemoteBranchName());

        verify(notifierEngine, times(1)).notify(anyString(), anyString());
        verify(serializerEngine, times(1)).serialize(anyListOf(SerializedData.class), eq(SerializerType.INSPECTOR));
        verify(serializerEngine, times(1)).serialize(anyListOf(SerializedData.class), eq(SerializerType.NOPOL));

        Git gitDir = Git.open(new File(inspector.getRepoToPushLocalPath()));
        Iterable<RevCommit> logs = gitDir.log().call();

        Iterator<RevCommit> iterator = logs.iterator();
        assertThat(iterator.hasNext(), is(true));

        RevCommit commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("End of the repairnator process"));

        commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("Automatic repair"));

        commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("Bug commit"));

        assertThat(iterator.hasNext(), is(false));
    }

    @Ignore
    @Test
    public void testPatchFailingProjectM70() throws IOException, GitAPIException {
        int buildId = 269201915; // surli/failingProject only-one-failing

        Path tmpDirPath = Files.createTempDirectory("test_complete");
        File tmpDir = tmpDirPath.toFile();
        tmpDir.deleteOnExit();

        Optional<Build> optionalBuild = RepairnatorConfig.getInstance().getJTravis().build().fromId(buildId);
        assertTrue(optionalBuild.isPresent());
        Build failingBuild = optionalBuild.get();

        BuildToBeInspected buildToBeInspected = new BuildToBeInspected(failingBuild, null, ScannedBuildStatus.ONLY_FAIL, "test");

        List<AbstractDataSerializer> serializers = new ArrayList<>();
        List<AbstractNotifier> notifiers = new ArrayList<>();

        List<SerializerEngine> serializerEngines = new ArrayList<>();
        SerializerEngine serializerEngine = mock(SerializerEngine.class);
        serializerEngines.add(serializerEngine);

        List<NotifierEngine> notifierEngines = new ArrayList<>();
        NotifierEngine notifierEngine = mock(NotifierEngine.class);
        notifierEngines.add(notifierEngine);

        serializers.add(new InspectorSerializer(serializerEngines));
        serializers.add(new NopolSerializer(serializerEngines));

        notifiers.add(new PatchNotifier(notifierEngines));

        RepairnatorConfig config = RepairnatorConfig.getInstance();
        config.setLauncherMode(LauncherMode.REPAIR);

        ProjectInspector inspector = new ProjectInspector(buildToBeInspected, tmpDir.getAbsolutePath(), serializers, notifiers);
        inspector.run();

        JobStatus jobStatus = inspector.getJobStatus();
        assertThat(jobStatus.getAstorStatus(), is(AstorOutputStatus.STOP_BY_PATCH_FOUND));
        assertThat(jobStatus.getPipelineState(), is(PipelineState.NOPOL_NOTPATCHED));
        assertThat(jobStatus.getPushState(), is(PushState.REPAIR_INFO_COMMITTED));
        assertThat(jobStatus.getFailureLocations().size(), is(1));
        assertThat(jobStatus.getMetrics().getFailureNames().size(), is(1));

        verify(notifierEngine, times(3)).notify(anyString(), anyString()); // notify for Astor, NPEFix and Nopol
        verify(serializerEngine, times(1)).serialize(anyListOf(SerializedData.class), eq(SerializerType.INSPECTOR));
        verify(serializerEngine, times(1)).serialize(anyListOf(SerializedData.class), eq(SerializerType.NOPOL));

        Git gitDir = Git.open(new File(inspector.getRepoToPushLocalPath()));
        Iterable<RevCommit> logs = gitDir.log().call();

        Iterator<RevCommit> iterator = logs.iterator();
        assertThat(iterator.hasNext(), is(true));

        RevCommit commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("End of the repairnator process"));

        commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("Automatic repair"));

        commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("Bug commit"));

        assertThat(iterator.hasNext(), is(false));
    }

    @Test
    public void testFailingProjectNotBuildable() throws IOException, GitAPIException {
        int buildId = 228303218; // surli/failingProject only-one-failing

        Path tmpDirPath = Files.createTempDirectory("test_complete2");
        File tmpDir = tmpDirPath.toFile();
        tmpDir.deleteOnExit();

        Optional<Build> optionalBuild = RepairnatorConfig.getInstance().getJTravis().build().fromId(buildId);
        assertTrue(optionalBuild.isPresent());
        Build failingBuild = optionalBuild.get();

        BuildToBeInspected buildToBeInspected = new BuildToBeInspected(failingBuild, null, ScannedBuildStatus.ONLY_FAIL, "test");

        List<AbstractDataSerializer> serializers = new ArrayList<>();
        List<AbstractNotifier> notifiers = new ArrayList<>();

        List<SerializerEngine> serializerEngines = new ArrayList<>();
        SerializerEngine serializerEngine = mock(SerializerEngine.class);
        serializerEngines.add(serializerEngine);

        serializers.add(new InspectorSerializer(serializerEngines));
        serializers.add(new NopolSerializer(serializerEngines));

        RepairnatorConfig config = RepairnatorConfig.getInstance();
        config.setLauncherMode(LauncherMode.REPAIR);

        ProjectInspector inspector = new ProjectInspector(buildToBeInspected, tmpDir.getAbsolutePath(), serializers, notifiers);
        inspector.run();

        JobStatus jobStatus = inspector.getJobStatus();
        assertThat(jobStatus.getPipelineState(), is(PipelineState.NOTBUILDABLE));
        assertThat(jobStatus.getPushState(), is(PushState.NONE));

        verify(serializerEngine, times(1)).serialize(anyListOf(SerializedData.class), eq(SerializerType.INSPECTOR));

    }

    @Test
    public void testSpoonException() throws IOException, GitAPIException {
        int buildId = 355743087; // surli/failingProject only-one-failing

        Path tmpDirPath = Files.createTempDirectory("test_spoonexception");
        File tmpDir = tmpDirPath.toFile();
        tmpDir.deleteOnExit();

        Optional<Build> optionalBuild = RepairnatorConfig.getInstance().getJTravis().build().fromId(buildId);
        assertTrue(optionalBuild.isPresent());
        Build failingBuild = optionalBuild.get();

        BuildToBeInspected buildToBeInspected = new BuildToBeInspected(failingBuild, null, ScannedBuildStatus.ONLY_FAIL, "test");

        List<AbstractDataSerializer> serializers = new ArrayList<>();
        List<AbstractNotifier> notifiers = new ArrayList<>();

        List<SerializerEngine> serializerEngines = new ArrayList<>();
        SerializerEngine serializerEngine = mock(SerializerEngine.class);
        serializerEngines.add(serializerEngine);

        serializers.add(new InspectorSerializer(serializerEngines));
        serializers.add(new NopolSerializer(serializerEngines));

        RepairnatorConfig config = RepairnatorConfig.getInstance();
        config.setLauncherMode(LauncherMode.REPAIR);

        ProjectInspector inspector = new ProjectInspector(buildToBeInspected, tmpDir.getAbsolutePath(), serializers, notifiers);
        inspector.run();

        JobStatus jobStatus = inspector.getJobStatus();
        assertThat(jobStatus.getPipelineState(), is(PipelineState.NOPOL_NOTPATCHED));
        assertThat(jobStatus.getNopolInformations().get(0).getStatus(), is(NopolStatus.EXCEPTION));

        verify(serializerEngine, times(1)).serialize(anyListOf(SerializedData.class), eq(SerializerType.INSPECTOR));

    }

    @Test
    public void testRepairingWithNPEFix() throws IOException, GitAPIException {
        int buildId = 253130137; // surli/failingProject npe

        Path tmpDirPath = Files.createTempDirectory("test_complete_npe");
        File tmpDir = tmpDirPath.toFile();
        tmpDir.deleteOnExit();

        Optional<Build> optionalBuild = RepairnatorConfig.getInstance().getJTravis().build().fromId(buildId);
        assertTrue(optionalBuild.isPresent());
        Build failingBuild = optionalBuild.get();

        BuildToBeInspected buildToBeInspected = new BuildToBeInspected(failingBuild, null, ScannedBuildStatus.ONLY_FAIL, "testnpe");

        List<AbstractDataSerializer> serializers = new ArrayList<>();
        List<AbstractNotifier> notifiers = new ArrayList<>();

        List<SerializerEngine> serializerEngines = new ArrayList<>();
        SerializerEngine serializerEngine = mock(SerializerEngine.class);
        serializerEngines.add(serializerEngine);

        List<NotifierEngine> notifierEngines = new ArrayList<>();
        NotifierEngine notifierEngine = mock(NotifierEngine.class);
        notifierEngines.add(notifierEngine);

        serializers.add(new InspectorSerializer(serializerEngines));
        serializers.add(new NopolSerializer(serializerEngines));

        notifiers.add(new PatchNotifier(notifierEngines));

        RepairnatorConfig config = RepairnatorConfig.getInstance();
        config.setLauncherMode(LauncherMode.REPAIR);

        ProjectInspector inspector = new ProjectInspector(buildToBeInspected, tmpDir.getAbsolutePath(), serializers, notifiers);
        inspector.run();

        JobStatus jobStatus = inspector.getJobStatus();
        assertThat(jobStatus.getAstorStatus(), is(AstorOutputStatus.MAX_GENERATION));
        assertThat(jobStatus.getPipelineState(), is(PipelineState.NOPOL_PATCHED));
        assertThat(jobStatus.getPushState(), is(PushState.REPAIR_INFO_COMMITTED));
        assertThat(jobStatus.getFailureLocations().size(), is(1));
        assertThat(jobStatus.getMetrics().getFailureNames().size(), is(1));
        assertThat(jobStatus.isHasBeenPatched(), is(true));
        assertThat(jobStatus.getNpeFixPatches().size(), is(6));

        verify(notifierEngine, times(2)).notify(anyString(), anyString()); // Nopol and NPEFix
        verify(serializerEngine, times(1)).serialize(anyListOf(SerializedData.class), eq(SerializerType.INSPECTOR));
        verify(serializerEngine, times(1)).serialize(anyListOf(SerializedData.class), eq(SerializerType.NOPOL));

        Git gitDir = Git.open(new File(inspector.getRepoToPushLocalPath()));
        Iterable<RevCommit> logs = gitDir.log().call();

        Iterator<RevCommit> iterator = logs.iterator();
        assertThat(iterator.hasNext(), is(true));

        RevCommit commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("End of the repairnator process"));

        commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("Automatic repair"));

        commit = iterator.next();
        assertThat(commit.getShortMessage(), containsString("Bug commit"));

        assertThat(iterator.hasNext(), is(false));
    }
}
