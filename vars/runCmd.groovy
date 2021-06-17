#!/usr/bin/env groovy
// Runs a command. Platform agnostic since many linux commands can run on windows with special setup.
def call(def attrs = [:], String command) {
    boolean skipIfDryRun = attrs.skipIfDryRun != null ? attrs.skipIfDryRun.asBoolean() : false
    if (isSkip(command, skipIfDryRun)) {
        return
    }
    boolean value = env.IS_UNIX
    def command1 = prependSetupScript(command)
    if (value) {
        sh command1
    } else {
        bat command1
    }
}

// Prepend environment setup instruction to command (e.g. module add) .
// The extra setup changes will only live during this particular shell script call.
private String prependSetupScript(String command) {
    def command1 = command
    if (env.ENV_SETUP_SCRIPT != null && env.ENV_SETUP_SCRIPT != '') {
        command1 = env.ENV_SETUP_SCRIPT + "\n" + command
    }
    return command1
}

boolean isSkip(String cmd, boolean skipIfDryRun) {
    boolean isDryRun = "${env.DRY_RUN}".toBoolean()
    if (isDryRun && skipIfDryRun) {
        echo "Dry run enabled, skipped running: '${cmd}'"
        return true
    } else {
        return false
    }
}
