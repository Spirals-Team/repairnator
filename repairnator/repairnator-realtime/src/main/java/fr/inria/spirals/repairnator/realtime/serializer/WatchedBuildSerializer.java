package fr.inria.spirals.repairnator.realtime.serializer;

import com.google.gson.JsonObject;
import fr.inria.jtravis.entities.Build;
import fr.inria.spirals.repairnator.utils.DateUtils;
import fr.inria.spirals.repairnator.utils.Utils;
import fr.inria.spirals.repairnator.realtime.RTScanner;
import fr.inria.spirals.repairnator.serializer.SerializerImpl;
import fr.inria.spirals.repairnator.serializer.SerializerType;
import fr.inria.spirals.repairnator.serializer.engines.SerializedData;
import fr.inria.spirals.repairnator.serializer.engines.SerializerEngine;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class WatchedBuildSerializer extends SerializerImpl {

    RTScanner rtScanner;

    public WatchedBuildSerializer(List<SerializerEngine> engines, RTScanner rtScanner) {
        super(engines, SerializerType.RTSCANNER);
        this.rtScanner = rtScanner;
    }

    private List<Object> serializeAsList(Build build)  {
        List<Object> result = new ArrayList<>();
        result.add(Utils.getHostname());
        result.add(this.rtScanner.getRunId());
        result.add(DateUtils.formatCompleteDate(new Date()));
        result.add(DateUtils.formatCompleteDate(build.getFinishedAt()));
        if (build.getRepository() == null) {
            result.add("ID="+build.getRepository().getId());
        } else {
            result.add(build.getRepository().getSlug());
        }
        result.add(build.getId());
        result.add(build.getState());

        return result;
    }

    private JsonObject serializeAsJson(Build build) {
        JsonObject result = new JsonObject();

        result.addProperty("hostname", Utils.getHostname());
        result.addProperty("runId", this.rtScanner.getRunId());
        this.addDate(result, "dateWatched", new Date());
        result.addProperty("dateWatchedStr", DateUtils.formatCompleteDate(new Date()));
        this.addDate(result, "dateBuildEnd", build.getFinishedAt());
        result.addProperty("dateBuildEndStr", DateUtils.formatCompleteDate(build.getFinishedAt()));
        if (build.getRepository() == null) {
            result.addProperty("repository", "ID="+build.getRepository().getId());
        } else {
            result.addProperty("repository", build.getRepository().getSlug());
        }
        result.addProperty("buildId", build.getId());
        result.addProperty("status", build.getState().name());

        return result;
    }

    public void serialize(Build build) {
        SerializedData data = new SerializedData(this.serializeAsList(build), this.serializeAsJson(build));

        List<SerializedData> allData = new ArrayList<>();
        allData.add(data);

        for (SerializerEngine engine : this.getEngines()) {
            engine.serialize(allData, this.getType());
        }
    }
}
