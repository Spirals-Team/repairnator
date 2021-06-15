package fr.inria.spirals.repairnator.pipeline;

import fr.inria.spirals.repairnator.process.step.repair.AbstractRepairStep;
import fr.inria.spirals.repairnator.process.step.repair.NPERepair;
import fr.inria.spirals.repairnator.process.step.repair.Sorald;
import fr.inria.spirals.repairnator.process.step.repair.NPERepairSafe;
import fr.inria.spirals.repairnator.process.step.repair.AssertFixerRepair;
import fr.inria.spirals.repairnator.process.step.repair.sequencer.SequencerRepair;
import fr.inria.spirals.repairnator.process.step.repair.nopol.NopolAllTestsRepair;
import fr.inria.spirals.repairnator.process.step.repair.nopol.NopolMultiWithTestExclusionRepair;
import fr.inria.spirals.repairnator.process.step.repair.nopol.NopolSingleTestRepair;
import fr.inria.spirals.repairnator.process.step.repair.astor.AstorJKaliRepair; 
import fr.inria.spirals.repairnator.process.step.repair.astor.AstorJGenProgRepair;
import fr.inria.spirals.repairnator.process.step.repair.astor.AstorJMutRepair;
import fr.inria.spirals.repairnator.process.step.repair.styler.StylerRepair;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.ArrayList;

/**
 * This class defines a java ServiceLoader to automatically discover the available
 * repair steps from the manifest (see the resources).
 */
public class RepairToolsManager {
    private static RepairToolsManager instance;
    private Map<String, AbstractRepairStep> repairTools;
    private ServiceLoader<AbstractRepairStep> repairToolLoader = ServiceLoader.load(AbstractRepairStep.class);
    private RepairToolsManager() {
        this.repairTools = new HashMap<>();
        this.discoverRepairTools();
    }

    public static RepairToolsManager getInstance() {
        if (instance == null) {
            instance = new RepairToolsManager();
        }
        return instance;
    }

    /**
     * This method allows to refresh the list of repair tools
     * It is mainly used for test purpose.
     */
    public void discoverRepairTools() {
        this.repairTools.clear();
        this.repairToolLoader.reload();

        for (AbstractRepairStep repairStep : this.repairToolLoader) {
            this.repairTools.put(repairStep.getRepairToolName(), repairStep);
        }

    }

    /* same as discoverRepairtools but does not use service loader, also used to reset the repairtools - meant for Jenkins*/
    public void manualLoadRepairTools() {
        this.repairTools.clear();
        /* Manual loading */
        AbstractRepairStep npe = new NPERepair();
        this.repairTools.put(npe.getRepairToolName(),npe);
        AbstractRepairStep npeSafe = new NPERepairSafe();
        this.repairTools.put(npeSafe.getRepairToolName(),npeSafe);
        AbstractRepairStep assertFixer = new AssertFixerRepair();
        this.repairTools.put(assertFixer.getRepairToolName(),assertFixer);
        AbstractRepairStep sequencer = new SequencerRepair();
        this.repairTools.put(sequencer.getRepairToolName(),sequencer);
        AbstractRepairStep nopolAllTests = new NopolAllTestsRepair();
        this.repairTools.put(nopolAllTests.getRepairToolName(),nopolAllTests);
        AbstractRepairStep nopolTestExclusionStrategy = new NopolMultiWithTestExclusionRepair();
        this.repairTools.put(nopolTestExclusionStrategy.getRepairToolName(),nopolTestExclusionStrategy);
        AbstractRepairStep nopolSingleTest = new NopolSingleTestRepair();
        this.repairTools.put(nopolSingleTest.getRepairToolName(),nopolSingleTest);
        AbstractRepairStep astorJGenProg = new AstorJGenProgRepair();
        this.repairTools.put(astorJGenProg.getRepairToolName(),astorJGenProg);
        AbstractRepairStep astorJKali = new AstorJKaliRepair();
        this.repairTools.put(astorJKali.getRepairToolName(),astorJKali);
        AbstractRepairStep astorJMut = new AstorJMutRepair();
        this.repairTools.put(astorJMut.getRepairToolName(),astorJMut);
        AbstractRepairStep sorald = new Sorald();
        this.repairTools.put(sorald.getRepairToolName(),sorald);
        AbstractRepairStep styler = new StylerRepair();
        this.repairTools.put(styler.getRepairToolName(), styler);
    }

    public static AbstractRepairStep getStepFromName(String name) {
        return getInstance().repairTools.get(name);
    }

    public static Set<String> getRepairToolsName() {
        return getInstance().repairTools.keySet();
    }
}
