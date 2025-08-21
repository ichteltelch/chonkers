package ds.chonker.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import ds.chonker.tree.ChonkerLeaf.ByteLeaf;
import ds.intrusive.IndexGetterAndSetter;
import ds.intrusive.IntrusiveSet;

public class Rechonker <M extends ChonkersMonoidData<M>>{
	static final IndexGetterAndSetter<Item<?>> getSetBucketIndex = new IndexGetterAndSetter<Item<?>>() {
		public int getIndex(Item<?> e) {
			return e.bucketIndex;
		}
		public void setIndex(Item<?> e, int index) {
			e.bucketIndex = index;
		}
	};


	ChonkerTreeZipper<M> left;
	ChonkerTreeZipper<M> right;
	ChonkerConfig<M> config;

	List<IntrusiveSet<Item<M>>> prioBuckets=new ArrayList<>();
	private Random randomizer;
	{
		for(int i=0; i<6; ++i)
			prioBuckets.add(new IntrusiveSet<>(getSetBucketIndex));
	}
	void clearPrioBuckets() {
		prioBuckets.forEach(IntrusiveSet::clear);
	}
	Item<M> pollPrioBuckets(int prio) {
		IntrusiveSet<Item<M>> l = prioBuckets.get(prio);
		return pollPrioBucket(prio, l);
	}
	private Item<M> pollPrioBucket(int prio, IntrusiveSet<Item<M>> l) {
		//		for(Iterator<Item<M>> i = l.iterator(); i.hasNext(); ) {
		//			Item<M> next = i.next();
		//			i.remove();
		//			if(next.mergePriority==prio)
		//				return next;
		//		}
		while(l.size()>0) {
			Item<M> next = l.elementAt(randomizer==null?l.size()-1:randomizer.nextInt(l.size()));
			l.remove(next);
			if(next.mergePriority==prio)
				return next;
		}
		return null;
	}


	int currentLevel;
	int phase;
	int lastLayerOfPreviousPhase;
	int firstLayerOfCurrentPhase;
	int lastLayerOfCurrentPhase;

	static class Item<M extends ChonkersMonoidData<M>>{
		/**
		 * Where this Item<M> is inside its bucket
		 */
		int bucketIndex = -1;
		/**
		 * A value that increases going from left to right in the ordered queue.
		 * This is used to break ties for equal merge priorities, so that
		 * merging proceeds from left to right.
		 */
		long queueOrdering = Long.MIN_VALUE;
		ChonkerTreeZipper<M> zip;
		ChonkerNode<M> node;
		boolean leftTainted = false;
		boolean rightTainted = false;
		/**
		 * The left neighbor in the ordered queue.
		 * Is zero if at left end; in this case, leftItem should be {@code this}
		 */
		Item<M> left;
		/**
		 * The right neighbor in the ordered queue.
		 * Is zero if at right end; in this case, rightItem should be {@code this}
		 */
		Item<M> right;
		Diffbits diffBits;
		int mergePriority;

		public Item(ChonkerTreeZipper<M> zip, Item<M> left, Item<M> right, ChonkerConfig<M> config) {
			this(zip, left, right, config, ChonkerNode.NO_MERGE_PRIORITY);
		}
		public Item(ChonkerTreeZipper<M> zip, Item<M> left, Item<M> right, ChonkerConfig<M> config, int mergePrio) {
			assert !zip.isEnd();
			this.zip = zip;
			this.left = left;
			this.right = right;
			//			this.diffBits = diffBits;
			this.mergePriority = mergePrio;
			this.node=zip.node;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(" @ ").append(queueOrdering);
			if(mergePriority!=ChonkerNode.NO_MERGE_PRIORITY) {
				sb.append(" !!=> ").append(mergePriority);
			}
			sb.append("\t");
			sb.append(node);

			if(diffBits!=null) {
				sb.append(diffBits);
			}
			return sb.toString();
		}
	};

	/**
	 * Insert an Item<M> into the ordered queue.
	 * Left and right pointers must be set already (or be null for queue end position).
	 * The queue ordering position will be set.
	 * This does not insert it into the heap, 
	 * as this can only be done once the merge priority is known.
	 * Also, depending on the left and right pointers, this insertion may
	 * result in removing items from the queue. These items are then also removed from the
	 * heap.
	 * @param i
	 */
	void insert(Item<M> i) {
		//		assert (i.diffBits==null) == (i.mergePriority == ChonkerNode.NO_MERGE_PRIORITY); 
		Item<M> leftmostCutoff;
		if(i.left==null) {
			//						leftmostCutoff = leftItem==null?null:leftItem.right;
			leftmostCutoff = leftItem==null?null:leftItem;
			leftItem = i;
		}else {
			leftmostCutoff = i.left.right;
			i.left.right=i;
		}
		Item<M> rightmostCutoff;
		if(i.right==null) {
			rightmostCutoff = rightItem==null?null:rightItem;
			rightItem = i;
		}else {
			rightmostCutoff = i.right.left;
			i.right.left=i;
		}
		if(leftmostCutoff!= null && rightmostCutoff!=null) {
			if(leftmostCutoff.queueOrdering <= rightmostCutoff.queueOrdering) {
				Item<M> at = leftmostCutoff;
				while(true) {
					if(at.mergePriority!=ChonkerNode.NO_MERGE_PRIORITY) {
						at.mergePriority = ChonkerNode.NO_MERGE_PRIORITY;
					}
					if(at==rightmostCutoff)
						break;
					at = at.right;
					assert at!=null;
				}
			}
		}
		//		compute queue ordering value
		if(i.left != null) {
			i.queueOrdering = i.left.queueOrdering + i.left.node.weight();
		}else if (i.right != null) {
			i.queueOrdering = i.right.queueOrdering - i.node.weight();
		} else if(left.isEnd()){
			i.queueOrdering = 0;
		} else {
			i.queueOrdering = left.discardRight(config).getRoot().node.weight();
		}
		if(i.mergePriority!=ChonkerNode.NO_MERGE_PRIORITY) {
			prioBuckets.get(i.mergePriority).add(i);
		}
	}
	Item<M> leftItem;
	Item<M> rightItem;
	int maxDiffbitOrder;
	long absoluteUnit;

	int diffbit;
	//	Item<M> leftmostChange;
	//	Item<M> rightmostChange;

	public Rechonker(ChonkerConfig<M> _config, 
			ChonkerTreeZipper<M> _left, 
			Iterable<? extends ChonkerNode<M>> mid, 
					ChonkerTreeZipper<M> _right) {
		this(_config, _left, mid, _right, null);
	}
	public Rechonker(ChonkerConfig<M> _config, 
			ChonkerTreeZipper<M> _left, 
			Iterable<? extends ChonkerNode<M>> mid, 
					ChonkerTreeZipper<M> _right, Random randomizer) {
		this.config=_config;
		this.randomizer = randomizer;
		left = _left.discardRight(config).rightMost(0);
		right = _right.discardLeft(config).leftMost(0);
		currentLevel = 0;
		phase = 2;
		firstLayerOfCurrentPhase  = ChonkerNode.encodeLevelTag(currentLevel, phase, 0);
		lastLayerOfPreviousPhase = 0;
		absoluteUnit=config.absoluteUnit(currentLevel);
		maxDiffbitOrder=config.maxDiffbitOrder(currentLevel);


		//Insert new items
		for(ChonkerNode<M> n:mid) {
			Item<M> item = new Item<M>(new ChonkerTreeZipper<M>(n, null, 0), rightItem, null, (config));
			insert(item);
			//Fuse equal items if possible
			//			tryFuseRight();
		}

		//		fuseWithContext();






	}
	public void print() {
		if(PRINT)
			System.out.println(toString());
	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb
		.append(currentLevel).append(" : ").append(phase).append("    ")
		.append(left.node).append(" < …… > ").append(right.node)
		.append("\n  ")
		//		.append("left context zipper: "+left)
		//		.append("\n  ")
		//		.append("right context zipper: "+right)
		//		.append("\n  ")
		;
		for(Item<M> i = leftItem; i!=null; i=i.right) {
			sb.append(i);
			if(i.leftTainted)
				sb.append(" LAL ");
			if(i.rightTainted)
				sb.append(" RAL ");

			sb.append("\n  ");
		}
		//		List<ChonkerNode> list = getListAtLevelTag(lastLayerOfPreviousPhase);
		//		sb.append(list)		
		//		.append("\n  ");
		//		sb.append(flatten(list));
		return sb.toString();
	}
	public ChonkerNode<M> run() {
		while(true) {
			//			System.out.println("L: "+ (queueLength()));
			//			System.out.println("L: "+ (queueLength()*(double)absoluteUnit));
			doBalancingPhaseBuckets();
			doCaterpillarPhase();
			doDiffbitPhase();
			ChonkerNode<M> res = result();
			if(res!=null)
				return res;
		}

	}
	int queueLength() {
		int r = 0;
		for(Item<M> i = leftItem; i!=null; i=i.right)
			r++;
		return r;
	}
	private ChonkerNode<M> result() {
		if(leftItem==rightItem && left.isEnd() && right.isEnd())
			return leftItem.node;
		else
			return null;
	}



	private void doBalancingPhaseBuckets() {		

		initBalancePhase();

		computeBalancePrios();

		for(int i=0; i<2; ++i) {
			print();
			doBalancePriority(i);
		}
		print();

		//		finishBalancePhase();
		phaseEndCleanup();

		//		for(int prio=0; prio<4; ++prio) {
		//			int tag = ChonkerNode.encodeLevelTag(currentLayer, 0, prio);
		//			IntrusiveSet<Item<M>> bucket = prioBuckets.get(prio);
		//
		//			mergeLeftItem: if(!left.isEnd()) {
		//				if(left.node.rawBitLength() + leftItem.node.rawBitLength() >= absoluteUnit)
		//					break mergeLeftItem;
		//				ChonkerTreeZipper<M> ancestor = leftItem.zip.commonAncestorUpToTag(left, tag);
		//				if(ancestor==null)
		//					break mergeLeftItem;
		//				assert ancestor.node.layer() == currentLayer;
		//				assert ancestor.node.phase() == 0;
		//				if(ancestor.node.prio() != prio)
		//					break mergeLeftItem;
		//				int excess = ancestor.node.rawBitLength() - leftItem.node.rawBitLength();
		//				Item<M> it = new Item<M>(ancestor, null, leftItem.right, (config)
		//						, leftItem.mergePriority
		//						);
		//				insert(it);
		//				while(excess > 0) {
		//					excess -= left.node.rawBitLength();
		//					left = left.left(previousPassLevelTag);
		//				}
		//				assert excess == 0;
		//				//				if(it.mergePriority<=prio){
		//				//					assert it.mergePriority < prio;
		//				//					it.diffBits = null;
		//				//					it.mergePriority=ChonkerNode.NO_MERGE_PRIORITY;
		//				//				}
		//
		//
		//			}
		//			mergeRightItem: if(!right.isEnd()) {
		//				if(rightItem.node.rawBitLength() + right.node.rawBitLength() >= absoluteUnit) {
		//					break mergeRightItem;
		//				}
		//				ChonkerTreeZipper<M> ancestor = rightItem.zip.commonAncestorUpToTag(right, tag);
		//				if(ancestor==null)
		//					break mergeRightItem;
		//				assert ancestor.node.layer() == currentLayer;
		//				assert ancestor.node.phase() == 0;
		//				if(ancestor.node.prio() != prio)
		//					break mergeRightItem;
		//
		//				int excess = ancestor.node.rawBitLength() - rightItem.node.rawBitLength();
		//				Item<M> it = new Item<M>(ancestor, rightItem.left, null, (config));
		//				insert(it);
		//				while(excess > 0) {
		//					excess -= right.node.rawBitLength();
		//					right = right.right(previousPassLevelTag);
		//				}
		//				assert excess == 0;
		//			}
		//
		//
		//			while(true) {
		//				Item<M> at = pollPrioBucket(prio, bucket);
		//				if(at==null)
		//					break;
		//				bucket.remove(at);
		//				if(at.right==null)
		//					continue;
		//				if(at.node.rawBitLength()>=absoluteUnit)
		//					continue;
		//				//				if(at.node.levelTag() >= currentLevelTag)
		//				//					continue;
		//
		//
		//
		//				ChonkerTreeZipper<M> ancestor;
		//				if(at.right==null) {
		//					ancestor = at.zip.commonAncestorUpToTag(right, tag);
		//				}else if(at.right.right==null){
		//					ancestor = at.zip.commonAncestorUpToTag(at.right.zip, tag);
		//				}else {
		//					ancestor=null;
		//				}
		//				Item<M> it;
		//				if(ancestor!=null) {
		//
		//					if(at.right==null) {
		//						int excess = ancestor.node.rawBitLength() - at.node.rawBitLength();
		//						it = new Item<M>(ancestor, at.left, null, (config));
		//						if(it.left!=null 
		//								&& it.left.mergePriority != ChonkerNode.NO_MERGE_PRIORITY
		//								&& it.left.mergePriority==at.mergePriority) {
		//							prioBuckets.get(at.mergePriority).remove(it.left);
		//						}
		//						insert(it);
		//						while(excess > 0) {
		//							excess -= right.node.rawBitLength();
		//							right = right.right(previousPassLevelTag);
		//						}
		//						assert excess == 0;
		//
		//
		//
		//
		//						continue;
		//					}else {
		//
		//						//						Item<M> insl, insr;
		//						//						ChonkerTreeZipper<M> uppl
		//						if(ancestor.node.isConcat(at.node, at.right.node) &&
		//								ancestor.node.levelTag() == tag) {
		//							it = new Item<M>(ancestor, at.left, at.right.right, (config)
		//									, at.right.mergePriority
		//									);
		//						}else {
		//							ancestor=null;
		//							it=null;
		//						}
		//
		//					}
		//
		//
		//
		//				}else {
		//					it=null;
		//				}
		//				if(ancestor==null) { 
		//					if(at.right==null)
		//						continue;
		//					if(at.right.right==null)
		//						continue;
		//					if(at.right!=null){
		//
		//						if(at.right.mergePriority == prio)
		//							continue;
		//
		//						if(at.node.rawBitLength() + at.right.node.rawBitLength() >= absoluteUnit) {
		//							continue;
		//						}
		//						ChonkerNode<M> nn = config.branch(tag, at.node, at.right.node);
		//						ChonkerTreeZipper<M> nz = new ChonkerTreeZipper<M>(nn);
		//						it = new Item<M>(nz, at.left, at.right.right, (config)
		//								, at.right.mergePriority
		//								);
		//					}else {
		//						continue;
		//					}
		//				}
		//				//					it.diffBits = at.right.diffBits;
		//				if(it.node.levelTag()>layerEndTag)
		//					System.out.print("");
		//				if(it.left!=null 
		//						&& it.left.mergePriority != ChonkerNode.NO_MERGE_PRIORITY
		//						&& it.left.mergePriority==at.mergePriority) {
		//					bucketRemove(it.left);
		//				}
		//
		//				//					if(it.mergePriority<=prio){
		//				//						assert it.mergePriority < prio;
		//				//						it.diffBits = null;
		//				//						it.mergePriority=ChonkerNode.NO_MERGE_PRIORITY;
		//				//					}
		//				insert(it);
		//				ChonkerNode<M> m = it.node;
		//				if(it.left!=null) {
		//					ChonkerNode<M> l = it.left.node;
		//					boolean heckinLeft = l!=null && l.rawBitLength() + m.rawBitLength() < absoluteUnit;
		//					if(!heckinLeft) {
		//						//						it.left.mergePriority = ChonkerNode.NO_MERGE_PRIORITY;
		//						bucketRemove(it.left);
		//
		//					}else {
		//						if(it.left.mergePriority==at.mergePriority) {
		//							bucketRemove(it.left);
		//						}
		//					}
		//				}
		//				ChonkerNode<M> r;
		//				if(it.right!=null)
		//					r = it.right.node;
		//				else if(right.isEnd())
		//					r = null;
		//				else
		//					r = right.upToLayer(tag).node;
		//				if(r!=null) {
		//					boolean heckinRight = r!=null && r.rawBitLength() + m.rawBitLength() < absoluteUnit;
		//					if(!heckinRight) {
		//						bucketRemove(it);
		//						//						it.mergePriority = ChonkerNode.NO_MERGE_PRIORITY;
		//					}
		//				}
		//			}
		//
		//
		//
		//
		//		}




	}
	private void computeBalancePrios() {
		for(Item<M> at = leftItem; at!=null; at=at.right) {
			ChonkerNode<M> m = at.node;
			ChonkerNode<M> l;
			ChonkerNode<M> r;
			if(at.left!=null) {
				l = at.left.node;
			}else if(left.isEnd()) {
				l = null;
			}else {
				l = left.node;
			}
			if(at.right!=null) {
				r = at.right.node;
			}else if(right.isEnd()) {
				r = null;
			}else {
				r = right.node;
			}
			boolean smallerThanAllNeighbors = true;
//			boolean biggerThanAllNeighbors = !true;
			if(l!=null) {
				int compare = Long.compare(m.weight(), l.weight());
				if(compare==0) {
//					long db = l.getRawDiffbit(0, 0, m);
					long db = l.equalContent(m)?-1:l.getDiffbit(config, m, currentLevel);
					if(db>=0)
						compare = (((int)db)&1)*2-1;
				}
				if(compare>=0) {
					smallerThanAllNeighbors = false;
				}
//				if(compare<=0) {
//					biggerThanAllNeighbors = false;
//				}
			}
			if(r!=null) {
				int compare = Long.compare(m.weight(), r.weight());
				if(compare==0) {
//					long db = r.getRawDiffbit(0, 0, m);
					long db = r.equalContent(m)?-1:r.getDiffbit(config, m, currentLevel);
					if(db>=0)
						compare = (((int)db)&1)*2-1;
				}
				if(compare>=0) {
					smallerThanAllNeighbors = false;
				}
//				if(compare<=0) {
//					biggerThanAllNeighbors = false;
//				}
			}
			boolean heckinLeft = l!=null && l.weight() + m.weight() < absoluteUnit;
			boolean heckinRight = r!=null && r.weight() + m.weight() < absoluteUnit;
			if(smallerThanAllNeighbors) {
				if(heckinRight)
					at.mergePriority = 0;
				if(heckinLeft && at.left!=null)
					at.left.mergePriority = Math.min(at.left.mergePriority, 1);
//			}else if(biggerThanAllNeighbors) {
//				if(heckinRight)
//					at.mergePriority = 2;
//				if(heckinLeft && at.left!=null)
//					at.left.mergePriority = Math.min(at.left.mergePriority, 3);
			}
		}
		print();

		clearPrioBuckets();
		for(Item<M> at = leftItem; at!=null; at=at.right) {
			if(at.mergePriority != ChonkerNode.NO_MERGE_PRIORITY) {	
				prioBuckets.get(at.mergePriority).add(at);
				//				at.diffBits=new Diffbits(0, 2, null);
			}
		}
	}
	private void initBalancePhase() {
		assert isDiffbitPhase();


		lastLayerOfPreviousPhase = lastLayerOfCurrentPhase;
		phase = 0;
		currentLevel++;
		firstLayerOfCurrentPhase = ChonkerNode.encodeLevelTag(currentLevel, phase, 0);
		lastLayerOfCurrentPhase = ChonkerNode.encodeLevelTag(currentLevel, phase, 1);
		left=left.discardRight(config).upToLayer(lastLayerOfPreviousPhase);
		right=right.discardLeft(config).upToLayer(lastLayerOfPreviousPhase);
		absoluteUnit=config.absoluteUnit(currentLevel);
		maxDiffbitOrder=config.maxDiffbitOrder(currentLevel);
		clearItemZippers();


		int aus = 2;
		bringIntoQueue(6, 5, 5, 4, absoluteUnit, (absoluteUnit/aus>=Long.MAX_VALUE/aus)?Long.MAX_VALUE:aus*absoluteUnit);
		//		bringIntoQueue(60, 30, 60, 20, 0);




		for(Item<M> at = leftItem; at!=null; at=at.right) {
			at.mergePriority = ChonkerNode.NO_MERGE_PRIORITY;
		}
	}


	void bringIntoQueue(
			int leftCount, int leftUntainted, 
			int rightCount, int rightUntainted, 
			long maxHeckinWeight, long maxTotalUntaintedWeight) {
		{
			long totalUntaintedWeight = 0;
			for(int i=0; 
					i<rightCount 
					&& !right.isEnd()
					&& (maxHeckinWeight<=0 || rightItem == null ||
					//					rightItem.node.weight() + 
					right.node.weight()<maxHeckinWeight)
					//					|| _falsePrint("skipped adding "+(rightCount-i)+" items because not heckin'")
					; ++i)
			{
				insert(new Item<M>(right, rightItem, null, (config)));
				right = right.right(lastLayerOfPreviousPhase);
				if(i>=rightUntainted)
					rightItem.rightTainted = true;
				else if(maxTotalUntaintedWeight>0){
					totalUntaintedWeight += rightItem.node.weight();
					if(totalUntaintedWeight>maxTotalUntaintedWeight 
							|| totalUntaintedWeight<0 //May have overflown
							) {
						//Skip to inserting tainted items
						//						System.out.println("Skipped adding "+(rightUntainted - 1 - i)+" untainted items");
						i = rightUntainted-1;
					}
				}
			}
		}
		{
			long totalUntaintedWeight = 0;
			for(int i=0; 
					i<leftCount 
					&& !left.isEnd()
					&& (maxHeckinWeight<=0 || leftItem==null ||
					//				leftItem.node.weight() + 
					left.node.weight()<maxHeckinWeight
					//				|| _falsePrint("skipped adding "+(leftCount-i)+" items because not heckin'")
							)
					; ++i)
			{
				insert(new Item<M>(left, null, leftItem, (config)));
				left = left.left(lastLayerOfPreviousPhase);
				if(i>=leftUntainted)
					leftItem.leftTainted = true;
				else if(maxTotalUntaintedWeight>0){
					totalUntaintedWeight += leftItem.node.weight();
					if(totalUntaintedWeight>maxTotalUntaintedWeight
							|| totalUntaintedWeight<0 //May have overflown
							) {
						//Skip to inserting tainted items
						//					System.out.println("Skipped adding "+(leftUntainted - 1 - i)+" untainted items");

						i = leftUntainted-1;
					}
				}
			}
		}
	}

	static boolean _false() {
		return false;
	}
	static boolean _falsePrint(String s) {
		System.out.println(s);
		return false;
	}
	void bucketRemove(Item<M> i) {
		if(i.mergePriority != ChonkerNode.NO_MERGE_PRIORITY)
			prioBuckets.get(i.mergePriority).remove(i);

	}

	private boolean isDiffbitPhase() {
		return phase == 2;
	}
	private void doCaterpillarPhase() {
		assert isBalancingPhase();




		//increment level, enter megachonker merging mode
		lastLayerOfPreviousPhase = lastLayerOfCurrentPhase;
		++phase;
		firstLayerOfCurrentPhase = ChonkerNode.encodeLevelTag(currentLevel, phase, 0);
		lastLayerOfCurrentPhase = firstLayerOfCurrentPhase;
		left=left.discardRight(config).upToLayer(lastLayerOfPreviousPhase);
		right=right.discardLeft(config).upToLayer(lastLayerOfPreviousPhase);
		absoluteUnit=config.absoluteUnit(currentLevel);
		maxDiffbitOrder=config.maxDiffbitOrder(currentLevel);
		clearItemZippers();


		bringIntoQueue(2, 3, 2, 3, 0, 0);

		print();

		fuseWithLeftContext();


		//fuse chunks in window
		for(Item<M> i = leftItem; i!=null && i.right!=null;) {
			//			List<ChonkerNode> lalt = getListAtLevelTag(currentLevelTag);
			//			lalt.subList(40, lalt.size()).clear();
			//			System.out.println(lalt); 
			ChonkerNode<M> fused = config.tryFuse(i.node, i.right.node, firstLayerOfCurrentPhase, 0, 0);
			if(fused!=null) {
				ChonkerTreeZipper<M> zip = new ChonkerTreeZipper<M>(fused, null, 0);
				Item<M> it = new Item<M>(zip, i.left, i.right.right, (config));

				insert(it);
				i=it;
			}else {
				if(i.node.equalContent(i.right.node)) {
					config.tryFuse(i.node, i.right.node, firstLayerOfCurrentPhase, 0, 0);
				}
				i=i.right;
			}
		}


		//		for(Item<M> i = leftItem; i!=null && i.right!=null; i=i.right) {
		//			if(i.node.equalContent(i.right.node))
		//				config.tryFuse(i.node, i.right.node, firstLayerOfCurrentPhase, 0, 0);
		//			else {
		//				try{
		//					i.node.getDiffbit(config, i.right.node, firstLayerOfCurrentPhase);
		//				}catch(Exception e){
		//					config.tryFuse(i.node, i.right.node, firstLayerOfCurrentPhase, 0, 0);			
		//				}
		//			}
		//		}


		//fuse relevant parts of the context to the window
		//fuse equal items with context
		fuseWithRightContext();

//		ChonkerNode<M> last = null;
//		for(ChonkerNode<M> i: getListAtLevelTag(lastLayerOfCurrentPhase)) {
//			if(last!=null) {
////				if(i.equalContent(last))
////					config.tryFuse(last, i, firstLayerOfCurrentPhase, 0, 0);
////				else {
////					try{
////						last.getDiffbit(config, i, firstLayerOfCurrentPhase);
////					}catch(Exception e){
////						config.tryFuse(last, i, firstLayerOfCurrentPhase, 0, 0);			
////					}
////				}
//			}
//			last = i;
//		}

	}
	private void clearItemZippers() {
		for(Item<M> at = leftItem; at!=null; at=at.right) {
			at.zip = new ChonkerTreeZipper<M>(at.node);
		}
	}
	public static <M extends ChonkersMonoidData<M>> void checkBalancePrecondition(ChonkerConfig<M> c, ChonkerNode<M> node, int layer) {	
		List<ChonkerNode<M>> list = new ArrayList<>();
		give(node, ChonkerNode.encodeLevelTag(layer, 0, 0)-1, list);
		List<Long> sizes = weights(list);
		boolean lastWasKitten = false;
		long absoluteUnit = c.absoluteUnit(layer);
		for(long size : sizes) {
			boolean isKitten = size*4<absoluteUnit;
			if(isKitten && lastWasKitten) {
				throw new IllegalStateException("Consecutive kittens in layer "+layer);
			}
			lastWasKitten = isKitten;
		}
	}
	public static List<Long> weights(List<? extends ChonkerNode<?>> list) {
		return list.stream().map(ChonkerNode::weight).collect(Collectors.toList());
	}
	public static <M extends ChonkersMonoidData<M>> void checkBalancePostcondition(ChonkerConfig<M> c, ChonkerNode<M> node, int layer) {	
		List<ChonkerNode<M>> list = new ArrayList<>();
		give(node, ChonkerNode.encodeLevelTag(layer, 0, 3), list);
		List<Long> sizes = weights(list);
		long absoluteUnit = c.absoluteUnit(layer);
		long lastSize = Integer.MAX_VALUE/2;
		boolean lastWasKitten = false;

		for(long size : sizes) {
			boolean isKitten = size*4<absoluteUnit;
			boolean isHeckinLeft = size + lastSize < absoluteUnit;

			if(isKitten && isHeckinLeft) {
				throw new IllegalStateException("The kitten is a heckin' chonker on its left side in layer "+layer);
			}
			if(lastWasKitten && isHeckinLeft) {
				throw new IllegalStateException("The kitten is a heckin' chonker on its right side in layer "+layer);
			}


			lastWasKitten = isKitten;
			lastSize = size;
		}
	}
	public static <M extends ChonkersMonoidData<M>> void checkDiffbitPrecondition(ChonkerConfig<M> c, ChonkerNode<M> node, int layer) {	
		List<ChonkerNode<M>> list = new ArrayList<>();
		give(node, ChonkerNode.encodeLevelTag(layer, 2, 0)-1, list);
		List<Long> sizes = weights(list);
		long absoluteUnit = c.absoluteUnit(layer);
		long lastSize = Integer.MAX_VALUE/2;
		boolean lastWasKitten = false;

		for(long size : sizes) {
			boolean isKitten = size*4<absoluteUnit;
			boolean isHeckinLeft = size + lastSize < absoluteUnit;

			if(isKitten && isHeckinLeft) {
				throw new IllegalStateException("The kitten is a heckin' chonker on its left side in layer "+layer);
			}
			if(lastWasKitten && isHeckinLeft) {
				throw new IllegalStateException("The kitten is a heckin' chonker on its right side in layer "+layer);
			}


			lastWasKitten = isKitten;
			lastSize = size;
		}
	}
	public static <M extends ChonkersMonoidData<M>> void checkDiffbitPostcondition(ChonkerConfig<M> c, ChonkerNode<M> node, int layer) {	
		List<ChonkerNode<M>> list = new ArrayList<>();
		give(node, ChonkerNode.encodeLevelTag(layer, 2, 5), list);
		List<Long> sizes = weights(list);
		boolean lastWasFineBoi = false;
		long absoluteUnit = c.absoluteUnit(layer);
		for(long size : sizes) {
			boolean isFineBoi = size*2<absoluteUnit;
			if(isFineBoi && lastWasFineBoi) {
				throw new IllegalStateException("Consecutive kittens in layer "+layer);
			}
			lastWasFineBoi = isFineBoi;
		}
	}
	private boolean isBalancingPhase() {
		return phase==0;
	}
	private void doDiffbitPhase() {
		initDiffbitPhase();

		computeDiffbits();
		print();

		for(int mergePrio = 0; mergePrio < 6; ++mergePrio) {
			doMergePriorityBucketed(mergePrio);
		}

		phaseEndCleanup();

	}
	private void initDiffbitPhase() {
		assert isCaterpillarPhase();
		//increment level, enter diffbit merging mode



		lastLayerOfPreviousPhase = lastLayerOfCurrentPhase;
		++phase;
		firstLayerOfCurrentPhase = ChonkerNode.encodeLevelTag(currentLevel, phase, 0);
		lastLayerOfCurrentPhase = ChonkerNode.encodeLevelTag(currentLevel, phase, 5);
		left=left.discardRight(config).upToLayer(lastLayerOfPreviousPhase);
		right=right.discardLeft(config).upToLayer(lastLayerOfPreviousPhase);
		absoluteUnit=config.absoluteUnit(currentLevel);
		maxDiffbitOrder=config.maxDiffbitOrder(currentLevel);
		clearItemZippers();







		int distinctPrios = 6;
		int maxCascadeLength = 0b1<<(distinctPrios); 

		//Load right context in order to compute diffbits
		int leftUntainted = maxDiffbitOrder + 2 + maxCascadeLength;
		int leftTainted = 1;
		int rightUntainted = maxCascadeLength;
		int rightTainted = maxDiffbitOrder + 1;
		int maxLeftExtension = leftTainted + leftUntainted;
		int maxRightExtension = rightTainted + rightUntainted;

		int aus = 4;
		bringIntoQueue(maxLeftExtension, leftUntainted, 
				maxRightExtension, rightUntainted, 
				absoluteUnit, (absoluteUnit/aus>=Long.MAX_VALUE/aus)?Long.MAX_VALUE:aus*absoluteUnit);
		//				bringIntoQueue(200, 100, 200, 100, absoluteUnit);


	}


	private void doMergePriorityBucketed(int mergePrio) {
		assert !isCaterpillarPhase();

		IntrusiveSet<Item<M>> prioBucket = prioBuckets.get(mergePrio);

		while(true) {
			Item<M> at = pollPrioBucket(mergePrio, prioBucket);
			if(at==null)
				break;
			assert at.mergePriority == mergePrio;

			tryMergeItem(at);

		}

	}	
	private void doBalancePriority(int mergePrio) {
		assert !isCaterpillarPhase();

		IntrusiveSet<Item<M>> prioBucket = prioBuckets.get(mergePrio);

		while(true) {
			Item<M> at = pollPrioBucket(mergePrio, prioBucket);
			if(at==null)
				break;
			assert at.mergePriority == mergePrio;

			tryMergeItem(at);

		}

	}

	Item<M> deb1;
	Item<M> deb2;
	private void tryMergeItem(Item<M> at) {
		int mergePrio = at.mergePriority;
//		if(at==deb1) {
//			System.out.println("** DEB: "+at);
//		}
		//Can't merge if there is no right item
		if(at.right == null) {
			return;
		}
		//Don't merge if next priority to the right is the same.
		//This avoids ill-defined cascades
		if(at.right.mergePriority==mergePrio) {
			if(at.right==deb1) {
				System.out.println("** DEB: "+at);
			}
			//This must be checked first so that taint does not spread needlessly
			//and way to far
			return;
		}

		//Don't merge if the left node is already too heavy on its own
		if(at.node.weight()>=absoluteUnit) {
			if(at.leftTainted && at.node.prioAtLevel(currentLevel, phase) < mergePrio) {
				//The current item is tainted and has affected the decision not to merge,
				//so the right item is now tainted too.
				if(at.right!=null && at.right.node.weight()<absoluteUnit)
					at.right.leftTainted=true;
			}
			return;
		}



		//Don't merge if the right node is already too heavy on its own
		if(at.right.node.weight()>=absoluteUnit) {
			if(at.right.rightTainted && at.right.node.prioAtLevel(currentLevel, phase) < mergePrio)
				//The right item is tainted and has affected the decision not to merge,
				//so the current item is now tainted too.
				at.rightTainted = true;
			return;
		}
		/**
		 * Don't merge if the combined weight of the two nodes is too heavy
		 */
		if(at.node.weight() + at.right.node.weight()>=absoluteUnit) {
			// Taint must spread both ways
			if(at.right.rightTainted && at.right.node.prioAtLevel(currentLevel, phase) < mergePrio)
				at.rightTainted = true;
			if(at.leftTainted && at.node.prioAtLevel(currentLevel, phase) < mergePrio)
				at.right.leftTainted = true;
			return;
		}



//		//Don't merge if, as it sometimes happens with unlucky balance at prio>0,
//		//The two nodes are exactly equal
//		if(at.node.equalContent(at.right.node)) {
//			//taint spreads unconditionally
//			if(at.right.rightTainted)
//				at.rightTainted = true;
//			if(at.leftTainted)
//				at.right.leftTainted = true;
//			return;
//		}


		//All clear; can merge
		ChonkerTreeZipper<M> ancestor = at.zip.commonAncestorUpToTag(at.right.zip, lastLayerOfCurrentPhase);

//		if(at.right==deb1) {
//			System.out.println("** DEB: ");
//		}
		Item<M> it;
		if(ancestor != null 
				&& ancestor.node.level()==currentLevel
				&& ancestor.node.phase()==phase
				&& ancestor.node.prioAtLevel(currentLevel, phase)==mergePrio
				&& ancestor.node.isConcat(at.node, at.right.node)
				) {
			it = new Item<M>(ancestor, at.left, at.right.right, (config), at.right.mergePriority);


		}else {
			ChonkerNode<M> newBranch = config.branch(
					ChonkerNode.encodeLevelTag(currentLevel, phase, mergePrio)
					, at.node, at.right.node);
			ChonkerTreeZipper<M> newZip = new ChonkerTreeZipper<M>(newBranch, null, 0);
			it = new Item<M>(newZip, at.left, at.right.right, (config), at.right.mergePriority);

		}
		if(at.left != null 
				&& at.left.mergePriority<=5  
				&& at.left.mergePriority>mergePrio
				&& at.left.node.weight() + it.node.weight() >= absoluteUnit
				) {
			//The merger to the left can't happen anymore, 
			//we remove it
			prioBuckets.get(at.left.mergePriority).remove(at.left);

			//The following is a actually a bad idea.
			//It makes diffbit computations slower
			//apparently by proliferating structure variants of the same content.
			//Worse, it unnecessarily spreads taint.
			
			if(false) {
				//and also remove its merge priority annotation,
				//so that the merger even further left is free to happen
				at.left.mergePriority = ChonkerNode.NO_MERGE_PRIORITY;
				if(at.rightTainted || at.right.rightTainted) {
					at.left.rightTainted = true;
				}
			}
		}
		if(at.left!=null 
				&& at.mergePriority != ChonkerNode.NO_MERGE_PRIORITY
				&& at.left.mergePriority==at.mergePriority) 
		{
			//Cancel merger to the left, as it should not happen,
			//but won't be able to tell that because the equal-priority
			//merger to its right has just disappeared.
			//we need to keep its merge pririty annotation
			//so that the merger even further left cat check if it should happen.
			prioBuckets.get(at.left.mergePriority).remove(at.left);
		}else if(false){ 
			//bad idea, probably for the same reasons as above:
			if(it.right!=null 
					&& it.mergePriority!= ChonkerNode.NO_MERGE_PRIORITY
					&& it.right.node.weight()+it.node.weight()>=absoluteUnit) {
				//A further merger to the right cannot happen anymore,
				//because it would yield a megachonker,
				//(And if it didn't, it would have to be at a later merge priority)
				//so we delete its merge priority annotation,
				//so that the merger to the left, which might still happen,
				//gets a chance depite being at the same priority

				//Not necessary, as it has not been added yet: prioBuckets.get(it.mergePriority).remove(it);
				it.mergePriority = ChonkerNode.NO_MERGE_PRIORITY;
				if(it.right.rightTainted)
					it.rightTainted=true;
			}
		}

		

		it.diffBits = at.right.diffBits;
		it.leftTainted = at.leftTainted | at.right.leftTainted;
		it.rightTainted = at.rightTainted | at.right.rightTainted;
		if(it.mergePriority<=mergePrio){
			assert it.mergePriority < mergePrio;
			it.diffBits = null;
			it.mergePriority=ChonkerNode.NO_MERGE_PRIORITY;
		}
		//			if(at.rightAtLayer==currentLayer)
		//				it.rightAtLayer=currentLayer;
		insert(it);
		//			if(leftItem.zip.toString().length()<left.toString().length())
		//				System.out.print("");
	}


	public void phaseEndCleanup() {
		deb1 = deb2 = null;
		if(right.isEnd()) {
			while(rightItem!=null 
					&& rightItem.rightTainted
					) {
				rightItem.rightTainted = false;
			}
		}else {

			while(rightItem!=null 
					&& rightItem.rightTainted
					) {
				assert !right.isEnd();
				//				long skipCount = rightItem.rightTainted;
				long skipCount = rightItem.node.weight();
				//				ChonkerTreeZipper<M> newRight = right.skipLeft(skipCount, lastLayerOfPreviousPhase);
				ChonkerTreeZipper<M> newRight = right.skipLeft(skipCount, firstLayerOfCurrentPhase);

				right = newRight; 
				Item<M> newRightItem;

				if(skipCount==rightItem.node.weight()) {
					newRightItem = rightItem.left;
					newRightItem.right = null;
				}else {
					ChonkerTreeZipper<M> nz = rightItem.zip.zipTo(rightItem.node.weight()-skipCount);
					nz = nz.upToLayer(rightItem.node.layerTag()-1);
					newRightItem = new Item<M>(nz, rightItem.left, null,  config);
				}

				rightItem = newRightItem;
				if(rightItem.left!=null)
					rightItem.left.right = rightItem;
			}
		}

		if(left.isEnd()) {
			while(leftItem!=null && leftItem.leftTainted) {
				leftItem.leftTainted = false;
			}
		}else {

			while(leftItem!=null && leftItem.leftTainted) {
				assert !left.isEnd();
				ChonkerTreeZipper<M> newLeft;
				//				long skipCount = leftItem.leftTainted;
				long skipCount = leftItem.node.weight();
				newLeft = left.skipRight(skipCount, lastLayerOfPreviousPhase);
				//				newLeft = left.skipRight(skipCount, firstLayerOfCurrentPhase);


				left = newLeft;
				Item<M> newLeftItem;
				if(skipCount==leftItem.node.weight()) {
					newLeftItem = leftItem.right;
					newLeftItem.left = null;
				}else {
					ChonkerTreeZipper<M> nz = leftItem.zip.zipTo(skipCount);
					nz = nz.upToLayer(leftItem.node.layerTag()-1);
					newLeftItem = new Item<M>(nz, null, leftItem.right, config);
				}

				leftItem = newLeftItem;
				if(newLeftItem.right!=null)
					newLeftItem.right.left = newLeftItem;
			}
		}
		for(Item<M> i = leftItem; i!=null; i=i.right) {
			i.diffBits = null;
			i.mergePriority=ChonkerNode.NO_MERGE_PRIORITY;
		}
		clearPrioBuckets();



		//	}		
	}


	private void computeDiffbits() {
		assert !isCaterpillarPhase();
		int validDiffbits;

		Item<M> start;


		boolean freshDiffbits;
		Item<M> successor;
		//Initialized based on merge possibilities beyond the end of the queue
		if(
				// The end resets the diffbits
				right.isEnd() 
				|| 
				//a single oversized chunk resets the diffbits
				right.node.weight()>=absoluteUnit
				) {
			validDiffbits = 100;
			freshDiffbits = false;
			start = rightItem.left;
			successor = rightItem;
		}else if(
				//A merger that would result in an oversized chunk resets the diffbits
				//(we know it would not happen, so it is no problem if the diffbits
				//next to it repeat, and it will also block cascades).
				//However, the rightItem should not have diffbits,
				//otherwise the merging logic gets confused
				//And may try to merge it at the wrong time.
				(
						rightItem==null 
						||
						rightItem.node.weight() + right.node.weight()>=absoluteUnit
						)
				){
			rightItem.diffBits=null;
			start = rightItem.left;
			successor = rightItem;

			validDiffbits = 100;
			freshDiffbits = false;
		}else {
			freshDiffbits = true;
			validDiffbits = 0;
			start = rightItem;
			successor = null;

		}


		if(validDiffbits<maxDiffbitOrder)
			System.out.print("");

		for(Item<M> i = start; i!=null; i=i.left) {
			//			System.out.println(toString());
			if(
					//a single oversized chunk resets the diffbits
					i.node.weight()>=absoluteUnit) 
			{
				i.diffBits = null;
				validDiffbits = 100;
				freshDiffbits = true;
				successor = i;
			}else if(
					//reset diffbits if successor exists
					successor!=null
					&& //and if merger with successor would result in an oversized chunk
					i.node.weight()+successor.node.weight()>=absoluteUnit
					){
				i.diffBits = null;
				freshDiffbits = false;
				validDiffbits = 100;
				//But the Item<M> can still be a successor
				successor = i;
			}else {
				long order0;
				if(freshDiffbits || successor==null) {
					//Force difference
					order0 = i.node.getAugBit(config, currentLevel,  0)?0:1;
				}else {
					order0 = i.node.getDiffbit(config, successor.node, currentLevel);
					if(successor.diffBits!= null && validDiffbits>1 && order0==successor.diffBits.getDiffbit(0)) {
						i.node.getDiffbit(config, successor.node, currentLevel);
						successor.node.getDiffbit(config, successor.right.node, currentLevel);
					}
				}
				i.diffBits = new Diffbits(order0, maxDiffbitOrder, freshDiffbits?null:successor.diffBits);
				i.diffBits.valid = validDiffbits;
				++validDiffbits;
				freshDiffbits=false;
				successor = i;
			}
		}
		//		System.out.println(validDiffbits);



		clearPrioBuckets();
		for(Item<M> i = leftItem; i!=null; i=i.right) {
			if(i.diffBits==null) {
				i.mergePriority = ChonkerNode.NO_MERGE_PRIORITY;
			}else if(i.diffBits.valid>maxDiffbitOrder) {
				i.mergePriority = i.diffBits.getHighestOrderDiffbit();
				//				i.rightAtLayer=currentLayer;
			}else {
				ChonkerTreeZipper<M> rzip = i.right==null?right:i.right.zip;
				ChonkerTreeZipper<M> ancestor = i.zip.commonAncestorUpToTag(rzip, lastLayerOfCurrentPhase);
				if(ancestor==null || ancestor.node.level()!=currentLevel) {
					i.mergePriority = ChonkerNode.NO_MERGE_PRIORITY;
					i.diffBits=null;
				} else {
					i.mergePriority = ancestor.node.prioAtLevel(currentLevel, phase);
				}
			}
			if(i.mergePriority!=ChonkerNode.NO_MERGE_PRIORITY) {
				prioBuckets.get(i.mergePriority).add(i);
			}
		}


	}


	private boolean isCaterpillarPhase() {
		return phase == 1;
	}
	private void fuseWithLeftContext() {
		assert isCaterpillarPhase();
		while(leftItem!=null && !left.isEnd()) {
			if(left.parent!=null 
					&& left.parent.node instanceof Caterpillar
					&& left.parent.node.layerTag()==firstLayerOfCurrentPhase
					) {
				//Try to fuse with a megachonker on the left
				Caterpillar<M> leftMc = ((Caterpillar<M>)left.parent.node);
				//				assert left.parent.node.numChildren() - left.indexInParent - 1 == 0;
				ChonkerNode<M> fused = config.tryFuse(
						leftMc.dropSuffixChildren(left.parent.node.numChildren() - left.indexInParent-1, config)
						, leftItem.node, firstLayerOfCurrentPhase, 0, 0);
				if(fused!=null) {
					ChonkerTreeZipper<M> newZip = new ChonkerTreeZipper<M>(fused, null, 0);
					left = left.parent.left(lastLayerOfCurrentPhase);
					insert(new Item<M>(newZip, null, leftItem.right, (config)));
				}else {
					break;
				}
			}else 
			{
				//Try to fuse with a single node on the left
				ChonkerNode<M> fused = config.tryFuse(left.node, leftItem.node, firstLayerOfCurrentPhase, 0, 0);
				if(fused!=null) {
					ChonkerTreeZipper<M> newZip = new ChonkerTreeZipper<M>(fused, null, 0);
					left = left.left(lastLayerOfCurrentPhase);
					insert(new Item<M>(newZip, null, leftItem.right, (config)));
				}else {
					break;
				}

			}
		}
	}
	private void fuseWithRightContext() {

		assert isCaterpillarPhase();
		while(rightItem!=null && right.node!=null) {
			if(right.parent!=null 
					&& right.parent.node instanceof Caterpillar
					&& right.parent.node.layerTag()==firstLayerOfCurrentPhase
					) {
				//Try to fuse with a megachonker on the right
				Caterpillar<M> rightMc = ((Caterpillar<M>)right.parent.node);
				//				assert right.indexInParent==0;
				ChonkerNode<M> fused = config.tryFuse(rightItem.node, rightMc.dropPrefixChildren(right.indexInParent, config), firstLayerOfCurrentPhase, 0, 0);
				if(fused!=null) {
					ChonkerTreeZipper<M> newZip = new ChonkerTreeZipper<M>(fused, null, 0);
					right = right.parent.right(lastLayerOfCurrentPhase);
					insert(new Item<M>(newZip, rightItem.left, null, (config)));
				}else {
					break;
				}
			}else 
			{
				//Try to fuse with a single node on the left
				ChonkerNode<M> fused = config.tryFuse(rightItem.node, right.node, firstLayerOfCurrentPhase, 0, 0);
				if(fused!=null) {
					ChonkerTreeZipper<M> newZip = new ChonkerTreeZipper<M>(fused, null, 0);
					right = right.right(lastLayerOfCurrentPhase);
					insert(new Item<M>(newZip, rightItem.left, null, (config)));
				}else {
					break;
				}

			}
		}

	}

	//	private boolean tryFuseRight() {
	//		if(rightItem.left != null) {
	//			ChonkerNode<M> fused = config.tryFuse(rightItem.left.node, rightItem.node, firstLayerOfCurrentPhase, 0, 0);
	//			if(fused!=null) {
	//				insert(new Item<M>(new ChonkerTreeZipper<M>(fused, null, 0), rightItem.left.left, null, (config)));
	//				return true;
	//			}
	//		}
	//		return false;
	//	}
	//	private boolean tryFuseLeft() {
	//		if(leftItem.right != null) {
	//			ChonkerNode<M> fused = config.tryFuse(leftItem.node, leftItem.right.node, firstLayerOfCurrentPhase, 0, 0);
	//			if(fused!=null) {
	//				insert(new Item<M>(new ChonkerTreeZipper<M>(fused, null, 0), null, leftItem.right.right, (config)));
	//				return true;
	//			}
	//		}
	//		return false;
	//	}

	public static boolean PRINT = false;

	public List<ChonkerNode<M>> getListAtLevelTag(int levelTag){
		ArrayList<ChonkerNode<M>> ret = new ArrayList<>();
		giveLeft(left.discardRight(config).upToLayer(levelTag), levelTag, ret);
		for(Item<M> at = leftItem; at!=null; at=at.right) {
			give(at.node, levelTag, ret);
		}
		giveRight(right.discardLeft(config).upToLayer(levelTag), levelTag, ret);
		return ret;
	}
	public static <M extends ChonkersMonoidData<M>> List<List<ChonkerNode<M>>> flatten(List<? extends ChonkerNode<M>> in){
		return in
				.stream()
				.map(n->give(n, 0, new ArrayList<>()))
				.collect(Collectors.toList())
				;
	}
	public static<T> List<T> flatten2(List<? extends List<T>> in){
		ArrayList<T> ret = new ArrayList<>();
		for(List<T> l: in)
			ret.addAll(l);
		return ret;

	}


	private void giveLeft(ChonkerTreeZipper<M> z, int layerTag, List<ChonkerNode<M>> ret) {
		give(z.getRoot().node, layerTag, ret);
	}
	private void giveRight(ChonkerTreeZipper<M> z, int layerTag, List<ChonkerNode<M>> ret) {
		give(z.getRoot().node, layerTag, ret);
	}
	public static <M extends ChonkersMonoidData<M>> List<ChonkerNode<M>> give(ChonkerNode<M> n, int layerTag, List<ChonkerNode<M>> ret) {
		if(n==null)
			return ret;
		if(n.layerTag()>layerTag) {
			for(int ci = 0; ci < n.numChildren(); ++ci) {
				give(n.getChild(ci), layerTag, ret);
			}
		}else {
			ret.add(n);
		}
		return ret;
	}
	public static void mainz(String[] args) {
		int seed = 0;
		int mag = 100;
		int s=0;
		try {
			seed:for(; seed<100000; ++seed) {
				ChonkerConfig<ChonkersMonoidData.Minimal> c = ChonkerConfig.BYTES;
				c.canon.clear();
				c.monoidCanon.clear();
//				System.gc();
//				System.out.println(c.canon.size());
//				System.out.println(c.monoidCanon.size());
				System.out.println(seed);
				ArrayList<ByteLeaf> leaves1 = new ArrayList<>();
				ArrayList<ByteLeaf> leaves2 = new ArrayList<>();
				ArrayList<ByteLeaf> leavesAll = new ArrayList<>();
				Random rnd = new Random(3235324+seed
						);
				ChonkerTreeZipper<ChonkersMonoidData.Minimal> end = ChonkerTreeZipper.end();
				for(int i=0; i < 100*mag; ++i) {
					leaves1.add(new ByteLeaf(rnd.nextInt(0x4<<s)));
				}
				for(int i=0; i < 40; ++i) {
					leaves2.add(new ByteLeaf(rnd.nextInt(0x4<<s)));
				}
				leavesAll.addAll(leaves1);
				leavesAll.addAll(leaves2);
				//		Rechonker c1 = new Rechonker(ChonkerConfig.BYTES, end, leaves1, end);
				//		Rechonker c2 = new Rechonker(ChonkerConfig.BYTES, end, leaves2, end);
				//		Rechonker cAll = new Rechonker(ChonkerConfig.BYTES, end, leavesAll, end);
				//		ChonkerNode<M> result1 = c1.run();
				//		ChonkerNode<M> result2 = c2.run();
				//		ChonkerNode<M> resultAll = cAll.run();
				//		System.out.println(result1);
				//		System.out.println(result2);
				//		System.out.println(resultAll);

				ArrayList<ByteLeaf> leavesModi = new ArrayList<>(leaves1);
				Random accessRandomizer = rnd;
				//				Random accessRandomizer = null;
				ChonkerNode<ChonkersMonoidData.Minimal> preModi = new Rechonker<ChonkersMonoidData.Minimal>(
						c, end, leavesModi, end, accessRandomizer)
						.run();

				if(!true) {
					for(int i=1; i<=preModi.level(); ++i) {
						checkBalancePrecondition(c, preModi, i);
						checkBalancePostcondition(c, preModi, i);
						checkDiffbitPrecondition(c, preModi, i);
						checkDiffbitPostcondition(c, preModi, i);
					}
					continue;
				}


				int replacementStartIndex = 10*mag;
				int replacementEndIndex = 80*mag;
				int[] replacement = new int[rnd.nextInt(10*mag)];
				for(int i=0; i<replacement.length; ++i) {
					replacement[i] = rnd.nextInt(0x4<<s);
				}

				List<ByteLeaf> replacementList= new ArrayList<>();
				for(int r: replacement) {
					replacementList.add(new ByteLeaf(r));
				}
				if(PRINT)System.out.println(leavesModi);
				List<ByteLeaf> modify = leavesModi.subList(replacementStartIndex, replacementEndIndex);
				modify.clear();
				modify.addAll(replacementList);
				ChonkerTreeZipper<ChonkersMonoidData.Minimal> left = preModi.zipTo(null, (replacementStartIndex-1)*8);
				ChonkerTreeZipper<ChonkersMonoidData.Minimal> right= preModi.zipTo(null, (replacementEndIndex)*8);
				if(PRINT)System.out.println(leavesModi);

				if(PRINT)System.out.println(preModi);
				Rechonker<ChonkersMonoidData.Minimal>  rc1 = new Rechonker<ChonkersMonoidData.Minimal>(c, end, leavesModi, end, accessRandomizer);
				Rechonker<ChonkersMonoidData.Minimal> rc2 = new Rechonker<ChonkersMonoidData.Minimal>(c, left, replacementList, right, accessRandomizer);

				@SuppressWarnings("unused")
				int levelTag = 0;
				int prevLevelTag = 0;
				run:for(int level = 0; ;) {
					for(int stage = -4; stage<=6; stage++) {


						//						if(rc2.leftItem.zip.toString().length()<rc2.left.toString().length())
						//							System.out.print("");
						int _phase;
						int mergePrio;
						switch(stage) {
						case -4: level++; _phase = 0; mergePrio = 0; break;
						case -3: _phase = 1; mergePrio = 0; break;
						case -2: _phase = 2; mergePrio = 0; break;
						case -1: _phase = 2; mergePrio = 0; break;
						case 6: _phase = 2; mergePrio = 0; break;
						default: _phase = 2; mergePrio = stage; break;
						}

						prevLevelTag = ChonkerNode.encodeLevelTag(_phase==0?level-1:level, (_phase+2)%3, _phase==0?5:_phase==1?3:0);
						levelTag = ChonkerNode.encodeLevelTag(level, _phase, mergePrio);





						if(true) {
							List<? extends ChonkerNode<ChonkersMonoidData.Minimal>> repr1 = rc1.getListAtLevelTag(prevLevelTag);
							//							boolean stop = layer==2 && _phase==0 && mergePrio==0;
							boolean stop = level==1 && stage==0;
							List<? extends ChonkerNode<ChonkersMonoidData.Minimal>> repr2 = rc2.getListAtLevelTag(prevLevelTag);
							List<List<ChonkerNode<ChonkersMonoidData.Minimal>>> l1 = flatten(repr1);
							List<List<ChonkerNode<ChonkersMonoidData.Minimal>>> l2 = flatten(repr2);
							List<ChonkerNode<ChonkersMonoidData.Minimal>> lf1 = flatten2(l1);
							List<ChonkerNode<ChonkersMonoidData.Minimal>> lf2 = flatten2(l2);
							if(PRINT) {
								System.out.println(level+" / "+_phase + " / "+mergePrio);
								System.out.println(rc1);
								if(stop) {
									System.out.print("");
									rc2.getListAtLevelTag(prevLevelTag);
								}
								System.out.println(rc2.toString());
								if(stop) 
									System.out.print("");
								System.out.println(l1);
								System.out.println(l2);
							}
							if(!lf1.equals(lf2) || (stage<=0) && !(l1.equals(l2) && repr1.equals(repr2))) {
								rc1.getListAtLevelTag(prevLevelTag);
								rc2.getListAtLevelTag(prevLevelTag);
								System.out.print("");
								System.out.println(repr1);
								System.out.println(repr2);
								repr1.equals(repr2);
								System.out.println(l1);
								System.out.println(l2);
								System.out.println(seed+" : "+level+" / "+_phase);
								break seed;
							}
						}

						boolean do1 = rc1.result()==null;
						boolean do2 = rc2.result()==null;
						if(!do1 && !do2)
							break run;
						switch(stage) {
						case -4:
							if(do1)	rc1.doBalancingPhaseBuckets();
							if(do2)	rc2.doBalancingPhaseBuckets();
							break;
						case -3:
							if(do1)	rc1.doCaterpillarPhase();
							if(do2)	rc2.doCaterpillarPhase();
							break;

						case -2:
							if(do1)	rc1.initDiffbitPhase();
							if(do2)	rc2.initDiffbitPhase();
							break;
						case -1:
							if(do1)	rc1.computeDiffbits();
							if(do2)	rc2.computeDiffbits();
							//							PRINT = true;
							break;
						case 6:
							if(do1)	rc1.phaseEndCleanup();
							if(do2)	rc2.phaseEndCleanup();
							break;
						default:

							if(do1)	rc1.doMergePriorityBucketed(mergePrio);
							if(do2)	rc2.doMergePriorityBucketed(mergePrio);
							break;
						}

					}





				}
				ChonkerNode<ChonkersMonoidData.Minimal> modi1 = rc1.result();
				ChonkerNode<ChonkersMonoidData.Minimal> modi2 = rc2.result();
				if(PRINT)System.out.print("");
				if(PRINT)System.out.println("");
				if(PRINT)System.out.println(modi1);
				if(PRINT)System.out.println("==");
				if(PRINT)System.out.println(modi2);
				boolean success = modi1.equalStructure(modi2);
				System.out.println(success);
				if(!success) {
					modi1.equalStructure(modi2);
					System.out.println(seed);
					break;
				}

			}
		}finally {
			System.out.println(seed);
		}
	}



}
