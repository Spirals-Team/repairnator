package fr.inria.spirals.repairnator.realtime;

import fr.inria.jtravis.entities.Build;

// Submit build ids received from websocket
public interface BuildSubmitter {
    void submitBuild(Build b);
}
