package com.onthegomap.planetiler.geo;

import static com.onthegomap.planetiler.config.PlanetilerConfig.MAX_MAXZOOM;

import com.onthegomap.planetiler.util.Format;
import com.onthegomap.planetiler.util.Hilbert;
import java.util.regex.Pattern;
import javax.annotation.concurrent.Immutable;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Envelope;

/**
 * The coordinate of a <a href="https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames">slippy map tile</a>.
 * <p>
 * Tile coords are sorted by consecutive Z levels in ascending order: 0 coords for z=0, 4 coords for z=1, etc. The
 * default is TMS order: a level is sorted by x ascending, y descending to match the ordering of the MBTiles sqlite
 * index.
 * <p>
 *
 * @param encoded the tile ID encoded as a 32-bit integer
 * @param x       x coordinate of the tile where 0 is the western-most tile just to the east the international date line
 *                and 2^z-1 is the eastern-most tile
 * @param y       y coordinate of the tile where 0 is the northern-most tile and 2^z-1 is the southern-most tile
 * @param z       zoom level ({@code <= 15})
 */
@Immutable
public record TileCoord(int encoded, int x, int y, int z) implements Comparable<TileCoord> {

  public static void main(String[] args) {


    insertLinks("""
      |    tile    | inbytes | infeatures | outbytes | time  |
      |------------|---------|------------|----------|-------|
      | 6/52/24    | 316,843  | 7,592       | 118,047   | 0.739 |
      | 6/50/26    | 263,488  | 5,587       | 101,577   | 0.668 |
      | 6/52/26    | 332,374  | 6,722       | 138,996   | 0.667 |
      | 10/308/381 | 604,776  | 6,533       | 362,984   | 0.662 |
      | 6/45/25    | 147,850  | 2,085       | 77,298    | 0.634 |
      | 6/51/26    | 221,077  | 4,312       | 89,517    | 0.63  |
      | 6/51/27    | 264,509  | 5,301       | 102,592   | 0.617 |
      | 6/15/22    | 124,147  | 1,480       | 70,605    | 0.597 |
      | 6/52/27    | 310,144  | 6,443       | 122,242   | 0.593 |
      | 1/0/0      | 149,781  | 3,906       | 63,429    | 0.586 |
      """);
    insertLinks("""
      |     tile     | inbytes | infeatures | outbytes |  time  |
      |--------------|---------|------------|----------|--------|
      | 7/68/33      |  574,106 |       3,310 |   253,432 |  48.02 |
      | 13/6527/4239 | 2,792,615 |     106,023 |   135,957 | 35.365 |
      | 13/6661/4261 | 3,111,509 |     114,647 |   130,210 |  32.91 |
      | 13/6661/4262 | 3,046,934 |     111,292 |   126,727 | 32.741 |
      | 13/6527/4240 | 2,344,566 |      88,393 |   116,492 | 29.108 |
      | 13/6527/4238 | 2,900,531 |     105,839 |   143,392 | 28.857 |
      | 13/6526/4236 | 3,025,297 |     106,137 |   137,592 | 28.829 |
      | 7/68/61      |  731,969 |       5,077 |   268,917 | 28.583 |
      | 13/6527/4237 | 2,843,197 |      99,475 |   144,424 | 28.142 |
      | 13/6607/4274 | 2,228,848 |      90,111 |    92,170 | 27.981 |
      """);
    insertLinks("""
      |     tile     | inbytes | infeatures | outbytes |  time  |
      |--------------|---------|------------|----------|--------|
      | 10/236/413   | 6,676,345 |     179,762 |   17,9050 |  16.77 |
      | 13/3037/4648 | 2,991,716 |     116,502 |   13,0376 | 13.434 |
      | 7/68/33      |  574,320 |       3,311 |   25,3391 | 11.826 |
      | 13/1436/3313 | 2,554,569 |     104,277 |   12,5866 | 11.517 |
      | 13/3038/4646 | 2,664,982 |     103,904 |   13,4165 |  11.47 |
      | 13/3037/4647 | 2,579,326 |      99,017 |   13,9578 |   11.2 |
      | 13/6527/4239 | 2,792,855 |     106,027 |   13,6069 |  11.08 |
      | 13/3036/4648 | 2,553,943 |      96,097 |   13,6788 | 11.064 |
      | 13/3039/4646 | 2,629,057 |     104,042 |   13,3678 |  11.02 |
      | 13/6661/4261 | 3,111,945 |     114,652 |   13,0269 | 10.987 |
      """);
  }

  private static void insertLinks(String input) {
    var matcher = Pattern.compile("([0-9]+)/([0-9]+)/([0-9]+)").matcher(input);
    System.out.println(matcher.replaceAll(matchResult -> {
      int z = Integer.parseInt(matchResult.group(1));
      int x = Integer.parseInt(matchResult.group(2));
      int y = Integer.parseInt(matchResult.group(3));
      String url = TileCoord.ofXYZ(x, y, z).getDebugUrl();
      return "[" + z + "/" + x + "/" + y + "](" + url + ")";
    }));
  }

  private static final int[] ZOOM_START_INDEX = new int[MAX_MAXZOOM + 1];

  static {
    int idx = 0;
    for (int z = 0; z <= MAX_MAXZOOM; z++) {
      ZOOM_START_INDEX[z] = idx;
      int count = (1 << z) * (1 << z);
      if (Integer.MAX_VALUE - idx < count) {
        throw new IllegalStateException("Too many zoom levels " + MAX_MAXZOOM);
      }
      idx += count;
    }
  }

  private static int startIndexForZoom(int z) {
    return ZOOM_START_INDEX[z];
  }

  private static int zoomForIndex(int idx) {
    for (int z = MAX_MAXZOOM; z >= 0; z--) {
      if (ZOOM_START_INDEX[z] <= idx) {
        return z;
      }
    }
    throw new IllegalArgumentException("Bad index: " + idx);
  }

  public TileCoord {
    assert z <= MAX_MAXZOOM;
  }

  public static TileCoord ofXYZ(int x, int y, int z) {
    return new TileCoord(encode(x, y, z), x, y, z);
  }

  public static TileCoord decode(int encoded) {
    int z = zoomForIndex(encoded);
    long xy = tmsPositionToXY(z, encoded - startIndexForZoom(z));
    return new TileCoord(encoded, (int) (xy >>> 32 & 0xFFFFFFFFL), (int) (xy & 0xFFFFFFFFL), z);
  }

  /** Decode an integer using Hilbert ordering on a zoom level back to TMS ordering. */
  public static TileCoord hilbertDecode(int encoded) {
    int z = TileCoord.zoomForIndex(encoded);
    long xy = Hilbert.hilbertPositionToXY(z, encoded - TileCoord.startIndexForZoom(z));
    return TileCoord.ofXYZ(Hilbert.extractX(xy), Hilbert.extractY(xy), z);
  }

  /** Returns the tile containing a latitude/longitude coordinate at a given zoom level. */
  public static TileCoord aroundLngLat(double lng, double lat, int zoom) {
    double factor = 1 << zoom;
    double x = GeoUtils.getWorldX(lng) * factor;
    double y = GeoUtils.getWorldY(lat) * factor;
    return TileCoord.ofXYZ((int) Math.floor(x), (int) Math.floor(y), zoom);
  }

  public static int encode(int x, int y, int z) {
    return startIndexForZoom(z) + tmsXYToPosition(z, x, y);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TileCoord tileCoord = (TileCoord) o;

    return encoded == tileCoord.encoded;
  }

  @Override
  public int hashCode() {
    return encoded;
  }

  @Override
  public String toString() {
    return "{x=" + x + " y=" + y + " z=" + z + '}';
  }

  public double progressOnLevel(TileExtents extents) {
    // approximate percent complete within a bounding box by computing what % of the way through the columns we are
    var zoomBounds = extents.getForZoom(z);
    return 1d * (x - zoomBounds.minX()) / (zoomBounds.maxX() - zoomBounds.minX());
  }

  public double hilbertProgressOnLevel(TileExtents extents) {
    return 1d * Hilbert.hilbertXYToIndex(this.z, this.x, this.y) / (1 << 2 * this.z);
  }

  @Override
  public int compareTo(TileCoord o) {
    return Long.compare(encoded, o.encoded);
  }

  /** Returns the latitude/longitude of the northwest corner of this tile. */
  public Coordinate getLatLon() {
    double worldWidthAtZoom = Math.pow(2, z);
    return new CoordinateXY(
      GeoUtils.getWorldLon(x / worldWidthAtZoom),
      GeoUtils.getWorldLat(y / worldWidthAtZoom)
    );
  }


  /** Returns a URL that displays the openstreetmap data for this tile. */
  public String getDebugUrl() {
    Coordinate coord = getLatLon();
    return Format.osmDebugUrl(z, coord);
  }

  /** Returns the pixel coordinate on this tile of a given latitude/longitude (assuming 256x256 px tiles). */
  public Coordinate lngLatToTileCoords(double lng, double lat) {
    double factor = 1 << z;
    double x = GeoUtils.getWorldX(lng) * factor;
    double y = GeoUtils.getWorldY(lat) * factor;
    return new CoordinateXY((x - Math.floor(x)) * 256, (y - Math.floor(y)) * 256);
  }

  /** Return the equivalent tile index using Hilbert ordering on a single level instead of TMS. */
  public int hilbertEncoded() {
    return startIndexForZoom(this.z) +
      Hilbert.hilbertXYToIndex(this.z, this.x, this.y);
  }

  public static long tmsPositionToXY(int z, int pos) {
    if (z == 0)
      return 0;
    int dim = 1 << z;
    int x = pos / dim;
    int y = dim - 1 - (pos % dim);
    return ((long) x << 32) | y;
  }

  public static int tmsXYToPosition(int z, int x, int y) {
    int dim = 1 << z;
    return x * dim + (dim - 1 - y);
  }

  public TileCoord parent() {
    return ofXYZ(x / 2, y / 2, z - 1);
  }

  public Envelope bounds() {
    double worldWidthAtZoom = Math.pow(2, z);
    return new Envelope(
      GeoUtils.getWorldLon(x / worldWidthAtZoom),
      GeoUtils.getWorldLon((x + 1) / worldWidthAtZoom),
      GeoUtils.getWorldLat(y / worldWidthAtZoom),
      GeoUtils.getWorldLat((y + 1) / worldWidthAtZoom)
    );
  }
}
