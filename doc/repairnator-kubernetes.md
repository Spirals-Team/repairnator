# Repairnator in Kubernetes

This page documents how to deploy the Repairnator components in K8.

## Prerequisites

* kubectl
* a working k8s cluster (preferable high cpu capacity)
* gcloud (for creating the cluster mentoned above and creating mongodb if you don't have them already)

The starting folder for these deployment below is `repairnator/kubernetes-support`.

## Run

Setup activeMQ

```
cd repairnator/kubernetes-support
kubectl create -f queue-for-buildids/activemq.yaml
```

BuildRainer: build build-rainer jar and run it.

```
cd repairnator/build-rainer
mvn install -DskipTest
java -jar target/build-rainer-1.0-SNAPSHOT-jar-with-dependencies.jar
```

build rainer should now be running and submitting builds to `pipeline` queue on ActiveMQ.

Worker: to deploy a pipeline worker which pulls build ids from ActiveMQ, run repair attempts and creates PRs if patches are found:

```
kubectl create -f ./repairnator/kubernetes-support/repairnator-deployment-yamlfiles/repairnator-pipeline.yaml
```


## Troubleshooting

Proxy activemq server 

```
kubectl get pods
kubectl port-forward activemq-XXXXXXX-XXXXX 1099:1099 8161:8161 61613:61613 61616:61616
```

Send a build id to queue 

```
# 566070885 is a failed travis build from this [repo](https://github.com/Tailp/travisplay)
python /queue-for-buildids/publisher.py -d /queue/pipeline 566070885
```

Check the pipeline output by

```
kubectl get pods 
kubectl logs -f repairnator-pipeline-XXXXXXXXX-XXXXX
```
