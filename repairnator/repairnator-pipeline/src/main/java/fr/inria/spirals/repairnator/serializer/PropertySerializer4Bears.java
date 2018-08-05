package fr.inria.spirals.repairnator.serializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.inspectors.properties.Properties;
import fr.inria.spirals.repairnator.process.inspectors.properties.PropertySerializerAdapter;
import fr.inria.spirals.repairnator.serializer.engines.SerializedData;
import fr.inria.spirals.repairnator.serializer.engines.SerializerEngine;

import java.util.ArrayList;
import java.util.List;

public class PropertySerializer4Bears extends AbstractDataSerializer {

    public PropertySerializer4Bears(List<SerializerEngine> engines) {
        super(engines, SerializerType.PROPERTIES4BEARS);
    }

    @Override
    public void serializeData(ProjectInspector inspector) {
        Gson gson = new GsonBuilder().registerTypeAdapter(Properties.class, new PropertySerializerAdapter()).create();
        JsonObject element = (JsonObject)gson.toJsonTree(inspector.getJobStatus().getProperties());

        element.addProperty("runId", RepairnatorConfig.getInstance().getRunId());

        List<SerializedData> dataList = new ArrayList<>();

        dataList.add(new SerializedData(new ArrayList<>(), element));

        for (SerializerEngine engine : this.getEngines()) {
            engine.serialize(dataList, this.getType());
        }
    }
}
