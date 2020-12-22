package it.unive.lisa.util.datastructures.graph;

import it.unive.lisa.analysis.AnalysisState;
import it.unive.lisa.analysis.FunctionalLattice;
import it.unive.lisa.analysis.HeapDomain;
import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.ValueDomain;
import it.unive.lisa.callgraph.CallGraph;
import it.unive.lisa.util.workset.WorkingSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A generic graph, backed by an {@link AdjacencyMatrix}, over which a fixpoint
 * can be computed.
 * 
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 * 
 * @param <N> the type of the nodes in this graph
 * @param <E> the type of the edges in this graph
 */
public abstract class FixpointGraph<N extends Node<N>, E extends SemanticEdge<N, E>> extends Graph<N, E> {
	private static final Logger log = LogManager.getLogger(FixpointGraph.class);

	/**
	 * The default number of fixpoint iteration on a given node after which
	 * calls to {@link Lattice#lub(Lattice)} gets replaced with
	 * {@link Lattice#widening(Lattice)}.
	 */
	public static final int DEFAULT_WIDENING_THRESHOLD = 5;

	/**
	 * Builds the graph.
	 */
	protected FixpointGraph() {
		super();
	}

	/**
	 * Builds the graph.
	 * 
	 * @param entrypoints     the nodes of this graph that will be reachable
	 *                            from other graphs
	 * @param adjacencyMatrix the matrix containing all the nodes and the edges
	 *                            that will be part of this graph
	 */
	protected FixpointGraph(Collection<N> entrypoints, AdjacencyMatrix<N, E> adjacencyMatrix) {
		super(entrypoints, adjacencyMatrix);
	}

	/**
	 * Clones the given graph.
	 * 
	 * @param other the original graph
	 */
	protected FixpointGraph(FixpointGraph<N, E> other) {
		super(other);
	}

	/**
	 * A functional interface that can be used for compute the semantics of
	 * {@link Node}s, producing {@link AnalysisState}s.
	 * 
	 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
	 * 
	 * @param <N> the type of the nodes of the graph, where the semantic
	 *                computation will happen
	 * @param <H> the concrete type of {@link HeapDomain} embedded in the
	 *                analysis states
	 * @param <V> the concrete type of {@link ValueDomain} embedded in the
	 *                analysis states
	 * @param <F> the concrete type of {@link FunctionalLattice} where results
	 *                on internal nodes will be stored
	 */
	@FunctionalInterface
	public interface SemanticFunction<N extends Node<N>, H extends HeapDomain<H>, V extends ValueDomain<V>, F extends FunctionalLattice<F, N, AnalysisState<H, V>>> {

		/**
		 * Computes the semantics of the given {@link Node} {@code node},
		 * assuming that the entry state is {@code entryState}. The results of
		 * the semantic computations on inner {@link Node}s must be saved inside
		 * {@code expressions}. If the computation needs information regarding
		 * the other {@link FixpointGraph}s, {@code callGraph} can be queried.
		 * 
		 * @param node        the node whose semantics needs to be evaluated
		 * @param entryState  the entry state for the computation
		 * @param callGraph   the call graph that can be used to obtain semantic
		 *                        information on other graphs
		 * @param expressions the store where semantics results of inner
		 *                        expressions must be stored
		 * 
		 * @return the abstract analysis state after the execution of the given
		 *             node
		 * 
		 * @throws SemanticException if something goes wrong during the
		 *                               computation
		 */
		AnalysisState<H, V> compute(N node, AnalysisState<H, V> entryState, CallGraph callGraph,
				F expressions) throws SemanticException;
	}

	/**
	 * Computes a fixpoint over this graph. This method returns a
	 * {@code Map<N, AnalysisState<H, V>>} instance mapping each {@link Node} to
	 * the {@link AnalysisState} computed by this method. Note that the returned
	 * map has entries also for inner nodes. The computation uses
	 * {@link Lattice#lub(Lattice)} to compose results obtained at different
	 * iterations, up to {@code widenAfter * predecessors_number} times, where
	 * {@code predecessors_number} is the number of expressions that are
	 * predecessors of the one being processed. After overcoming that threshold,
	 * {@link Lattice#widening(Lattice)} is used. The computation starts at the
	 * nodes in {@code startingPoints}, using as its entry state their
	 * respective value. {@code cg} will be invoked to get the approximation of
	 * all invoked graphs, while {@code ws} is used as working set for the nodes
	 * to process.
	 * 
	 * @param <H>            the type of {@link HeapDomain} contained into the
	 *                           computed abstract state
	 * @param <V>            the type of {@link ValueDomain} contained into the
	 *                           computed abstract state
	 * @param <F>            the type of {@link FunctionalLattice} that will
	 *                           hold analysis states computed on intermediate
	 *                           nodes
	 * @param startingPoints a map between {@link Node}s that to use as a
	 *                           starting point of the computation (that must be
	 *                           nodes of this graph) and the entry states to
	 *                           apply on it
	 * @param cg             the callgraph that can be queried when a call
	 *                           towards an other graph is encountered
	 * @param ws             the {@link WorkingSet} instance to use for this
	 *                           computation
	 * @param widenAfter     the number of times after which the
	 *                           {@link Lattice#lub(Lattice)} invocation gets
	 *                           replaced by the
	 *                           {@link Lattice#widening(Lattice)} call. Use
	 *                           {@code 0} to <b>always</b> use
	 *                           {@link Lattice#lub(Lattice)}
	 * @param semantics      the {@link SemanticFunction} that will be used for
	 *                           computing the abstract post-state of nodes
	 * 
	 * @return a map that stores for each {@link Node} the result of the
	 *             fixpoint computation
	 * 
	 * @throws FixpointException if an error occurs during the semantic
	 *                               computation of a node, or if some
	 *                               unknown/invalid node ends up in the working
	 *                               set
	 */
	@SuppressWarnings("unchecked")
	protected <H extends HeapDomain<H>, V extends ValueDomain<V>, F extends FunctionalLattice<F, N, AnalysisState<H, V>>> Map<N, AnalysisState<H, V>> fixpoint(
			Map<N, AnalysisState<H, V>> startingPoints, CallGraph cg, WorkingSet<N> ws, int widenAfter,
			SemanticFunction<N, H, V, F> semantics)
			throws FixpointException {
		int size = adjacencyMatrix.getNodes().size();
		Map<N, AtomicInteger> lubs = new HashMap<>(size);
		Map<N, Pair<AnalysisState<H, V>, F>> result = new HashMap<>(size);
		startingPoints.keySet().forEach(ws::push);

		AnalysisState<H, V> oldApprox = null, newApprox;
		F oldIntermediate = null, newIntermediate;
		try {
			while (!ws.isEmpty()) {
				N current = ws.pop();

				if (current == null)
					throw new FixpointException(
							"Unknown node encountered during fixpoint execution in '" + this + "'");
				if (!adjacencyMatrix.getNodes().contains(current))
					throw new FixpointException("'" + current
							+ "' is not part of this graph, and cannot be analyzed in this fixpoint computation");

				AnalysisState<H, V> entrystate;
				try {
					entrystate = getEntryState(current, startingPoints, result);
				} catch (SemanticException e) {
					throw new FixpointException(
							"Exception while computing the entry state for '" + current + "' in " + this, e);
				}

				if (entrystate == null)
					throw new FixpointException(current + " does not have an entry state");

				if (result.containsKey(current)) {
					oldApprox = result.get(current).getLeft();
					oldIntermediate = result.get(current).getRight();
				} else {
					oldApprox = null;
					oldIntermediate = null;
				}

				try {
					newIntermediate = (F) mkInternalStore(entrystate);
					newApprox = semantics.compute(current, entrystate, cg, newIntermediate);
				} catch (SemanticException e) {
					log.error("Evaluation of the semantics of '" + current + "' in " + this
							+ " led to an exception: " + e);
					throw new FixpointException("Semantic exception during fixpoint computation", e);
				}

				if (oldApprox != null && oldIntermediate != null)
					try {
						if (widenAfter == 0) {
							newApprox = newApprox.lub(oldApprox);
							newIntermediate = newIntermediate.lub(oldIntermediate);
						} else {
							// we multiply by the number of predecessors since
							// if we have more than one
							// the threshold will be reached faster
							int lub = lubs
									.computeIfAbsent(current,
											e -> new AtomicInteger(widenAfter * predecessorsOf(e).size()))
									.getAndDecrement();
							if (lub > 0) {
								newApprox = newApprox.lub(oldApprox);
								newIntermediate = newIntermediate.lub(oldIntermediate);
							} else {
								newApprox = oldApprox.widening(newApprox);
								newIntermediate = oldIntermediate.widening(newIntermediate);
							}
						}
					} catch (SemanticException e) {
						throw new FixpointException(
								"Exception while updating the analysis results of '" + current + "' in " + this,
								e);
					}

				if ((oldApprox == null && oldIntermediate == null) || !newApprox.lessOrEqual(oldApprox)
						|| !newIntermediate.lessOrEqual(oldIntermediate)) {
					result.put(current, Pair.of(newApprox, newIntermediate));
					for (N instr : followersOf(current))
						ws.push(instr);
				}
			}

			HashMap<N, AnalysisState<H, V>> finalResults = new HashMap<>(result.size());
			for (Entry<N, Pair<AnalysisState<H, V>, F>> e : result.entrySet()) {
				finalResults.put(e.getKey(), e.getValue().getLeft());
				for (Entry<N, AnalysisState<H, V>> ee : e.getValue().getRight())
					finalResults.put(ee.getKey(), ee.getValue());
			}

			return finalResults;
		} catch (Exception e) {
			log.fatal("Unexpected exception during fixpoint computation of '" + this + "': " + e);
			throw new FixpointException("Unexpected exception during fixpoint computation", e);
		}
	}

	/**
	 * Builds a new instance of the {@link FunctionalLattice} that is used to
	 * store the fixpoint results on internal nodes, that is, node that are
	 * nested within outer ones.
	 * 
	 * @param <H>        the type of heap analysis embedded in the abstract
	 *                       state
	 * @param <V>        the type of value analysis embedded in the abstract
	 *                       state
	 * @param entrystate the analysis state before the creation of this lattice
	 * 
	 * @return the functional lattice where results on internal nodes will be
	 *             stored
	 */
	protected abstract <H extends HeapDomain<H>, V extends ValueDomain<V>> FunctionalLattice<?, N, AnalysisState<H, V>> mkInternalStore(
			AnalysisState<H, V> entrystate);

	private <H extends HeapDomain<H>, V extends ValueDomain<V>, F extends FunctionalLattice<F, N, AnalysisState<H, V>>> AnalysisState<H, V> getEntryState(
			N current,
			Map<N, AnalysisState<H, V>> startingPoints,
			Map<N, Pair<AnalysisState<H, V>, F>> result)
			throws SemanticException {
		AnalysisState<H, V> entrystate = startingPoints.get(current);
		Collection<N> preds = predecessorsOf(current);
		List<AnalysisState<H, V>> states = new ArrayList<>(preds.size());

		for (N pred : preds)
			if (result.containsKey(pred)) {
				// this might not have been computed yet
				E edge = adjacencyMatrix.getEdgeConnecting(pred, current);
				states.add(edge.traverse(result.get(edge.getSource()).getLeft()));
			}

		for (AnalysisState<H, V> s : states)
			if (entrystate == null)
				entrystate = s;
			else
				entrystate = entrystate.lub(s);

		return entrystate;
	}
}
