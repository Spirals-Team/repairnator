package fr.inria.spirals.repairnator.dockerpool.serializer;

import com.google.api.services.sheets.v4.Sheets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.inria.spirals.repairnator.serializer.ProcessSerializer;
import fr.inria.spirals.repairnator.Utils;
import fr.inria.spirals.repairnator.serializer.SerializerType;
import fr.inria.spirals.repairnator.serializer.engines.SerializedData;
import fr.inria.spirals.repairnator.serializer.engines.SerializerEngine;
import fr.inria.spirals.repairnator.serializer.gspreadsheet.GoogleSpreadSheetFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by urli on 03/03/2017.
 */
public class TreatedBuildTracking extends ProcessSerializer {
    private Logger logger = LoggerFactory.getLogger(TreatedBuildTracking.class);

    private String runid;
    private Integer buildId;
    private String containerId;
    private String status;

    public TreatedBuildTracking(List<SerializerEngine> engines, String runid, Integer buildId) throws IOException {
        super(engines, SerializerType.TREATEDBUILD);

        this.runid = runid;
        this.buildId = buildId;
        this.containerId = "N/A";
        this.status = "DETECTED";
        this.serialize();
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    private List<Object> serializeAsList() {
        Date date = new Date();

        List<Object> dataCol = new ArrayList<Object>();
        dataCol.add(runid);
        dataCol.add(buildId);
        dataCol.add(containerId);
        dataCol.add(Utils.formatCompleteDate(date));
        dataCol.add(Utils.formatOnlyDay(date));
        dataCol.add(Utils.getHostname());
        dataCol.add(status);
        return dataCol;
    }

    private JsonElement serializeAsJson() {
        Date date = new Date();

        JsonObject result = new JsonObject();
        result.addProperty("runId", runid);
        result.addProperty("buildId", buildId);
        result.addProperty("containerId", containerId);
        result.addProperty("dateReproducedBuild", Utils.formatCompleteDate(date));
        result.addProperty("dayReproducedBuild", Utils.formatOnlyDay(date));
        result.addProperty("hostname", Utils.getHostname());
        result.addProperty("status", status);

        return result;
    }

    public void serialize() {
        SerializedData data = new SerializedData(this.serializeAsList(), this.serializeAsJson());

        List<SerializedData> allData = new ArrayList<>();
        allData.add(data);

        for (SerializerEngine engine : this.getEngines()) {
            engine.serialize(allData, this.getType());
        }
    }
}
