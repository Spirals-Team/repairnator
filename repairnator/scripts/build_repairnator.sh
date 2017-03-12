#!/bin/bash

export M2_HOME=/opt/apache-maven-3.3.9
export PATH=$PATH:$M2_HOME/bin

HOME_REPAIR=/media/experimentations/repairnator

cd $HOME_REPAIR/github/nopol/nopol
git pull
mvn clean install

cd $HOME_REPAIR/github/librepair
git pull

cd jtravis
mvn clean install

cd ../repairnator
mvn clean install
