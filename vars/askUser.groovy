#!/usr/bin/env groovy

def call(choices)
{
    def selection

    // If a user is manually triggering this job, then ask what to build
    if (currentBuild.rawBuild.getCauses()[0].toString().contains('UserIdCause')) {
        echo "Build triggered by user, asking question..."
        try {
            timeout(time: 60, unit: 'SECONDS') {
                selection = input(id: 'selection', message: 'Select what to build', parameters: [
                        choice(choices: choices, description: 'Choose which build to execute', name: 'build')
                ])
            }
        } catch(err) {
            def user = err.getCauses()[0].getUser()
            if ('SYSTEM' == user.toString()) { // SYSTEM means timeout.
                selection = 'All'
            } else {
                // Aborted by user
                throw err
            }
        }
    } else {
        echo "Build triggered automatically, building 'All'..."
        selection = 'All'
    }

}
