package com.example.ridealong.domain.algorithm;

import com.example.ridealong.domain.model.GeoCoord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Builds a weighted graph from a ride's route polyline and runs priority-queue Dijkstra to
 * answer the questions RouteMatcher needs:
 *
 * <ul>
 *   <li><b>shared distance:</b> distance travelled along the poster's route between the
 *       passenger's pickup and drop-off (route edges only)</li>
 *   <li><b>detour:</b> extra distance the poster drives to collect and drop the passenger:
 *       d(start→P→D→end) − d(start→end)</li>
 *   <li><b>fare distance:</b> the passenger's full trip P→D in the graph (route + connector edges),
 *       used for proportional fare splitting</li>
 * </ul>
 *
 * Graph: nodes = (densified) route points plus a pickup node P and drop node D. Edges connect
 * consecutive route points (Haversine weight). P and D are joined by <i>connector</i> edges to their
 * k nearest route nodes, which models leaving the route to collect the passenger and rejoining it.
 */
public final class DijkstraRouter {

    /** Result of analysing one passenger against one route. */
    public static final class Analysis {
        public final double baseRouteKm;
        public final double sharedKm;
        public final double connectorKm;
        public final double fareDistanceKm;
        public final double detourKm;
        /** true when the passenger's travel runs against the route direction. */
        public final boolean wrongDirection;
        public final boolean reachable;

        Analysis(double baseRouteKm, double sharedKm, double connectorKm, double fareDistanceKm,
                 double detourKm, boolean wrongDirection, boolean reachable) {
            this.baseRouteKm = baseRouteKm;
            this.sharedKm = sharedKm;
            this.connectorKm = connectorKm;
            this.fareDistanceKm = fareDistanceKm;
            this.detourKm = detourKm;
            this.wrongDirection = wrongDirection;
            this.reachable = reachable;
        }
    }

    /** Adjacency-list weighted undirected graph. */
    public static final class Graph {
        private final List<List<double[]>> adj = new ArrayList<>(); // {to, weight, isConnector(0/1)}

        public int addNode() {
            adj.add(new ArrayList<>());
            return adj.size() - 1;
        }

        public int size() { return adj.size(); }

        public void addEdge(int a, int b, double w, boolean connector) {
            if (w < 0) throw new IllegalArgumentException("negative weight");
            adj.get(a).add(new double[]{b, w, connector ? 1 : 0});
            adj.get(b).add(new double[]{a, w, connector ? 1 : 0});
        }

        List<double[]> edges(int n) { return adj.get(n); }
    }

    /** Single-source shortest paths (dist, prev node, and whether the edge into a node was a connector). */
    public static final class ShortestPaths {
        public final double[] dist;
        final int[] prev;
        final boolean[] viaConnector;
        final double[] prevWeight;

        ShortestPaths(int n) {
            dist = new double[n];
            prev = new int[n];
            viaConnector = new boolean[n];
            prevWeight = new double[n];
            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            Arrays.fill(prev, -1);
        }

        /** Node sequence from the source to target (empty if unreachable). */
        public List<Integer> pathTo(int target) {
            List<Integer> p = new ArrayList<>();
            if (Double.isInfinite(dist[target])) return p;
            for (int at = target; at != -1; at = prev[at]) p.add(at);
            Collections.reverse(p);
            return p;
        }
    }

    static final double CONNECTOR_PENALTY = 1.001;

    private final int connectorsPerEndpoint;
    private final double densifyStepKm;
    private final int maxNodes;

    public DijkstraRouter(int connectorsPerEndpoint, double densifyStepKm, int maxNodes) {
        this.connectorsPerEndpoint = Math.max(1, connectorsPerEndpoint);
        this.densifyStepKm = densifyStepKm;
        this.maxNodes = Math.max(2, maxNodes);
    }

    public DijkstraRouter() { this(3, 0.15, 600); }

    /** Classic O((V+E) log V) Dijkstra with a binary heap and lazy deletion. */
    public static ShortestPaths dijkstra(Graph g, int source) {
        ShortestPaths sp = new ShortestPaths(g.size());
        sp.dist[source] = 0;
        PriorityQueue<double[]> pq = new PriorityQueue<>((x, y) -> Double.compare(x[1], y[1]));
        pq.add(new double[]{source, 0});
        boolean[] done = new boolean[g.size()];
        while (!pq.isEmpty()) {
            double[] cur = pq.poll();
            int u = (int) cur[0];
            if (done[u]) continue;
            done[u] = true;
            for (double[] e : g.edges(u)) {
                int v = (int) e[0];
                double nd = sp.dist[u] + e[1];
                if (nd < sp.dist[v]) {
                    sp.dist[v] = nd;
                    sp.prev[v] = u;
                    sp.prevWeight[v] = e[1];
                    sp.viaConnector[v] = e[2] == 1;
                    pq.add(new double[]{v, nd});
                }
            }
        }
        return sp;
    }

    /**
     * Analyse a passenger trip against a route.
     *
     * @param route  poster's route (start..end). Two points (straight line) is fine; it is densified.
     * @param pickup passenger pickup
     * @param drop   passenger drop-off, or null if the passenger only gave a pickup
     */
    public Analysis analyze(List<GeoCoord> route, GeoCoord pickup, GeoCoord drop) {
        if (route == null || route.size() < 2) throw new IllegalArgumentException("route needs >= 2 points");
        List<GeoCoord> nodes = GeometryUtils.densify(route, densifyStepKm, maxNodes);
        // Insert the perpendicular projections of pickup/drop as exact route nodes so the shared
        // distance is measured from the true snap point, not the nearest densified vertex.
        insertProjection(nodes, pickup);
        if (drop != null) insertProjection(nodes, drop);
        int n = nodes.size();
        Graph g = new Graph();
        for (int i = 0; i < n; i++) g.addNode();
        for (int i = 1; i < n; i++) g.addEdge(i - 1, i, Haversine.distanceKm(nodes.get(i - 1), nodes.get(i)), false);

        int p = g.addNode();
        connect(g, p, pickup, nodes);
        int d = -1;
        if (drop != null) {
            d = g.addNode();
            connect(g, d, drop, nodes);
        }

        int start = 0, end = n - 1;
        ShortestPaths fromStart = dijkstra(g, start);
        double base = fromStart.dist[end];
        ShortestPaths fromP = dijkstra(g, p);

        if (d == -1) {
            double via = fromP.dist[start] + fromP.dist[end];
            boolean ok = !Double.isInfinite(via);
            return new Analysis(base, 0, 0, 0, ok ? Math.max(0, via - base) : Double.POSITIVE_INFINITY, false, ok);
        }

        ShortestPaths fromD = dijkstra(g, d);
        double via = fromP.dist[start] + fromP.dist[d] + fromD.dist[end];
        if (Double.isInfinite(via)) {
            return new Analysis(base, 0, 0, 0, Double.POSITIVE_INFINITY, false, false);
        }

        // Walk the P→D path to split route vs connector distance and find the route entry/exit nodes.
        List<Integer> path = fromP.pathTo(d);
        double shared = 0, connector = 0;
        int firstRouteNode = -1, lastRouteNode = -1;
        for (int i = 1; i < path.size(); i++) {
            int v = path.get(i);
            if (fromP.viaConnector[v]) connector += fromP.prevWeight[v];
            else shared += fromP.prevWeight[v];
            if (v < n) {
                if (firstRouteNode == -1) firstRouteNode = v;
                lastRouteNode = v;
            }
        }
        boolean wrongDirection = firstRouteNode != -1 && lastRouteNode < firstRouteNode;
        return new Analysis(base, shared, connector, shared + connector, Math.max(0, via - base),
                wrongDirection, true);
    }

    private static void insertProjection(List<GeoCoord> nodes, GeoCoord point) {
        GeometryUtils.Projection pr = GeometryUtils.projectOnPolyline(point, nodes);
        if (pr == null) return;
        GeoCoord a = nodes.get(pr.segmentIndex);
        GeoCoord b = nodes.get(Math.min(pr.segmentIndex + 1, nodes.size() - 1));
        if (Haversine.distanceKm(pr.closest, a) < 1e-4 || Haversine.distanceKm(pr.closest, b) < 1e-4) return;
        nodes.add(pr.segmentIndex + 1, pr.closest);
    }

    private void connect(Graph g, int extraNode, GeoCoord point, List<GeoCoord> nodes) {
        int k = Math.min(connectorsPerEndpoint, nodes.size());
        double[] dists = new double[nodes.size()];
        Integer[] idx = new Integer[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            dists[i] = Haversine.distanceKm(point, nodes.get(i));
            idx[i] = i;
        }
        Arrays.sort(idx, (a, b) -> Double.compare(dists[a], dists[b]));
        // A 0.1% penalty breaks ties in favour of travelling along the route (straight-line
        // connectors and route edges can otherwise give equal-length but differently-split paths).
        for (int i = 0; i < k; i++) {
            g.addEdge(extraNode, idx[i], dists[idx[i]] * CONNECTOR_PENALTY, true);
        }
    }
}
