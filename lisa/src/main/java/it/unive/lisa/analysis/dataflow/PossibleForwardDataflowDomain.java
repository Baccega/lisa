package it.unive.lisa.analysis.dataflow;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.lattices.SetLattice;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A {@link DataflowDomain} for <b>forward</b> and <b>possible</b> dataflow
 * analysis. Being possible means that this domain is an instance of
 * {@link SetLattice}, i.e., is a set whose join operation is the set union.
 * 
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 * 
 * @param <E> the type of {@link DataflowElement} contained in this domain
 */
public class PossibleForwardDataflowDomain<E extends DataflowElement<PossibleForwardDataflowDomain<E>, E>> extends
		SetLattice<PossibleForwardDataflowDomain<E>, E> implements DataflowDomain<PossibleForwardDataflowDomain<E>, E> {

	private final boolean isTop;

	private final E domain;

	/**
	 * Builds an empty domain.
	 * 
	 * @param domain a singleton instance to be used during semantic operations
	 *                   to perform <i>kill</i> and <i>gen</i> operations
	 */
	public PossibleForwardDataflowDomain(E domain) {
		this(domain, new HashSet<>(), true);
	}

	private PossibleForwardDataflowDomain(E domain, Set<E> elements, boolean isTop) {
		super(elements);
		this.domain = domain;
		this.isTop = isTop;
	}

	@Override
	public PossibleForwardDataflowDomain<E> assign(Identifier id, ValueExpression expression, ProgramPoint pp)
			throws SemanticException {
		// if id cannot be tracked by the underlying lattice,
		// or if the expression cannot be processed, return this
		if (!domain.tracksIdentifiers(id) || !domain.canProcess(expression))
			return this;
		PossibleForwardDataflowDomain<E> killed = forgetIdentifiers(domain.kill(id, expression, pp, this));
		Set<E> updated = new HashSet<>(killed.elements);
		for (E generated : domain.gen(id, expression, pp, this))
			updated.add(generated);
		return new PossibleForwardDataflowDomain<E>(domain, updated, false);
	}

	@Override
	public PossibleForwardDataflowDomain<E> smallStepSemantics(ValueExpression expression, ProgramPoint pp)
			throws SemanticException {
		return this;
	}

	@Override
	public PossibleForwardDataflowDomain<E> assume(ValueExpression expression, ProgramPoint pp)
			throws SemanticException {
		// TODO could be refined
		return this;
	}

	@Override
	public PossibleForwardDataflowDomain<E> forgetIdentifier(Identifier id) throws SemanticException {
		if (isTop())
			return this;

		Collection<E> toRemove = new LinkedList<>();
		for (E e : elements)
			if (e.getIdentifier().equals(id))
				toRemove.add(e);

		if (toRemove.isEmpty())
			return this;
		Set<E> updated = new HashSet<>(elements);
		updated.removeAll(toRemove);
		return new PossibleForwardDataflowDomain<E>(domain, updated, false);
	}

	@Override
	public Satisfiability satisfies(ValueExpression expression, ProgramPoint pp) throws SemanticException {
		// TODO could be refined
		return Satisfiability.UNKNOWN;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (isTop ? 1231 : 1237);
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		PossibleForwardDataflowDomain<E> other = (PossibleForwardDataflowDomain<E>) obj;
		if (isTop != other.isTop)
			return false;
		return true;
	}

	@Override
	public String representation() {
		SortedSet<String> res = new TreeSet<>();
		elements.stream().map(e -> e.toString()).forEach(res::add);
		return res.toString();
	}

	@Override
	public PossibleForwardDataflowDomain<E> top() {
		return new PossibleForwardDataflowDomain<>(domain, new HashSet<>(), true);
	}

	@Override
	public boolean isTop() {
		return elements.isEmpty() && isTop;
	}

	@Override
	public PossibleForwardDataflowDomain<E> bottom() {
		return new PossibleForwardDataflowDomain<>(domain, new HashSet<>(), false);
	}

	@Override
	public boolean isBottom() {
		return elements.isEmpty() && !isTop;
	}

	@Override
	protected PossibleForwardDataflowDomain<E> mk(Set<E> set) {
		return new PossibleForwardDataflowDomain<>(domain, set, false);
	}

	@Override
	public Collection<E> getDataflowElements() {
		return elements;
	}
}
