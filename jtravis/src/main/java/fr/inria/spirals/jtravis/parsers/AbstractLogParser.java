package fr.inria.spirals.jtravis.parsers;

import fr.inria.spirals.jtravis.entities.TestsInformation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by urli on 03/01/2017.
 */
public abstract class AbstractLogParser {

    private BufferedReader reader;

    protected String log;

    protected List<TravisFold> folds;
    protected TravisFold outOfFold;

    protected int runningTests;
    protected int failingTests;
    protected int passingTests;
    protected int skippingTests;
    protected int erroredTests;

    public AbstractLogParser(String log) {
        this.runningTests = 0;
        this.failingTests = 0;
        this.passingTests = 0;
        this.skippingTests = 0;
        this.erroredTests = 0;

        this.log = log;
        this.folds = new ArrayList<TravisFold>();

        this.reader = new BufferedReader(new StringReader(log));
        try {
            this.analyzeLogToCreateFolds();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void analyzeLogToCreateFolds() throws IOException {

        TravisFold fold = null;
        this.outOfFold = new TravisFold("outOfFold");

        Pattern patternFoldStart = Pattern.compile(".*travis_fold:start:([\\w\\.]*)");
        Pattern patternFoldEnd = Pattern.compile(".*travis_fold:end:([\\w\\.]*)");

        while (this.reader.ready()) {
            String line = this.reader.readLine();

            if (line != null) {
                Matcher matcherFoldStart = patternFoldStart.matcher(line);
                Matcher matcherFoldEnd = patternFoldEnd.matcher(line);

                if (matcherFoldStart.matches()) {
                    String title = matcherFoldStart.group(1);
                    fold = new TravisFold(title);
                    this.folds.add(fold);
                    continue;
                }

                if (matcherFoldEnd.matches()) {
                    fold = null;
                    continue;
                }

                if (fold == null) {
                    this.outOfFold.addContent(line);
                } else {
                    fold.addContent(line);
                }
            } else {
                break;
            }
        }
        this.reader.close();
    }

    public TestsInformation getTestsInformation() {
        TestsInformation result = new TestsInformation();
        result.setErrored(this.erroredTests);
        result.setFailing(this.failingTests);
        result.setPassing(this.passingTests);
        result.setRunning(this.runningTests);
        result.setSkipping(this.skippingTests);
        return result;
    }
}
