package io.codehunters.geo.polygon;

import io.codehunters.geo.GeoPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A polygon on (longitude, latitude) treated as a plane, with optional holes. Immutable.
 *
 * <p>Semantics:
 *
 * <ul>
 *   <li>A point on any edge or vertex, of the outline or of a hole, is inside: a zone owns its
 *       border.
 *   <li>A point strictly inside a hole is outside.
 *   <li>Edges are straight lines in degrees, not great circles. That is accurate for city- or
 *       region-sized zones; continent-sized zones drift from the geodesic outline.
 *   <li>No edge may span more than 180 degrees of longitude: a zone across the antimeridian must be
 *       split into two zones at longitude 180.
 * </ul>
 */
public final class Polygon {

  private static final double EPSILON = 1e-12;

  private final Ring shell;
  private final List<Ring> holes;

  private Polygon(Ring shell, List<Ring> holes) {
    this.shell = shell;
    this.holes = List.copyOf(holes);
  }

  /**
   * Builds a polygon without holes. The ring may repeat its first vertex at the end.
   *
   * @param shell the outline, at least 3 distinct vertices
   * @return the polygon
   * @throws IllegalArgumentException if the ring is null, too small or crosses the antimeridian
   */
  public static Polygon of(List<GeoPoint> shell) {
    return new Polygon(Ring.of(shell), List.of());
  }

  /**
   * Returns a copy with one more hole.
   *
   * @param hole the hole's outline, at least 3 distinct vertices
   * @return the new polygon
   * @throws IllegalArgumentException if the ring is null, too small or crosses the antimeridian
   */
  public Polygon withHole(List<GeoPoint> hole) {
    List<Ring> all = new ArrayList<>(holes);
    all.add(Ring.of(hole));
    return new Polygon(shell, all);
  }

  /**
   * Tells whether the point is inside, counting the border as inside.
   *
   * @param point the point
   * @return {@code true} when inside the outline and not strictly inside a hole
   */
  public boolean contains(GeoPoint point) {
    double x = point.longitude().value();
    double y = point.latitude().value();
    if (!shell.coversOrTouches(x, y)) {
      return false;
    }
    return holes.stream().noneMatch(hole -> hole.strictlyContains(x, y));
  }

  private record Ring(
      double[] xs, double[] ys, double minX, double maxX, double minY, double maxY) {

    static Ring of(List<GeoPoint> points) {
      if (points == null || points.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException(
            "A polygon ring must not be null or contain null vertices");
      }
      List<GeoPoint> open = withoutClosingVertex(points);
      if (open.stream().distinct().count() < 3) {
        throw new IllegalArgumentException("A polygon ring needs at least 3 distinct vertices");
      }
      double[] xs = coordinates(open, true);
      double[] ys = coordinates(open, false);
      Ring ring =
          new Ring(
              xs,
              ys,
              Arrays.stream(xs).min().orElseThrow(),
              Arrays.stream(xs).max().orElseThrow(),
              Arrays.stream(ys).min().orElseThrow(),
              Arrays.stream(ys).max().orElseThrow());
      ring.rejectAntimeridianEdges();
      ring.rejectZeroArea();
      return ring;
    }

    private static List<GeoPoint> withoutClosingVertex(List<GeoPoint> points) {
      boolean closed = points.size() > 1 && points.getFirst().equals(points.getLast());
      return closed ? points.subList(0, points.size() - 1) : points;
    }

    private static double[] coordinates(List<GeoPoint> points, boolean longitude) {
      return points.stream()
          .mapToDouble(p -> longitude ? p.longitude().value() : p.latitude().value())
          .toArray();
    }

    private void rejectAntimeridianEdges() {
      for (int i = 0; i < xs.length; i++) {
        if (Math.abs(xs[i] - xs[next(i)]) > 180.0) {
          throw new IllegalArgumentException(
              "A polygon edge must not cross the antimeridian; split the zone at longitude 180");
        }
      }
    }

    private void rejectZeroArea() {
      double twiceArea = 0;
      for (int i = 0; i < xs.length; i++) {
        twiceArea += xs[i] * ys[next(i)] - xs[next(i)] * ys[i];
      }
      if (Math.abs(twiceArea) <= EPSILON) {
        throw new IllegalArgumentException("A polygon ring must enclose an area");
      }
    }

    boolean coversOrTouches(double x, double y) {
      return inBoundingBox(x, y) && (touches(x, y) || crossings(x, y));
    }

    boolean strictlyContains(double x, double y) {
      return inBoundingBox(x, y) && !touches(x, y) && crossings(x, y);
    }

    /** Cheap rejection before walking the edges. */
    private boolean inBoundingBox(double x, double y) {
      return x >= minX - EPSILON
          && x <= maxX + EPSILON
          && y >= minY - EPSILON
          && y <= maxY + EPSILON;
    }

    private boolean touches(double x, double y) {
      for (int i = 0; i < xs.length; i++) {
        if (onSegment(x, y, i, next(i))) {
          return true;
        }
      }
      return false;
    }

    /** Even-odd ray casting towards increasing longitude. */
    private boolean crossings(double x, double y) {
      boolean inside = false;
      for (int i = 0; i < xs.length; i++) {
        int j = next(i);
        if ((ys[i] > y) != (ys[j] > y)
            && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
          inside = !inside;
        }
      }
      return inside;
    }

    private boolean onSegment(double x, double y, int i, int j) {
      double cross = (xs[j] - xs[i]) * (y - ys[i]) - (ys[j] - ys[i]) * (x - xs[i]);
      return Math.abs(cross) <= EPSILON
          && x >= Math.min(xs[i], xs[j]) - EPSILON
          && x <= Math.max(xs[i], xs[j]) + EPSILON
          && y >= Math.min(ys[i], ys[j]) - EPSILON
          && y <= Math.max(ys[i], ys[j]) + EPSILON;
    }

    private int next(int i) {
      return (i + 1) % xs.length;
    }
  }
}
