package com.onthegomap.planetiler.examples;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.Profile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.util.Glob;
import org.locationtech.jts.algorithm.MinimumAreaRectangle;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Example basemap using <a href="https://overturemaps.org/">Overture Maps</a> data.
 */
public class ShadowmapOvertureProfile implements Profile {

  @Override
  public void processFeature(SourceFeature source, FeatureCollector features) {
    var isUndergroundTag = source.getTag("is_underground");

    if (isUndergroundTag != null && (boolean) isUndergroundTag) {
      return;
    }

    var levelTag = source.getTag("level");

    if (isUndergroundTag == null && levelTag != null && (Integer)levelTag < 0) {
      return;
    }

    String layer = source.getSourceLayer();

    switch (layer) {
      case "building" -> {
        if (source.canBePolygon()) {
          processBuilding(source, features, false);
        }
      }
      case "building_part" -> {
        if (source.canBePolygon()) {
          processBuilding(source, features, true);
        }
      }
      case "land" -> {
        var tags = source.tags();
        var featureSubtype = (String) tags.get("subtype");
        var featureClass = (String) tags.get("class");

        switch (featureSubtype) {
          case "forest" -> {
            if (source.canBePolygon()) {
              processForest(source, features);
            }
          }
          case "tree" -> {
            switch (featureClass) {
              case "tree" -> {
                if (source.isPoint()) {
                  processTree(source, features);
                }
              }
              case "tree_row" -> {
                if (source.canBeLine()) {
                  processTreeRow(source, features);
                }
              }
            }
          }
        }
      }
      case null, default -> {
        // ignore for now
      }
    }
  }

  private void processBuilding(SourceFeature source, FeatureCollector features, boolean isPart) {
    var feature = features.polygon("building")
      .setAttr("height", source.getTag("height"))
      .setAttr("minHeight", source.getTag("min_height"))
      .setAttr("levels", source.getTag("num_floors"))
      .setAttr("minLevel", source.getTag("min_floor"))
      .setAttr("roofShape", source.getTag("roof_shape"))
      .setAttr("roofDirection", source.getTag("roof_direction"))
      .setAttr("roofOrientation", source.getTag("roof_orientation"))
      .setAttr("isPart", isPart);

    var roofShape = source.getTag("roof_shape");

    if (roofShape != null && !roofShape.equals("flat")) {
      setPolygonOMBB(feature);
    }

    setSourceIds(feature, source);
    setCommonFeatureParams(feature, source);

    feature.setBufferPixels(isPart ? 128 : 64);
    feature.setPixelToleranceAtAllZooms(0);

    if (feature.getAttrsAtZoom(0).containsKey("zenodoId")) {
      feature.setPixelToleranceAtAllZooms(0.08);
    }
  }

  private void processForest(SourceFeature source, FeatureCollector features) {
    var feature = features.polygon("forest");

    setLeafType(feature, source);
    setSourceIds(feature, source);
    setCommonFeatureParams(feature, source);
  }

  private void processTree(SourceFeature source, FeatureCollector features) {
    var feature = features.point("tree");
    var sourceTags = (HashMap<String, String>) source.getTag("source_tags");

    if (sourceTags.get("height") != null) {
      feature.setAttr("height", StreetsUtils.parseMeters(sourceTags.get("height")));
    }

    if (sourceTags.get("min_height") != null) {
      feature.setAttr("minHeight", StreetsUtils.parseMeters(sourceTags.get("min_height")));
    }

    setLeafType(feature, source);
    setSourceIds(feature, source);
    setCommonFeatureParams(feature, source);
  }

  private void processTreeRow(SourceFeature source, FeatureCollector features) {
    var feature = features.line("tree_row");
    var sourceTags = (HashMap<String, String>) source.getTag("source_tags");

    if (sourceTags.get("height") != null) {
      feature.setAttr("height", StreetsUtils.parseMeters(sourceTags.get("height")));
    }

    if (sourceTags.get("min_height") != null) {
      feature.setAttr("minHeight", StreetsUtils.parseMeters(sourceTags.get("min_height")));
    }

    setLeafType(feature, source);
    setSourceIds(feature, source);
    setCommonFeatureParams(feature, source);
  }

  private static void setCommonFeatureParams(FeatureCollector.Feature feature, SourceFeature sourceFeature) {
    feature
      .setZoomRange(14, 14)
      .setPixelToleranceAtAllZooms(0.2)
      .setMinPixelSize(0)
      .setMinPixelSizeAtMaxZoom(0)
      .setBufferPixels(4);
  }

  private static void setSourceIds(FeatureCollector.Feature feature, SourceFeature sourceFeature) {
    var sources = (ArrayList<HashMap<String, String>>) sourceFeature.getTag("sources");

    for (var sourceMap : sources) {
      var dataset = sourceMap.get("dataset");
      var recordId = sourceMap.get("record_id");

      if (dataset.equals("Microsoft ML Buildings")) {
        feature.setAttr("msId", recordId);
      } else if (dataset.equals("OpenStreetMap")) {
        String osmIdWithType = recordId.split("@")[0];
        String osmType = osmIdWithType.substring(0, 1);
        String osmId = osmIdWithType.substring(1);

        feature.setAttr("osmType", osmType);
        feature.setAttr("osmId", Long.parseLong(osmId));
      } else if (dataset.equals("Google Open Buildings")) {
        feature.setAttr("googleId", recordId);
      } else if (dataset.equals("Esri Community Maps")) {
        feature.setAttr("esriId", recordId);
      } else if (dataset.contains("zenodo")) {
        feature.setAttr("zenodoId", recordId);
      } else if (dataset.equals("Instituto Geográfico Nacional (España)s")) {
        feature.setAttr("ignId", recordId);
      } else {
        throw new RuntimeException("Unknown dataset: " + dataset);
      }
    }
  }

  private static void setLeafType(FeatureCollector.Feature feature, SourceFeature sourceFeature) {
    var sourceTags = (HashMap<String, String>) sourceFeature.getTag("source_tags");
    var leafType = sourceTags.get("leaf_type");

    if (leafType != null && leafType.equalsIgnoreCase("needleleaved")) {
      feature.setAttr("leafType", "needleleaved");
    }
  }

  private void setPolygonOMBB(FeatureCollector.Feature feature) {
    Geometry geometry = feature.getGeometry();
    Geometry ombb = MinimumAreaRectangle.getMinimumRectangle(geometry);

    var coords = ombb.getCoordinates();

    if (coords.length != 5) {
      return;
    }

    feature.setAttr("@ombb00", coords[0].x);
    feature.setAttr("@ombb01", coords[0].y);

    feature.setAttr("@ombb10", coords[1].x);
    feature.setAttr("@ombb11", coords[1].y);

    feature.setAttr("@ombb20", coords[2].x);
    feature.setAttr("@ombb21", coords[2].y);

    feature.setAttr("@ombb30", coords[3].x);
    feature.setAttr("@ombb31", coords[3].y);
  }

  @Override
  public List<VectorTile.Feature> postProcessLayerFeatures(String layer, int zoom, List<VectorTile.Feature> items) throws GeometryException {
    if (layer.equals("building")) {
      boolean hasParts = false;

      for (VectorTile.Feature item : items) {
        if ((boolean) item.tags().get("isPart")) {
          hasParts = true;
          break;
        }
      }

      if (hasParts) {
        var parts = new ArrayList<BuildingPartWithEnvelope>();
        var outlines = new ArrayList<BuildingOutlineWithEnvelope>();

        for (VectorTile.Feature item : items) {
          Geometry geometry = item.geometry().decode();
          Envelope bbox = geometry.getEnvelopeInternal();

          boolean isPart = (boolean) item.tags().get("isPart");

          if (isPart) {
            parts.add(new BuildingPartWithEnvelope(item, geometry, bbox));
          } else {
            outlines.add(new BuildingOutlineWithEnvelope(item, geometry, bbox));
          }
        }

        for (BuildingOutlineWithEnvelope outline : outlines) {
          for (BuildingPartWithEnvelope part : parts) {
            if (!outline.envelope.intersects(part.envelope)) {
              continue;
            }

            var intersects = false;

            try {
              intersects = outline.geometry.intersects(part.geometry);
            } catch (Exception e) {
              intersects = true;
            }

            if (intersects) {
              var outlineOsmId = outline.feature.tags().get("osmId");
              var outlineOsmType = outline.feature.tags().get("osmType");

              if (outlineOsmId != null) {
                part.feature.setTag("outlineOsmId", outlineOsmId);
                part.feature.setTag("outlineOsmType", outlineOsmType);
              }

              outline.geometryCopy = geometryDifference(outline.geometryCopy, part.geometry);
            }
          }

          var initialOutlineArea = outline.geometry.getArea();
          var newOutlineArea = outline.geometryCopy.getArea();

          if (newOutlineArea / initialOutlineArea < 0.2) {
            items.remove(outline.feature);
          }
        }

        for (BuildingPartWithEnvelope part : parts) {
          if (!part.envelope.intersects(BuildingPartWithEnvelope.TileBoundsEnvelope)) {
            items.remove(part.feature);
          }
        }
      }

      for (VectorTile.Feature item : items) {
        item.tags().remove("isPart");
      }

      for (Iterator<VectorTile.Feature> it = items.iterator(); it.hasNext(); ) {
        VectorTile.Feature item = it.next();

        Geometry geometry = item.geometry().decode();
        Envelope bbox = geometry.getEnvelopeInternal();

        if (!bbox.intersects(ShadowmapOvertureProfile.BuildingPartWithEnvelope.TileBoundsEnvelope)) {
          it.remove();
        }
      }
    }

    return items;
  }

  private Geometry geometryDifference(Geometry geometry1, Geometry geometry2) {
    if (geometry1 instanceof GeometryCollection collection) {
      Geometry[] outGeometries = new Geometry[collection.getNumGeometries()];

      for (int i = 0; i < collection.getNumGeometries(); i++) {
        outGeometries[i] = geometryDifference(collection.getGeometryN(i), geometry2);
      }

      return collection.getFactory().createGeometryCollection(outGeometries);
    } else {
      try {
        return geometry1.difference(geometry2);
      } catch (Exception e) {
        return geometry1;
      }
    }
  }

  @Override
  public String name() {
    return "Overture";
  }

  @Override
  public String description() {
    return "A basemap generated from Overture data";
  }

  @Override
  public String attribution() {
    return """
      <a href="https://www.openstreetmap.org/copyright" target="_blank">&copy; OpenStreetMap</a>
      <a href="https://docs.overturemaps.org/attribution" target="_blank">&copy; Overture Maps Foundation</a>
      """
      .replace("\n", " ")
      .trim();
  }

  public static void main(String[] args) throws Exception {
    run(Arguments.fromArgsOrConfigFile(args));
  }

  static void run(Arguments args) throws Exception {
    Path base = args.inputFile("base", "overture base directory", Path.of("data", "sources", "overture"));

    Planetiler.create(args)
      .setProfile(new ShadowmapOvertureProfile())
      .addParquetSource("overture-buildings",
        Glob.of(base).resolve("**", "*.parquet").find(),
        true, // hive-partitioning
        fields -> fields.get("id"), // hash the ID field to generate unique long IDs
        fields -> fields.get("type")) // extract "type={}" from the filename to get layer
      .overwriteOutput(Path.of("data", "overture.pmtiles"))
      .run();
  }

  private static class BuildingPartWithEnvelope {
    static final Envelope TileBoundsEnvelope = new Envelope(-4, 260, -4, 260);

    VectorTile.Feature feature;
    Geometry geometry;
    Envelope envelope;

    public BuildingPartWithEnvelope(VectorTile.Feature feature, Geometry geometry, Envelope envelope) {
      this.feature = feature;
      this.geometry = geometry;
      this.envelope = envelope;
    }
  }

  private static class BuildingOutlineWithEnvelope {
    VectorTile.Feature feature;
    Geometry geometry;
    Geometry geometryCopy;
    Envelope envelope;

    public BuildingOutlineWithEnvelope(VectorTile.Feature feature, Geometry geometry, Envelope envelope) {
      this.feature = feature;
      this.geometry = geometry;
      this.envelope = envelope;
      this.geometryCopy = geometry.copy();
    }
  }
}
