#!/usr/bin/env bash
set -e

if [ "$#" -ne 2 ]; then
    echo "Usage: ./check_branches.sh <input branch names> <output result>"
    exit 2
fi

# Use to create args in the command line for optionnal arguments
function ca {
  if [ -z "$2" ];
  then
      echo ""
  else
    if [ "$2" == "null" ];
    then
        echo ""
    else
        echo "$1 $2 "
    fi
  fi
}


if [ ! -f $1 ]; then
    echo "The list of input branches must be an existing file ($1 not found)"
    exit -1
fi

if [ -f $2 ]; then
    echo "The output file already exists, please specify a path for a new file to create ($2 already exists)"
    exit -1
fi

INPUT=$1
OUTPUT=$2

touch $OUTPUT

SCRIPT_DIR="$(dirname "${BASH_SOURCE[0]}")"

echo "Set environment variables"
. $SCRIPT_DIR/utils/init_script.sh

echo "Create log directory: $LOG_DIR"
mkdir $LOG_DIR

RUN_ID=`date "+%Y-%m-%d_%H%M%S"`

echo "Copy jar and prepare docker image"
mkdir $REPAIRNATOR_RUN_DIR

cp $REPAIRNATOR_CHECKBRANCHES_JAR $REPAIRNATOR_CHECKBRANCHES_DEST_JAR

echo "Pull the docker machine (name: $DOCKER_CHECKBRANCHES_TAG)..."
docker pull $DOCKER_CHECKBRANCHES_TAG

echo "Analyze started: `date "+%Y-%m-%d_%H%M%S"`" > $OUTPUT
echo "Considered repository: $CHECK_BRANCH_REPOSITORY" >> $OUTPUT

echo "Launch docker pool checkbranches ..."
args="`ca --smtpServer $SMTP_SERVER``ca --notifyto $NOTIFY_TO`"
if [ "$NOTIFY_ENDPROCESS" -eq 1 ]; then
    args="$args --notifyEndProcess"
fi
if [ "$HUMAN_PATCH" -eq 1 ]; then
    args="$args --humanPatch"
fi

echo "Supplementary args for docker pool checkbranches $args"
java -jar $REPAIRNATOR_CHECKBRANCHES_DEST_JAR -t $NB_THREADS -n $DOCKER_CHECKBRANCHES_TAG -i $INPUT -o $OUTPUT -r $CHECK_BRANCH_REPOSITORY -g $DAY_TIMEOUT --runId $RUN_ID $args &> $LOG_DIR/checkbranches.log

echo "Docker pool checkbranches finished, delete the run directory ($REPAIRNATOR_RUN_DIR)"
rm -rf $REPAIRNATOR_RUN_DIR

echo "Analyze finished: `date "+%Y-%m-%d_%H%M%S"`" >> $OUTPUT