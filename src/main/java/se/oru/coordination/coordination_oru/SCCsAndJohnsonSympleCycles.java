package se.oru.coordination.coordination_oru;

/*
 * (C) Copyright 2013-2017, by Nikolay Ognyanov and Contributors.
 *
 * JGraphT : a free Java graph-theory library
 *
 * This program and the accompanying materials are dual-licensed under
 * either
 *
 * (a) the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation, or (at your option) any
 * later version.
 *
 * or (per the licensee's choosing)
 *
 * (b) the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation.
 */

import java.util.*;

import org.jgrapht.*;
import org.jgrapht.alg.cycle.*;
import org.jgrapht.graph.*;

/**
 * Find all simple cycles of a directed graph using the Johnson's algorithm.
 *
 * <p>
 * See:<br>
 * D.B.Johnson, Finding all the elementary circuits of a directed graph, SIAM J. Comput., 4 (1975),
 * pp. 77-84.
 *
 * @param <V> the vertex type.
 * @param <E> the edge type.
 *
 * @author Nikolay Ognyanov
 */

public class SCCsAndJohnsonSympleCycles<V, E>
    implements DirectedSimpleCycles<V, E>
{
    // The graph.
    private DirectedGraph<V, E> graph;

    // The main state of the algorithm.
    private List<List<V>> cycles = null;
    private V[] iToV = null;
    private Map<V, Integer> vToI = null;
    private Set<V> blocked = null;
    private Map<V, Set<V>> bSets = null;
    private ArrayDeque<V> stack = null;

    // The state of the embedded Tarjan SCC algorithm.
    private List<Set<V>> SCCs = null;
    private int index = 0;
    
    //Indices of vertices already visited and "time" they have been visited.
    private Map<V, Integer> vIndex = null;
    
    //The low-link value of a node is the smallest [lower] 
    //node id reachable from that node when doing the DFS (including itself).
    //All the nodes which have the same low link value belong to the same strongly connected component.
    //Depending where the DFS starts and which edges are visited the low link value could be wrong ...
    private Map<V, Integer> vLowlink = null;
    
    //... To avoid this problem, the Tarjan's algorithm maintains use a stack of node.
    //Nodes are added to the stack as they are explored for the first time. 
    //Moreover, nodes are removed from the stack each time a complete SCC is found.
    private ArrayDeque<V> path = null;
    
    //If u and v are node in the stack and we are exploring u, then the update condition is:
    //u's lowlink value should be updated with v's lowlink value if
    //1. u's lowlink value > v's lowlink value;
    //2. there is a path from u to v;
    //3. v is on the stack.
    private Set<V> pathSet = null;

    /**
     * Create a simple cycle finder with an unspecified graph.
     */
    public SCCsAndJohnsonSympleCycles()
    {
    }

    /**
     * Create a simple cycle finder for the specified graph.
     *
     * @param graph - the DirectedGraph in which to find cycles.
     *
     * @throws IllegalArgumentException if the graph argument is <code>
     * null</code>.
     */
    public SCCsAndJohnsonSympleCycles(DirectedGraph<V, E> graph)
    {
        if (graph == null) {
            throw new IllegalArgumentException("Null graph argument.");
        }
        this.graph = graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DirectedGraph<V, E> getGraph()
    {
        return graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGraph(DirectedGraph<V, E> graph)
    {
        if (graph == null) {
            throw new IllegalArgumentException("Null graph argument.");
        }
        this.graph = graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<List<V>> findSimpleCycles()
    {
        if (graph == null) {
            throw new IllegalArgumentException("Null graph.");
        }
        initState();

        int startIndex = 0;
        int size = graph.vertexSet().size();
        while (startIndex < size) {
            Object[] minSCCGResult = findMinSCSG(startIndex);
            if (minSCCGResult[0] != null) {
                startIndex = (Integer) minSCCGResult[1];
                @SuppressWarnings("unchecked") DirectedGraph<V, E> scg =
                    (DirectedGraph<V, E>) minSCCGResult[0];
                V startV = toV(startIndex);
                for (E e : scg.outgoingEdgesOf(startV)) {
                    V v = graph.getEdgeTarget(e);
                    blocked.remove(v);
                    getBSet(v).clear();
                }
                findCyclesInSCG(startIndex, startIndex, scg);
                startIndex++;
            } else {
                break;
            }
        }

        List<List<V>> result = cycles;
        clearState();
        return result;
    }
    
    private Object[] findMinSCSG(int startIndex)
    {
        // Per Johnson : "adjacency structure of strong
        // component K with least vertex in subgraph of
        // G induced by {s, s+ 1, n}".
        // Or in contemporary terms: the strongly connected
        // component of the subgraph induced by {v1,...,vn}
        // which contains the minimum (among those SCCs)
        // vertex index. We return that index together with
        // the graph.
        Object[] result = new Object[2];
        initMinSCGState();
        
        List<Set<V>> SCCs = findSCCS(startIndex);
        System.out.println("Strongly connected components: " + SCCs.toString());

        // find the SCC with the minimum index
        int minIndexFound = Integer.MAX_VALUE;
        Set<V> minSCC = null;
        for (Set<V> scc : SCCs) {
            for (V v : scc) {
                int t = toI(v);
                if (t < minIndexFound) {
                    minIndexFound = t;
                    minSCC = scc;
                }
            }
        }
        if (minSCC == null) {
            return result;
        }

        // build a graph for the SCC found
        @SuppressWarnings("unchecked") DirectedGraph<V, E> resultGraph = new DefaultDirectedGraph<V,E>(
            new ClassBasedEdgeFactory<V,E>((Class<? extends E>) DefaultEdge.class));
        resultGraph = SCCToSubGraph(minSCC);

        // It is ugly to return results in an array
        // of Object but the idea is to restrict
        // dependencies to JgraphT only and there is
        // no utility pair container in JgraphT.
        result[0] = resultGraph;
        result[1] = minIndexFound;
        
        clearMinSCCState();
        return result;
    }

    public List<Set<V>> findAllSCCS() {
        if (graph == null) {
            throw new IllegalArgumentException("Null graph.");
        }
        initState();
    	initMinSCGState();
        
        List<Set<V>> SCCs = findSCCS(0);
        //System.out.println("Strongly connected components: " + SCCs.toString());
        
        clearMinSCCState();
        clearState();
        return SCCs;
    }
    
    private List<Set<V>> findSCCS(int startIndex)
    {        
        // Find SCCs in the subgraph induced
        // by vertices startIndex and beyond.
        // A call to StrongConnectivityAlgorithm
        // would be too expensive because of the
        // need to materialize the subgraph.
        // So - do a local search by the Tarjan's
        // algorithm and pretend that vertices
        // with an index smaller than startIndex
        // do not exist.
    	
    	//pick a random node and include it in the computation just if 
    	//it is greater than the start vertex.
        for (V v : graph.vertexSet()) {
            int vI = toI(v);
            if (vI < startIndex) {
                continue;
            }
            
            //if it was not already visited by a DFS search,
            //start the DFS search from it.
            if (!vIndex.containsKey(v)) {
                getSCCs(startIndex, vI);
            }
        }
        List<Set<V>> result = SCCs;
        SCCs = null;
        return result;
    }

    private void getSCCs(int startIndex, int vertexIndex)
    {   	
        V vertex = toV(vertexIndex);
        vIndex.put(vertex, index);
        vLowlink.put(vertex, index);
        index++;
        path.push(vertex);
        pathSet.add(vertex);

        //Start a DFS search ...
        Set<E> edges = graph.outgoingEdgesOf(vertex);
        for (E e : edges) {
            V successor = graph.getEdgeTarget(e);
            int successorIndex = toI(successor);
            if (successorIndex < startIndex) {
                continue;
            }
            if (!vIndex.containsKey(successor)) {	//not yet explored. Explore the vertex.
                getSCCs(startIndex, successorIndex);
                vLowlink.put(vertex, Math.min(vLowlink.get(vertex), vLowlink.get(successor)));
            } else if (pathSet.contains(successor)) { //already explored. Update the vertex low link
                vLowlink.put(vertex, Math.min(vLowlink.get(vertex), vIndex.get(successor)));
            }
        }
        if (vLowlink.get(vertex).equals(vIndex.get(vertex))) {
            Set<V> result = new HashSet<V>();
            V temp;
            do {
                temp = path.pop();
                pathSet.remove(temp);
                result.add(temp);
            } while (!vertex.equals(temp));
            if (result.size() == 1) {
                V v = result.iterator().next();
                if (graph.containsEdge(vertex, v)) {
                    SCCs.add(result);
                }
            } else {
                SCCs.add(result);
            }
        }
    }

    private boolean findCyclesInSCG(int startIndex, int vertexIndex, DirectedGraph<V, E> scg)
    {
        // Find cycles in a strongly connected graph
        // per Johnson.
        boolean foundCycle = false;
        V vertex = toV(vertexIndex);
        stack.push(vertex);
        blocked.add(vertex);

        for (E e : scg.outgoingEdgesOf(vertex)) {
            V successor = scg.getEdgeTarget(e);
            int successorIndex = toI(successor);
            if (successorIndex == startIndex) {
                List<V> cycle = new ArrayList<V>();
                cycle.addAll(stack);
                cycles.add(cycle);
                foundCycle = true;
            } else if (!blocked.contains(successor)) {
                boolean gotCycle = findCyclesInSCG(startIndex, successorIndex, scg);
                foundCycle = foundCycle || gotCycle;
            }
        }
        if (foundCycle) {
            unblock(vertex);
        } else {
            for (E ew : scg.outgoingEdgesOf(vertex)) {
                V w = scg.getEdgeTarget(ew);
                Set<V> bSet = getBSet(w);
                bSet.add(vertex);
            }
        }
        stack.pop();
        return foundCycle;
    }
    
    public List<List<V>> getCyclesInSCG(V vertex, DirectedGraph<V, E> scg)
    {
    	initState();
    	
        // Find cycles in a strongly connected graph.
        boolean foundCycle = false;
        int startIndex = toI(vertex);
        stack.push(vertex);
        blocked.add(vertex);

        for (E e : scg.outgoingEdgesOf(vertex)) {
            V successor = scg.getEdgeTarget(e);
            int successorIndex = toI(successor);
            if (successorIndex == startIndex) {
                List<V> cycle = new ArrayList<V>();
                cycle.addAll(stack);
                cycles.add(cycle);
                foundCycle = true;
            } else if (!blocked.contains(successor)) {
                boolean gotCycle = findCyclesInSCG(startIndex, successorIndex, scg);
                foundCycle = foundCycle || gotCycle;
            }
        }
        if (foundCycle) {
            unblock(vertex);
        } else {
            for (E ew : scg.outgoingEdgesOf(vertex)) {
                V w = scg.getEdgeTarget(ew);
                Set<V> bSet = getBSet(w);
                bSet.add(vertex);
            }
        }
        stack.pop();
        clearState();
        return cycles;
    }

    private void unblock(V vertex)
    {
        blocked.remove(vertex);
        Set<V> bSet = getBSet(vertex);
        while (bSet.size() > 0) {
            V w = bSet.iterator().next();
            bSet.remove(w);
            if (blocked.contains(w)) {
                unblock(w);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void initState()
    {
        cycles = new LinkedList<List<V>>();
        iToV = (V[]) graph.vertexSet().toArray();
        vToI = new HashMap<V,Integer>();
        blocked = new HashSet<V>();
        bSets = new HashMap<V,Set<V>>();
        stack = new ArrayDeque<V>();

        for (int i = 0; i < iToV.length; i++) {
            vToI.put(iToV[i], i);
        }
    }

    private void clearState()
    {
        cycles = null;
        iToV = null;
        vToI = null;
        blocked = null;
        bSets = null;
        stack = null;
    }

    private void initMinSCGState()
    {
        index = 0;
        SCCs = new ArrayList<Set<V>>();
        vIndex = new HashMap<V,Integer>();
        vLowlink = new HashMap<V,Integer>();
        path = new ArrayDeque<V>();
        pathSet = new HashSet<V>();
    }

    private void clearMinSCCState()
    {
        index = 0;
        SCCs = null;
        vIndex = null;
        vLowlink = null;
        path = null;
        pathSet = null;
    }

    private Integer toI(V vertex)
    {
        return vToI.get(vertex);
    }

    private V toV(Integer i)
    {
        return iToV[i];
    }

    private Set<V> getBSet(V v)
    {
        // B sets typically not all needed,
        // so instantiate lazily.
        Set<V> result = bSets.get(v);
        if (result == null) {
            result = new HashSet<V>();
            bSets.put(v, result);
        }
        return result;
    }
    
    public DirectedGraph<V, E> SCCToSubGraph(Set<V> SCC) {
    	if (graph == null) {
            throw new IllegalArgumentException("Null graph argument.");
        }
    	@SuppressWarnings("unchecked") DirectedGraph<V, E> resultGraph = new DefaultDirectedGraph<V,E>(
                new ClassBasedEdgeFactory<V,E>((Class<? extends E>) DefaultEdge.class));
        for (V v : SCC) {
            resultGraph.addVertex(v);
        }
        for (V v : SCC) {
            for (V w : SCC) {
                if (graph.containsEdge(v, w)) {
                    resultGraph.addEdge(v, w);
                }
            }
        }
        return resultGraph;
    }
}