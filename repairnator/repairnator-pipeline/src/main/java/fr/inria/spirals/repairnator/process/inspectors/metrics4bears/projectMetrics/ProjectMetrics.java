package fr.inria.spirals.repairnator.process.inspectors.metrics4bears.projectMetrics;

public class ProjectMetrics {

    private int numberModules;
    private int numberSourceFiles;
    private int numberTestFiles;
    private int numberLibrariesFailingModule;

    public ProjectMetrics() {}

    public int getNumberModules() {
        return numberModules;
    }

    public void setNumberModules(int numberModules) {
        this.numberModules = numberModules;
    }

    public int getNumberSourceFiles() {
        return numberSourceFiles;
    }

    public void setNumberSourceFiles(int numberSourceFiles) {
        this.numberSourceFiles = numberSourceFiles;
    }

    public int getNumberTestFiles() {
        return numberTestFiles;
    }

    public void setNumberTestFiles(int numberTestFiles) {
        this.numberTestFiles = numberTestFiles;
    }

    public int getNumberLibrariesFailingModule() {
        return numberLibrariesFailingModule;
    }

    public void setNumberLibrariesFailingModule(int numberLibrariesFailingModule) {
        this.numberLibrariesFailingModule = numberLibrariesFailingModule;
    }

}
