package scala.collection

/**
  * A multidict is a map that can associate a set of values to a given key.
  *
  * @tparam K the type of keys
  * @tparam V the type of values
  */
trait MultiDict[K, V]
  extends Iterable[(K, V)]
    with MultiDictOps[K, V, MultiDict, MultiDict[K, V]]
    with Equals {

  def multiMapFactory: MapFactory[MultiDictCC] = MultiDict

  override protected[this] def fromSpecificIterable(coll: Iterable[(K, V)]): MultiDictCC[K, V] = multiMapFactory.from(coll)
  override protected[this] def newSpecificBuilder(): mutable.Builder[(K, V), MultiDictCC[K, V]] = multiMapFactory.newBuilder[K, V]()

  def canEqual(that: Any): Boolean = true

  override def equals(o: Any): Boolean = o match {
    case that: MultiDict[K, V] =>
      (this eq that) ||
        (that canEqual this) &&
          (this.size == that.size) && {
          try {
            sets forall { case (k, vs) => that.sets.get(k).contains(vs) }
          } catch {
            case _: ClassCastException => false
          }
        }
    case _ => false
  }

  override def hashCode(): Int = Set.unorderedHash(sets, "MultiMap".##)

}


trait MultiDictOps[K, V, +CC[X, Y] <: MultiDict[X, Y], +C <: MultiDict[K, V]]
  extends IterableOps[(K, V), Iterable, C] {

  protected[this] type MultiDictCC[K, V] = CC[K, V]

  def multiMapFactory: MapFactory[MultiDictCC]

  protected[this] def multiMapFromIterable[L, W](it: Iterable[(L, W)]): CC[L, W] =
    multiMapFactory.from(it)

  protected[this] def fromSpecificSets(it: Iterable[(K, Set[V])]): C =
    fromSpecificIterable(it.view.flatMap { case (k, vs) => vs.view.map(v => (k, v)) })

  protected[this] def fromSets[L, W](it: Iterable[(L, Set[W])]): CC[L, W] =
    multiMapFromIterable(it.view.flatMap { case (k, vs) => vs.view.map(v => (k, v)) })

  /**
    * @return All the elements contained in this multidict, grouped by key
    */
  def sets: Map[K, Set[V]]

  def iterator(): Iterator[(K, V)] =
    sets.iterator().flatMap { case (k, vs) => vs.view.map(v => (k, v)) }

  /**
    * @return The set of values associated with the given `key`, or the empty
    *         set if there is no such association
    * @param key key to look up
    */
  def get(key: K): Set[V] = sets.get(key).getOrElse(Set.empty)

  /**
    * @return Whether `key` has at least one occurrence in this multidict or not
    * @param key the key to test
    */
  def containsKey(key: K): Boolean = sets.contains(key)

  /**
    * @return Whether the binding `kv` is contained in this multidict or not
    * @param kv the binding to test
    */
  def containsEntry(kv: (K, V)): Boolean = sets.get(kv._1).exists(_.contains(kv._2))

  /**
    * @return Whether at least one key is associated to the given `value`
    * @param value the value to test
    */
  def containsValue(value: V): Boolean = sets.exists { case (_, vs) => vs.contains(value) }

  /** @return the set of keys */
  def keySet: Set[K] = sets.keySet

  /** @return all the values contained in this multidict */
  def values: Iterable[V] = sets.values.flatten

  /**
    * @return a multidict that contains all the entries of `this` multidict,
    *         transformed by the function `f`
    *
    * @param f transformation function
    * @tparam L new type of keys
    * @tparam W new type of values
    */
  def map[L, W](f: ((K, V)) => (L, W)): CC[L, W] =
    multiMapFromIterable(new View.Map(toIterable, f))

  /**
    * @return a multidict that contains all the entries of `this` multidict,
    *         transformed by the function `f` and concatenated
    *
    * @param f transformation function
    * @tparam L new type of keys
    * @tparam W new type of values
    */
  def flatMap[L, W](f: ((K, V)) => IterableOnce[(L, W)]): CC[L, W] =
    multiMapFromIterable(new View.FlatMap(toIterable, f))

  /**
    * @return a multidict that contains all the entries of `this` multidict
    *         after they have been successfully transformed by the
    *         given partial function `pf`
    *
    * @param pf transformation to apply
    * @tparam L new type of keys
    * @tparam W new type of values
    */
  def collect[L, W](pf: PartialFunction[(K, V), (L, W)]): CC[L, W] =
    flatMap(kv =>
      if (pf.isDefinedAt(kv)) new View.Single(pf(kv))
      else View.Empty
    )

  /** Concatenate the entries given in `that` iterable to `this` multidict */
  def concat(that: Iterable[(K, V)]): C =
    fromSpecificIterable(new View.Concat(toIterable, that))

  override def withFilter(p: ((K, V)) => Boolean): MultiMapWithFilter = new MultiMapWithFilter(p)

  class MultiMapWithFilter(p: ((K, V)) => Boolean) extends WithFilter(p) {
    def map[L, W](f: ((K, V)) => (L, W)): CC[L, W] = multiMapFromIterable(new View.Map(filtered, f))
    def flatMap[L, W](f: ((K, V)) => IterableOnce[(L, W)]): CC[L, W] = multiMapFromIterable(new View.FlatMap(filtered, f))
    override def withFilter(q: ((K, V)) => Boolean): MultiMapWithFilter = new MultiMapWithFilter(kv => p(kv) && q(kv))
  }

  /**
    * @return Whether there exists a value associated with the given `key`
    *         that satisfies the given predicate `p`
    */
  def entryExists(key: K, p: V => Boolean): Boolean =
    sets.get(key).exists(_.exists(p))

  /**
    * @return a new multidict resulting from applying the given function `f`
    *         to each group of values of this multidict and collecting
    *         the results
    * @param f the function to apply
    * @tparam L the new type of keys
    * @tparam W the type of values of the returned multidict
    */
  def mapSets[L, W](f: ((K, Set[V])) => (L, Set[W])): CC[L, W] =
    fromSets(new View.Map(sets, f))

  /**
    * @return a multidict that contains all the entries of `this` multidict,
    *         after they have been successfully transformed by the given
    *         partial function
    *
    * @param pf the partial function to apply to each set of values
    * @tparam L the new type of keys
    * @tparam W the new type of values
    */
  def collectSets[L, W](pf: PartialFunction[(K, Set[V]), (L, Set[W])]): CC[L, W] =
    flatMapSets(kvs =>
      if (pf.isDefinedAt(kvs)) new View.Single(pf(kvs))
      else View.Empty
    )

  /**
    * @return a new multidict resulting from applying the given function `f`
    *         to each group of values of this multidict and concatenating
    *         the results
    * @param f the function to apply
    * @tparam L the new type of keys
    * @tparam W the type of values of the returned multidict
    */
  def flatMapSets[L, W](f: ((K, Set[V])) => IterableOnce[(L, Set[W])]): CC[L, W] =
    fromSets(new View.FlatMap(sets, f))

  /**
    * @return a new multidict concatenating the values of this multidict
    *         and `that` collection of values
    *
    * @param that the collection of values to add to this multidict
    */
  def concatSets(that: Iterable[(K, Set[V])]): C =
    fromSpecificSets(new View.Concat(sets, that))

  /**
    * @return a multidict that contains all the entries of this multidict
    *         that satisfy the predicate `p`
    */
  def filterSets(p: ((K, Set[V])) => Boolean): C =
    fromSpecificSets(new View.Filter(sets, p, isFlipped = false))

}

object MultiDict extends MapFactory.Delegate[MultiDict](immutable.MultiDict)