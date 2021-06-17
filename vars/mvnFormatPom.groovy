#!/usr/bin/env groovy

def call() {
    echo "Checking that pom file is formatted."

    List<String> incorrectFiles=new ArrayList<>()
    runCmdExitCode "git diff pom.xml"
    def outputAndExitCodeForDiff = runCmdExitCode "git diff --name-only --relative"
    echo "outputAndExitCodeForDiff :${outputAndExitCodeForDiff}"
    if(outputAndExitCodeForDiff[0]){
        "${outputAndExitCodeForDiff[0]}".split("\n").each {
            filename->incorrectFiles.add(filename)
        }
    }
    if (incorrectFiles != null && !incorrectFiles.isEmpty()) {
        def formatFilesWithLineBreaker = incorrectFiles.join("\n")

        runCmd "git add ${incorrectFiles.join(' ')}"
        echo 'Unformatted files were found!'
    } else {
        echo 'Successful, all files are properly formatted.'
    }
    return incorrectFiles
}
